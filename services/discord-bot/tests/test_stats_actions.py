from __future__ import annotations

from dataclasses import dataclass, field
from types import SimpleNamespace
from typing import Any, cast

import pytest

from xcore_discord_bot.bot import (
    XCoreDiscordBot,
    _StatsActionsView,
    _StatsBanModal,
    _StatsMuteModal,
)
from xcore_discord_bot.dto import PlayerRecord
from xcore_discord_bot.handlers_misc import cmd_stats


@dataclass
class _Role:
    id: int


@dataclass
class _User:
    id: int
    display_name: str
    roles: list[_Role]


@dataclass
class _Message:
    edits: list[dict[str, Any]] = field(default_factory=list)

    async def edit(self, *, view: Any = None) -> None:
        self.edits.append({"view": view})


@dataclass
class _Response:
    sent: list[dict[str, Any]] = field(default_factory=list)
    modals: list[Any] = field(default_factory=list)

    async def send_message(
        self,
        content: str | None = None,
        *,
        embed: Any = None,
        ephemeral: bool = False,
        view: Any = None,
    ) -> None:
        self.sent.append(
            {
                "content": content,
                "embed": embed,
                "ephemeral": ephemeral,
                "view": view,
            }
        )

    async def send_modal(self, modal: Any) -> None:
        self.modals.append(modal)


@dataclass
class _Interaction:
    id: int
    user: _User
    client: Any | None = None
    response: _Response = field(default_factory=_Response)
    _message: _Message = field(default_factory=_Message)

    async def original_response(self) -> _Message:
        return self._message


class _Store:
    async def find_player_by_pid(self, pid: int) -> PlayerRecord | None:
        if pid != 123:
            return None
        return PlayerRecord(
            pid=123,
            nickname="Vortex",
            uuid="uuid-123",
            discord_id="discord-123",
            custom_nickname="",
            description="Profile text",
            language="ru",
            translator_language="uk",
            hexed_rank=0,
            hexed_points=0,
            total_play_time=10,
            leaderboard=False,
            unlocked_badges=("developer", "translator"),
            active_badge="translator",
            is_admin=False,
            admin_source="NONE",
            created_at=0,
            updated_at=0,
        )

    async def find_players_by_discord_id(self, discord_id: str) -> list[PlayerRecord]:
        if discord_id != "77":
            return []
        return [
            PlayerRecord(pid=-4, nickname="Alt", total_play_time=5, discord_id="77"),
            PlayerRecord(pid=9, nickname="Main", total_play_time=900, discord_id="77"),
        ]

    async def find_ban(self, *, uuid: str, ip: str | None) -> None:
        assert uuid == "uuid-123"

    async def find_mute(self, *, uuid: str) -> None:
        assert uuid == "uuid-123"

    async def count_audit_for_player(self, *, uuid: str) -> int:
        assert uuid == "uuid-123"
        return 1

    async def count_audit_for_actor(
        self, *, actor_id: str, actor_discord_id: str | None
    ) -> int:
        assert actor_id == "Vortex"
        assert actor_discord_id == "discord-123"
        return 1

    async def list_audit_for_player(self, *, uuid: str, limit: int, page: int):
        assert uuid == "uuid-123"
        assert limit == 6
        assert page == 0
        from xcore_discord_bot.dto import AuditRecordSummary

        return [
            AuditRecordSummary(
                audit_id="audit-1",
                action="BAN",
                actor_name="Admin",
                reason="Rule 1",
                occurred_at="2026-04-11T15:30:00Z",
            )
        ]

    async def list_audit_for_actor(
        self,
        *,
        actor_id: str,
        actor_discord_id: str | None,
        limit: int,
        page: int,
    ):
        assert actor_id == "Vortex"
        assert actor_discord_id == "discord-123"
        assert limit == 6
        assert page == 0
        from xcore_discord_bot.dto import AuditRecordSummary

        return [
            AuditRecordSummary(
                audit_id="audit-2",
                action="MUTE",
                target_name="Target",
                actor_name="Moderator",
                reason="Flood",
                occurred_at="2026-04-11T15:30:00Z",
            )
        ]


@pytest.mark.asyncio
async def test_cmd_stats_attaches_actions_view() -> None:
    bot = object.__new__(XCoreDiscordBot)
    bot.__dict__["_store"] = _Store()
    bot.__dict__["_settings"] = SimpleNamespace(discord_admin_role_id=5)

    async def _no_op_ban(
        _interaction: Any, _player_id: int, _period: str, _reason: str
    ) -> None:
        return None

    async def _no_op_mute(
        _interaction: Any, _player_id: int, _period: str, _reason: str
    ) -> None:
        return None

    bot.__dict__["_create_stats_ban_modal"] = lambda **kwargs: _StatsBanModal(
        player_id=kwargs["player_id"],
        player=kwargs["player"],
        on_submit_ban=cast(Any, _no_op_ban),
    )
    bot.__dict__["_create_stats_mute_modal"] = lambda **kwargs: _StatsMuteModal(
        player_id=kwargs["player_id"],
        player=kwargs["player"],
        on_submit_mute=cast(Any, _no_op_mute),
    )

    interaction = _Interaction(
        id=1,
        user=_User(id=9, display_name="admin", roles=[_Role(5)]),
        client=bot,
    )
    await cmd_stats(bot, cast(Any, interaction), 123)

    assert len(interaction.response.sent) == 1
    sent = interaction.response.sent[0]
    assert sent["embed"] is not None
    assert isinstance(sent["view"], _StatsActionsView)
    assert sent["view"].message is interaction._message
    assert len(sent["view"].children) == 4
    embed = sent["embed"]
    assert embed.title == "Vortex"
    assert "`#123`" in embed.description
    assert "🎖️ Translator" in embed.description
    assert "> Profile text" in embed.description
    fields = {field.name: field.value for field in embed.fields}
    assert fields["🎖️ Badges"] == "Developer · **Translator** (shown)"
    # what only the admins are shown
    assert "🔨 Ban: none" in fields["🔒 Staff notes"]
    assert "🔇 Mute: none" in fields["🔒 Staff notes"]
    assert "🌐 Language: `ru` · translator: `uk`" in fields["🔒 Staff notes"]
    assert "🙈 Hidden from the leaderboards" in fields["🔒 Staff notes"]
    # a bot that cannot reach the ladders or the games still opens the profile
    assert fields["🏆 Season ratings"] == "Unavailable right now"
    assert fields["🎮 Games"] == "Unavailable right now"


@pytest.mark.asyncio
async def test_cmd_stats_hides_actions_for_non_admin() -> None:
    bot = object.__new__(XCoreDiscordBot)
    bot.__dict__["_store"] = _Store()
    bot.__dict__["_settings"] = SimpleNamespace(discord_admin_role_id=5)

    async def _no_op_ban(
        _interaction: Any, _player_id: int, _period: str, _reason: str
    ) -> None:
        return None

    async def _no_op_mute(
        _interaction: Any, _player_id: int, _period: str, _reason: str
    ) -> None:
        return None

    bot.__dict__["_create_stats_ban_modal"] = lambda **kwargs: _StatsBanModal(
        player_id=kwargs["player_id"],
        player=kwargs["player"],
        on_submit_ban=cast(Any, _no_op_ban),
    )
    bot.__dict__["_create_stats_mute_modal"] = lambda **kwargs: _StatsMuteModal(
        player_id=kwargs["player_id"],
        player=kwargs["player"],
        on_submit_mute=cast(Any, _no_op_mute),
    )

    interaction = _Interaction(
        id=5,
        user=_User(id=10, display_name="guest", roles=[_Role(2)]),
        client=bot,
    )
    await cmd_stats(bot, cast(Any, interaction), 123)

    assert len(interaction.response.sent) == 1
    sent = interaction.response.sent[0]
    assert sent["embed"] is not None
    assert sent["view"] is None
    assert "🔒 Staff notes" not in {field.name for field in sent["embed"].fields}


@pytest.mark.asyncio
async def test_cmd_stats_without_a_player_opens_the_linked_account() -> None:
    bot = object.__new__(XCoreDiscordBot)
    bot.__dict__["_store"] = _Store()
    bot.__dict__["_settings"] = SimpleNamespace(discord_admin_role_id=5)

    interaction = _Interaction(
        id=6,
        user=_User(id=77, display_name="guest", roles=[_Role(2)]),
        client=bot,
    )
    await cmd_stats(bot, cast(Any, interaction))

    embed = interaction.response.sent[0]["embed"]
    # the most played of the linked accounts, the other one named below it
    assert embed.title == "Main"
    assert embed.footer.text == "Also linked: #-4 Alt"


@pytest.mark.asyncio
async def test_cmd_stats_without_a_linked_account_says_how_to_link() -> None:
    bot = object.__new__(XCoreDiscordBot)
    bot.__dict__["_store"] = _Store()
    bot.__dict__["_settings"] = SimpleNamespace(discord_admin_role_id=5)

    interaction = _Interaction(
        id=7,
        user=_User(id=10, display_name="guest", roles=[_Role(2)]),
        client=bot,
    )
    await cmd_stats(bot, cast(Any, interaction))

    sent = interaction.response.sent[0]
    assert sent["embed"] is None
    assert sent["ephemeral"] is True
    assert "/link" in sent["content"]


@pytest.mark.asyncio
async def test_cmd_stats_by_discord_user_is_for_admins() -> None:
    bot = object.__new__(XCoreDiscordBot)
    bot.__dict__["_store"] = _Store()
    bot.__dict__["_settings"] = SimpleNamespace(discord_admin_role_id=5)
    linked = _User(id=77, display_name="linked", roles=[])

    guest = _Interaction(
        id=8, user=_User(id=10, display_name="guest", roles=[_Role(2)]), client=bot
    )
    await cmd_stats(bot, cast(Any, guest), None, cast(Any, linked))

    assert guest.response.sent[0]["embed"] is None
    assert guest.response.sent[0]["ephemeral"] is True
    assert "Only admins" in guest.response.sent[0]["content"]

    admin = _Interaction(
        id=9, user=_User(id=9, display_name="admin", roles=[_Role(5)]), client=bot
    )
    await cmd_stats(bot, cast(Any, admin), None, cast(Any, linked))

    assert admin.response.sent[0]["embed"].title == "Main"


@pytest.mark.asyncio
async def test_stats_actions_view_blocks_non_admin() -> None:
    bot = object.__new__(XCoreDiscordBot)
    bot.__dict__["_settings"] = SimpleNamespace(discord_admin_role_id=5)

    async def _no_op_ban(
        _interaction: Any, _player_id: int, _period: str, _reason: str
    ) -> None:
        return None

    async def _no_op_mute(
        _interaction: Any, _player_id: int, _period: str, _reason: str
    ) -> None:
        return None

    view = _StatsActionsView(
        settings=bot._settings,
        player_id=123,
        player={"nickname": "Vortex"},
        create_ban_modal=lambda **kwargs: _StatsBanModal(
            player_id=kwargs["player_id"],
            player=kwargs["player"],
            on_submit_ban=cast(Any, _no_op_ban),
        ),
        create_mute_modal=lambda **kwargs: _StatsMuteModal(
            player_id=kwargs["player_id"],
            player=kwargs["player"],
            on_submit_mute=cast(Any, _no_op_mute),
        ),
        open_target_audit=lambda *_args, **_kwargs: None,
        open_actor_audit=lambda *_args, **_kwargs: None,
    )

    interaction = _Interaction(
        id=2,
        user=_User(id=8, display_name="guest", roles=[_Role(3)]),
    )
    allowed = await view.interaction_check(cast(Any, interaction))

    assert allowed is False
    assert interaction.response.sent == [
        {
            "content": "Access denied. Required admin role.",
            "embed": None,
            "ephemeral": True,
            "view": None,
        }
    ]


@pytest.mark.asyncio
async def test_stats_actions_view_ban_button_opens_modal() -> None:
    bot = object.__new__(XCoreDiscordBot)
    bot.__dict__["_settings"] = SimpleNamespace(discord_admin_role_id=5)

    async def _no_op_ban(
        _interaction: Any, _player_id: int, _period: str, _reason: str
    ) -> None:
        return None

    async def _no_op_mute(
        _interaction: Any, _player_id: int, _period: str, _reason: str
    ) -> None:
        return None

    view = _StatsActionsView(
        settings=bot._settings,
        player_id=123,
        player={"nickname": "Vortex"},
        create_ban_modal=lambda **kwargs: _StatsBanModal(
            player_id=kwargs["player_id"],
            player=kwargs["player"],
            on_submit_ban=cast(Any, _no_op_ban),
        ),
        create_mute_modal=lambda **kwargs: _StatsMuteModal(
            player_id=kwargs["player_id"],
            player=kwargs["player"],
            on_submit_mute=cast(Any, _no_op_mute),
        ),
        open_target_audit=lambda *_args, **_kwargs: None,
        open_actor_audit=lambda *_args, **_kwargs: None,
    )

    interaction = _Interaction(
        id=3,
        user=_User(id=9, display_name="admin", roles=[_Role(5)]),
    )

    ban_button = view.children[0]
    callback = ban_button.callback
    assert callback is not None
    await callback(cast(Any, interaction))

    assert len(interaction.response.modals) == 1
    assert isinstance(interaction.response.modals[0], _StatsBanModal)


@pytest.mark.asyncio
async def test_stats_actions_view_history_button_opens_paginated_audit() -> None:
    bot = object.__new__(XCoreDiscordBot)
    bot.__dict__["_store"] = _Store()
    bot.__dict__["_settings"] = SimpleNamespace(discord_admin_role_id=5)

    async def _no_op_ban(
        _interaction: Any, _player_id: int, _period: str, _reason: str
    ) -> None:
        return None

    async def _no_op_mute(
        _interaction: Any, _player_id: int, _period: str, _reason: str
    ) -> None:
        return None

    bot.__dict__["_create_stats_ban_modal"] = lambda **kwargs: _StatsBanModal(
        player_id=kwargs["player_id"],
        player=kwargs["player"],
        on_submit_ban=cast(Any, _no_op_ban),
    )
    bot.__dict__["_create_stats_mute_modal"] = lambda **kwargs: _StatsMuteModal(
        player_id=kwargs["player_id"],
        player=kwargs["player"],
        on_submit_mute=cast(Any, _no_op_mute),
    )

    async def _send_paginated(
        interaction: Any, fetch_page, *, ephemeral: bool = False, allowed_mentions=None
    ) -> None:
        del allowed_mentions
        embed, _has_next = await fetch_page(0)
        await interaction.response.send_message(embed=embed, ephemeral=ephemeral)

    bot.__dict__["_send_paginated"] = _send_paginated

    interaction = _Interaction(
        id=6,
        user=_User(id=9, display_name="admin", roles=[_Role(5)]),
    )
    view = _StatsActionsView(
        settings=bot._settings,
        player_id=123,
        player={"nickname": "Vortex", "uuid": "uuid-123", "discord_id": "discord-123"},
        create_ban_modal=lambda **kwargs: _StatsBanModal(
            player_id=kwargs["player_id"],
            player=kwargs["player"],
            on_submit_ban=cast(Any, _no_op_ban),
        ),
        create_mute_modal=lambda **kwargs: _StatsMuteModal(
            player_id=kwargs["player_id"],
            player=kwargs["player"],
            on_submit_mute=cast(Any, _no_op_mute),
        ),
        open_target_audit=lambda inter, pid, player: cmd_stats(bot, inter, pid),
        open_actor_audit=lambda inter, pid, player: cmd_stats(bot, inter, pid),
    )

    history_button = view.children[2]
    callback = history_button.callback
    assert callback is not None
    await callback(cast(Any, interaction))

    assert len(interaction.response.sent) == 1


@pytest.mark.asyncio
async def test_stats_actions_view_actions_button_opens_actor_audit() -> None:
    bot = object.__new__(XCoreDiscordBot)
    bot.__dict__["_store"] = _Store()
    bot.__dict__["_settings"] = SimpleNamespace(discord_admin_role_id=5)

    async def _no_op_ban(
        _interaction: Any, _player_id: int, _period: str, _reason: str
    ) -> None:
        return None

    async def _no_op_mute(
        _interaction: Any, _player_id: int, _period: str, _reason: str
    ) -> None:
        return None

    async def _send_paginated(
        interaction: Any, fetch_page, *, ephemeral: bool = False, allowed_mentions=None
    ) -> None:
        del allowed_mentions
        embed, _has_next = await fetch_page(0)
        await interaction.response.send_message(embed=embed, ephemeral=ephemeral)

    bot.__dict__["_send_paginated"] = _send_paginated

    from xcore_discord_bot.handlers_misc import cmd_stats_audit

    interaction = _Interaction(
        id=7,
        user=_User(id=9, display_name="admin", roles=[_Role(5)]),
    )
    view = _StatsActionsView(
        settings=bot._settings,
        player_id=123,
        player={"nickname": "Vortex", "uuid": "uuid-123", "discord_id": "discord-123"},
        create_ban_modal=lambda **kwargs: _StatsBanModal(
            player_id=kwargs["player_id"],
            player=kwargs["player"],
            on_submit_ban=cast(Any, _no_op_ban),
        ),
        create_mute_modal=lambda **kwargs: _StatsMuteModal(
            player_id=kwargs["player_id"],
            player=kwargs["player"],
            on_submit_mute=cast(Any, _no_op_mute),
        ),
        open_target_audit=lambda inter, pid, player: cmd_stats(bot, inter, pid),
        open_actor_audit=lambda inter, pid, player: cmd_stats_audit(
            bot, inter, pid, player, mode="actor"
        ),
    )

    actions_button = view.children[3]
    callback = actions_button.callback
    assert callback is not None
    await callback(cast(Any, interaction))

    assert len(interaction.response.sent) == 1
    sent = interaction.response.sent[0]
    assert sent["embed"] is not None
    assert sent["embed"].title == "Actions • Vortex"


@pytest.mark.asyncio
async def test_stats_mute_modal_calls_cmd_mute() -> None:
    calls: list[tuple[int, str, str]] = []

    async def _fake_cmd_mute(
        interaction: Any,
        player_id: int,
        period: str,
        reason: str,
    ) -> None:
        del interaction
        calls.append((player_id, period, reason))

    modal = _StatsMuteModal(
        player_id=123,
        player={"nickname": "Vortex"},
        on_submit_mute=cast(Any, _fake_cmd_mute),
    )
    cast(Any, modal).period = SimpleNamespace(value="10m")
    cast(Any, modal).reason = SimpleNamespace(value="")

    interaction = _Interaction(
        id=4,
        user=_User(id=9, display_name="admin", roles=[_Role(5)]),
    )
    await modal.on_submit(cast(Any, interaction))

    assert calls == [(123, "10m", "Not Specified")]
