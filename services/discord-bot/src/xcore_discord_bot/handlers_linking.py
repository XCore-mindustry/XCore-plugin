from __future__ import annotations

from collections.abc import Awaitable, Callable
from typing import TYPE_CHECKING

import discord
from discord import Interaction
from xcore_protocol.generated.discord import (
    DiscordAccountOperationRequestV1,
    DiscordAccountOperationResponseV1,
)
from xcore_protocol.generated.discord import (
    DiscordAccountOperationRequestV1Action as Action,
)
from xcore_protocol.generated.discord import (
    DiscordAccountOperationResponseV1Status as Status,
)

from .bus.accounts import ACCOUNT_OPERATION_TIMEOUT_MS
from .command_helpers import claim_mutation, get_player_or_reply, reply_locale
from .dto import PlayerRecord
from .i18n import t
from .protocol_outbound import build_discord_account_operation_request
from .utils.mindustry_colors import strip_mindustry_colors

if TYPE_CHECKING:
    from .bot import XCoreDiscordBot

# What a game server answers when it cannot write accounts (DiscordAccountOperationService).
READ_ONLY_REASON = "read-only"

# Message ids are "<action>-<key>", such as "link-done" or "unlink-unknown".
_RESULT_KEYS = {
    Status.SUCCESS: "done",
    Status.REJECTED: "rejected",
    Status.FAILED: "failed",
    Status.UNKNOWN: "unknown",
}


def _clean_player_name(name: str | None) -> str:
    if not name:
        return "Unknown"
    cleaned = strip_mindustry_colors(name).replace("`", "").strip()
    return cleaned or name


async def find_code_player(
    bot: XCoreDiscordBot, code_doc: dict[str, object]
) -> PlayerRecord | None:
    player_uuid = str(code_doc.get("playerUuid") or code_doc.get("player_uuid") or "")
    return await bot.store.find_player_by_uuid(player_uuid)


async def link_with_code(
    bot: XCoreDiscordBot,
    *,
    code: str,
    code_doc: dict[str, object],
    player: PlayerRecord | None,
    source_id: int,
    discord_id: str,
    discord_username: str,
    locale: str,
    before_rpc: Callable[[], Awaitable[object]] | None = None,
) -> str:
    """Asks the server that issued ``code`` to link it and returns the answer to show."""
    expires_at_raw = code_doc.get("expiresAt", code_doc.get("expires_at"))
    expires_at = int(str(expires_at_raw)) if expires_at_raw is not None else 0
    now_ms = int((bot.store.now_utc()).timestamp() * 1000)
    if expires_at > 0 and expires_at <= now_ms:
        return t(locale, "link-code-expired")
    if player is None or player.uuid is None:
        return t(locale, "link-player-not-found")
    if player.discord_id and player.discord_id != discord_id:
        return t(locale, "link-taken")

    # Only the issuing server can redeem its code; no other server is tried in its place.
    server = str(code_doc.get("server") or "")
    if server not in bot.container.servers.names():
        return t(locale, "link-server-offline", server=server or "?")

    request = build_discord_account_operation_request(
        action=Action.LINK,
        server=server,
        source_id=source_id,
        player_uuid=player.uuid,
        player_pid=player.pid,
        player_name=player.nickname,
        discord_id=discord_id,
        discord_username=discord_username,
        issued_at=now_ms,
        timeout_ms=ACCOUNT_OPERATION_TIMEOUT_MS,
        code=code,
    )
    if before_rpc is not None:
        await before_rpc()
    response = await bot.container.accounts_bus.rpc_account_operation(request)
    return _result(request, response, _clean_player_name(player.nickname), locale)


def _result(
    request: DiscordAccountOperationRequestV1,
    response: DiscordAccountOperationResponseV1,
    name: str,
    locale: str,
) -> str:
    return t(
        locale,
        f"{request.action.value}-{_RESULT_KEYS[response.status]}",
        name=name,
        pid=request.player.playerPid or 0,
        server=request.server,
        reason=response.reason or "-",
    )


async def _answer(interaction: Interaction, text: str) -> None:
    if interaction.response.is_done():
        await interaction.followup.send(text, ephemeral=True)
    else:
        await interaction.response.send_message(text, ephemeral=True)


async def cmd_link(bot: XCoreDiscordBot, interaction: Interaction, code: str) -> None:
    locale = reply_locale(bot, interaction)
    normalized_code = (code or "").strip().upper()
    if not normalized_code:
        await interaction.response.send_message(
            t(locale, "link-code-required"), ephemeral=True
        )
        return

    if not await claim_mutation(
        bot,
        interaction,
        operation="discord-link",
        scope=f"{interaction.user.id}:{normalized_code}",
        locale=locale,
    ):
        return

    code_doc = await bot.container.accounts_bus.get_discord_link_code(normalized_code)
    if code_doc is None:
        await interaction.response.send_message(
            t(locale, "link-code-not-found"), ephemeral=True
        )
        return

    text = await link_with_code(
        bot,
        code=normalized_code,
        code_doc=code_doc,
        player=await find_code_player(bot, code_doc),
        source_id=interaction.id,
        discord_id=str(interaction.user.id),
        discord_username=interaction.user.display_name,
        locale=locale,
        # The answer can take longer than Discord waits for a reply.
        before_rpc=lambda: interaction.response.defer(ephemeral=True),
    )
    await _answer(interaction, text)


async def cmd_link_status(bot: XCoreDiscordBot, interaction: Interaction) -> None:
    locale = reply_locale(bot, interaction)
    discord_id = str(interaction.user.id)
    players = await bot.store.find_players_by_discord_id(discord_id)
    if not players:
        await interaction.response.send_message(
            t(locale, "link-status-none"), ephemeral=True
        )
        return

    lines = [
        f"`{player.pid}` — {_clean_player_name(player.nickname)}" for player in players
    ]
    embed = discord.Embed(
        title=t(locale, "link-status-title"),
        description="\n".join(lines),
        color=discord.Color.blurple(),
    )
    await interaction.response.send_message(embed=embed, ephemeral=True)


async def cmd_unlink(
    bot: XCoreDiscordBot, interaction: Interaction, player_id: int
) -> None:
    locale = reply_locale(bot, interaction)
    player = await get_player_or_reply(bot, interaction, player_id, locale=locale)
    if player is None:
        return

    discord_id = str(interaction.user.id)
    if player.discord_id != discord_id:
        await interaction.response.send_message(
            t(locale, "unlink-not-yours"), ephemeral=True
        )
        return

    player_uuid = player.uuid
    if player_uuid is None:
        await interaction.response.send_message(
            t(locale, "unlink-no-uuid"), ephemeral=True
        )
        return

    if not await claim_mutation(
        bot,
        interaction,
        operation="discord-unlink",
        scope=f"{discord_id}:{player_id}",
        locale=locale,
    ):
        return

    server = _unlink_server(bot, player)
    name = _clean_player_name(player.nickname)
    if server is None:
        await interaction.response.send_message(
            t(locale, "unlink-no-server", name=name), ephemeral=True
        )
        return

    def unlink_request(target: str) -> DiscordAccountOperationRequestV1:
        return build_discord_account_operation_request(
            action=Action.UNLINK,
            server=target,
            source_id=interaction.id,
            player_uuid=player_uuid,
            player_pid=player.pid,
            player_name=player.nickname,
            discord_id=discord_id,
            discord_username=interaction.user.display_name,
            issued_at=int((bot.store.now_utc()).timestamp() * 1000),
            timeout_ms=ACCOUNT_OPERATION_TIMEOUT_MS,
            # The server unlinks only the link this is about, not one made after it was read.
            expected_linked_at=player.discord_linked_at or 0,
        )

    await interaction.response.defer(ephemeral=True)
    accounts = bot.container.accounts_bus
    request = unlink_request(server)
    response = await accounts.rpc_account_operation(request)
    fallback = bot.settings.permissions_rpc_server
    # A read-only server refuses before it records or changes anything, so the writable
    # server may take the unlink, as a new operation. Any other answer, unknown included,
    # stands as it is.
    if (
        response.status is Status.REJECTED
        and response.reason == READ_ONLY_REASON
        and fallback != server
        and fallback in bot.container.servers.names()
    ):
        request = unlink_request(fallback)
        response = await accounts.rpc_account_operation(request)
    await _answer(interaction, _result(request, response, name, locale))


def _unlink_server(bot: XCoreDiscordBot, player: PlayerRecord) -> str | None:
    """The server the player is on, else the configured server that writes accounts."""
    live = bot.container.servers.names()
    if player.online and player.online_server in live:
        return player.online_server
    if bot.settings.permissions_rpc_server in live:
        return bot.settings.permissions_rpc_server
    return None
