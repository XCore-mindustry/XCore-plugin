from __future__ import annotations

from typing import TYPE_CHECKING

from discord import Interaction

from .badges import get_badge

if TYPE_CHECKING:
    from .bot import XCoreDiscordBot


async def cmd_badge_grant(
    bot: "XCoreDiscordBot",
    interaction: Interaction,
    player_id: int,
    badge_id: str,
) -> None:
    await _cmd_badge_change(
        bot,
        interaction,
        player_id=player_id,
        badge_id=badge_id,
        grant=True,
    )


async def cmd_badge_revoke(
    bot: "XCoreDiscordBot",
    interaction: Interaction,
    player_id: int,
    badge_id: str,
) -> None:
    await _cmd_badge_change(
        bot,
        interaction,
        player_id=player_id,
        badge_id=badge_id,
        grant=False,
    )


async def _cmd_badge_change(
    bot: "XCoreDiscordBot",
    interaction: Interaction,
    *,
    player_id: int,
    badge_id: str,
    grant: bool,
) -> None:
    player = await bot._get_player_or_reply(interaction, player_id)
    if player is None:
        return

    operation = "badge-grant" if grant else "badge-revoke"
    if not await bot._claim_mutation(
        interaction,
        operation=operation,
        scope=f"{player_id}:{badge_id.strip().lower()}",
    ):
        return

    uuid_value = await bot._require_player_uuid(
        interaction,
        player,
        action="change badge",
    )
    if uuid_value is None:
        return

    badge = get_badge(badge_id)
    if badge is None:
        await interaction.response.send_message(
            f"Badge `{badge_id.strip() or badge_id}` was not found.",
            ephemeral=True,
        )
        return

    if badge.system or not badge.grantable:
        await interaction.response.send_message(
            f"Badge `{badge.id}` cannot be granted or revoked manually.",
            ephemeral=True,
        )
        return

    changed = (
        await bot.grant_badge(uuid=uuid_value, badge_id=badge.id)
        if grant
        else await bot.revoke_badge(uuid=uuid_value, badge_id=badge.id)
    )

    if changed:
        await bot.publish_reload_player_data_cache()

    player_name = bot._player_name(player)
    if grant:
        message = (
            f"Granted badge `{badge.id}` to `{player_name}`"
            if changed
            else f"Player already has badge `{badge.id}`"
        )
    else:
        message = (
            f"Revoked badge `{badge.id}` from `{player_name}`"
            if changed
            else f"Player does not have badge `{badge.id}`"
        )

    await interaction.response.send_message(message)
