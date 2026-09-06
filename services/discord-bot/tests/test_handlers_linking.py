from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, cast

import pytest

from xcore_discord_bot.dto import PlayerRecord
from xcore_discord_bot.handlers_linking import cmd_link, cmd_link_status


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


class _Bot:
    def __init__(self) -> None:
        self.published: list[dict[str, object]] = []

    async def _claim_mutation(
        self, interaction: _Interaction, *, operation: str, scope: str
    ) -> bool:
        del interaction, operation, scope
        return True

    async def find_discord_link_code(self, code: str) -> dict[str, object] | None:
        assert code == "ABC123"
        return {
            "code": code,
            "playerUuid": "uuid-7",
            "playerPid": 7,
            "server": "mini-pvp",
            "expires_at": 9999999999999,
        }

    async def now_utc(self):
        class _Now:
            def timestamp(self) -> float:
                return 1.0

        return _Now()

    async def find_player_by_uuid(self, uuid: str) -> PlayerRecord | None:
        assert uuid == "uuid-7"
        return PlayerRecord(pid=7, uuid="uuid-7", nickname="Target")

    async def _get_player_or_reply(
        self, interaction: _Interaction, player_id: int
    ) -> PlayerRecord | None:
        assert player_id == 7
        return PlayerRecord(pid=7, uuid="uuid-7", nickname="Target", discord_id="555")

    async def publish_discord_link_confirm(self, **kwargs) -> None:
        self.published.append(kwargs)

    async def publish_discord_unlink(self, **kwargs) -> None:
        self.published.append(kwargs)

    async def find_players_by_discord_id(self, discord_id: str) -> list[PlayerRecord]:
        assert discord_id == "555"
        return [
            PlayerRecord(pid=7, uuid="uuid-7", nickname="Target"),
            PlayerRecord(pid=9, uuid="uuid-9", nickname="Alt"),
        ]


@pytest.mark.asyncio
async def test_cmd_link_publishes_confirm_event() -> None:
    bot = _Bot()
    interaction = _Interaction()

    await cmd_link(cast(Any, bot), cast(Any, interaction), "abc123")

    assert bot.published == [
        {
            "code": "ABC123",
            "player_uuid": "uuid-7",
            "player_pid": 7,
            "player_name": "Target",
            "discord_id": "555",
            "discord_username": "discord-user",
        }
    ]
    assert interaction.response.sent == [
        ("Link request sent for `Target` (`pid=7`). Return in-game in a moment.", True)
    ]


@pytest.mark.asyncio
async def test_cmd_link_instantly_grants_admin_if_user_has_role() -> None:
    bot = _Bot()
    bot.admin_calls = []
    bot.access_changed_calls = []

    async def _get_admin_ids():
        return {"555"}

    async def _set_admin_access(**kwargs):
        bot.admin_calls.append(kwargs)
        return True, True

    async def _publish_admin_access(**kwargs):
        bot.access_changed_calls.append(kwargs)

    bot.get_discord_admin_member_ids = _get_admin_ids
    bot.set_admin_access = _set_admin_access
    bot.publish_discord_admin_access_changed = _publish_admin_access

    interaction = _Interaction()
    await cmd_link(cast(Any, bot), cast(Any, interaction), "abc123")

    assert len(bot.admin_calls) == 1
    assert bot.admin_calls[0] == {
        "uuid": "uuid-7",
        "is_admin": True,
        "admin_source": "DISCORD_ROLE",
    }
    assert len(bot.access_changed_calls) == 1
    assert bot.access_changed_calls[0]["admin"] is True
    assert "Права администратора выданы моментально" in interaction.response.sent[0][0]


@pytest.mark.asyncio
async def test_cmd_unlink_publishes_with_display_name() -> None:
    bot = _Bot()
    interaction = _Interaction()

    from xcore_discord_bot.handlers_linking import cmd_unlink

    await cmd_unlink(cast(Any, bot), cast(Any, interaction), 7)

    assert len(bot.published) == 1
    unlink_call = bot.published[0]
    assert unlink_call["actor_name"] == "discord-user"
    assert unlink_call["actor_name"] != "discord"


@pytest.mark.asyncio
async def test_cmd_link_status_renders_same_linked_account_output() -> None:
    bot = _Bot()
    interaction = _Interaction()

    await cmd_link_status(cast(Any, bot), cast(Any, interaction))

    assert interaction.response.sent == []
    assert len(interaction.response.embeds) == 1
    embed, ephemeral = interaction.response.embeds[0]
    assert ephemeral is True
    assert embed.title == "Linked Mindustry accounts"
    assert embed.description == "`7` — Target\n`9` — Alt"
