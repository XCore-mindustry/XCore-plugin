from __future__ import annotations

import json
from dataclasses import dataclass, field
from typing import Any, cast
from unittest.mock import AsyncMock, MagicMock

import discord
import pytest
from backends import published, real_bot

from xcore_discord_bot.bot import XCoreDiscordBot
from xcore_discord_bot.handlers_linking import cmd_link, cmd_link_status
from xcore_discord_bot.i18n import LocalePref


@dataclass
class _User:
    id: int = 555
    display_name: str = "discord-user"


@dataclass
class _Response:
    sent: list[tuple[str, bool]] = field(default_factory=list)
    embeds: list[tuple[Any, bool]] = field(default_factory=list)

    async def send_message(
        self, text: str | None = None, *, ephemeral: bool = False, embed: Any = None
    ) -> None:
        if text is not None:
            self.sent.append((text, ephemeral))
        if embed is not None:
            self.embeds.append((embed, ephemeral))


@dataclass
class _Interaction:
    id: int = 77
    user: _User = field(default_factory=_User)
    response: _Response = field(default_factory=_Response)
    locale: object = None


TARGET = {
    "_id": 1,
    "pid": 7,
    "uuid": "uuid-7",
    "nickname": "Target",
    "discord_id": "555",
}
ALT = {"_id": 2, "pid": 9, "uuid": "uuid-9", "nickname": "Alt", "discord_id": "555"}
CODE = {
    "code": "ABC123",
    "playerUuid": "uuid-7",
    "playerPid": 7,
    "server": "mini-pvp",
    "expires_at": 9999999999999,
}
CONFIRMS = "xcore:cmd:discord-link-confirm:mini-pvp"


async def _bot(*players: dict[str, Any]):
    bot, db = await real_bot()
    bot.container.servers.update_server("mini-pvp", 1, 0, 10, "v1")
    for player in players or (TARGET, ALT):
        await db["players"].insert_one(dict(player))
    await bot.bus.redis.set("xcore:discord-link:code:ABC123", json.dumps(CODE))
    return bot


@pytest.mark.asyncio
async def test_cmd_link_publishes_confirm_event() -> None:
    bot = await _bot()
    interaction = _Interaction()

    await cmd_link(cast(Any, bot), cast(Any, interaction), "abc123")

    (confirm,) = await published(bot.bus, CONFIRMS)
    assert confirm["code"] == "ABC123"
    assert confirm["player"] == {
        "playerUuid": "uuid-7",
        "playerPid": 7,
        "playerName": "Target",
    }
    assert confirm["discord"] == {"discordId": "555", "discordUsername": "discord-user"}
    assert interaction.response.sent == [
        ("Account linked to `Target` (`pid=7`). Return in-game in a moment.", True)
    ]


@pytest.mark.asyncio
async def test_cmd_link_instantly_grants_admin_if_user_has_role() -> None:
    bot = await _bot()
    bot.container.admin_sync_daemon.get_discord_admin_member_ids = AsyncMock(
        return_value={"555"}
    )

    interaction = _Interaction()
    await cmd_link(cast(Any, bot), cast(Any, interaction), "abc123")

    player = await bot.store.find_player_by_uuid("uuid-7")
    assert player.is_admin and player.admin_source == "DISCORD_ROLE"
    (changed,) = await published(bot.bus, "xcore:cmd:discord-admin-access:mini-pvp")
    assert changed["admin"] is True
    assert (
        interaction.response.sent[0][0]
        == "Account linked to `Target` (`pid=7`). Admin access granted!"
    )


@pytest.mark.asyncio
async def test_cmd_link_does_not_claim_admin_access_it_could_not_grant() -> None:
    bot = await _bot()
    bot.container.admin_sync_daemon.get_discord_admin_member_ids = AsyncMock(
        return_value={"555"}
    )
    bot.store.set_admin_access = AsyncMock(side_effect=RuntimeError("mongo down"))

    interaction = _Interaction()
    await cmd_link(cast(Any, bot), cast(Any, interaction), "abc123")

    assert interaction.response.sent == [
        ("Account linked to `Target` (`pid=7`). Return in-game in a moment.", True)
    ]


@pytest.mark.asyncio
async def test_cmd_unlink_publishes_with_display_name() -> None:
    bot = await _bot()
    interaction = _Interaction()

    from xcore_discord_bot.handlers_linking import cmd_unlink

    await cmd_unlink(cast(Any, bot), cast(Any, interaction), 7)

    (unlink,) = await published(bot.bus, "xcore:cmd:discord-unlink:mini-pvp")
    assert unlink["actor"]["actorName"] == "discord-user"
    assert unlink["player"]["playerPid"] == 7


@pytest.mark.asyncio
async def test_cmd_link_status_renders_same_linked_account_output() -> None:
    bot = await _bot()
    interaction = _Interaction()

    await cmd_link_status(cast(Any, bot), cast(Any, interaction))

    assert interaction.response.sent == []
    assert len(interaction.response.embeds) == 1
    embed, ephemeral = interaction.response.embeds[0]
    assert ephemeral is True
    assert embed.title == "Linked Mindustry accounts"
    # Newest account first.
    assert embed.description == "`9` — Alt\n`7` — Target"


@pytest.mark.asyncio
async def test_link_answers_in_the_users_discord_language() -> None:
    bot = await _bot()
    interaction = _Interaction(locale=discord.Locale.russian)

    await cmd_link(cast(Any, bot), cast(Any, interaction), "abc123")

    assert interaction.response.sent == [
        ("Аккаунт `Target` (`pid=7`) привязан. Можно возвращаться в игру.", True)
    ]


@pytest.mark.asyncio
async def test_a_language_chosen_with_the_language_command_wins() -> None:
    bot = await _bot()
    bot.container.cases.store.cached_locale_pref = lambda discord_id: LocalePref(
        "uk", "manual"
    )
    interaction = _Interaction(locale=discord.Locale.russian)

    await cmd_link_status(cast(Any, bot), cast(Any, interaction))

    assert interaction.response.embeds[0][0].title == "Прив'язані акаунти Mindustry"


@pytest.mark.asyncio
async def test_unlink_refuses_someone_elses_player_in_the_users_language() -> None:
    bot = await _bot()
    interaction = _Interaction(user=_User(id=556), locale=discord.Locale.ukrainian)

    from xcore_discord_bot.handlers_linking import cmd_unlink

    await cmd_unlink(cast(Any, bot), cast(Any, interaction), 7)

    assert interaction.response.sent == [
        ("Цей гравець не прив'язаний до вашого акаунта Discord.", True)
    ]
    assert await published(bot.bus, "xcore:cmd:discord-unlink:mini-pvp") == []


def _dm(content: str, author_id: int = 555) -> Any:
    channel = MagicMock(spec=discord.DMChannel)
    channel.send = AsyncMock()
    author = MagicMock(id=author_id, display_name="discord-user", bot=False)
    return MagicMock(content=content, channel=channel, author=author)


async def _send_dm(player: dict[str, Any], *, pref: LocalePref | None = None) -> Any:
    bot = await _bot(player)
    bot.container.cases.store.get_locale_pref = AsyncMock(return_value=pref)
    message = _dm("abc123")
    await XCoreDiscordBot.on_message(bot, message)
    return bot, message


@pytest.mark.asyncio
async def test_a_code_sent_in_a_dm_links_and_answers_in_the_players_language() -> None:
    player = dict(TARGET, discord_id="", local_language="ru")

    bot, message = await _send_dm(player)

    assert [confirm["code"] for confirm in await published(bot.bus, CONFIRMS)] == [
        "ABC123"
    ]
    message.channel.send.assert_awaited_once_with(
        "Запрос на привязку `Target` (`pid=7`) отправлен."
    )


@pytest.mark.asyncio
async def test_a_dm_follows_a_stored_language_choice_over_the_game_language() -> None:
    player = dict(TARGET, discord_id="", local_language="ru")

    _, message = await _send_dm(player, pref=LocalePref("uk", "manual"))

    message.channel.send.assert_awaited_once_with(
        "Запит на прив'язку `Target` (`pid=7`) надіслано."
    )


@pytest.mark.asyncio
async def test_a_dm_code_for_an_account_linked_elsewhere_is_refused() -> None:
    bot, message = await _send_dm(dict(TARGET, discord_id="999"))

    assert await published(bot.bus, CONFIRMS) == []
    message.channel.send.assert_awaited_once_with(
        "This Mindustry account is already linked to another Discord account."
    )
