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
            custom_nickname="",
            description="Profile text",
            language="ru",
            translator_language="uk",
            hexed_rank=0,
            hexed_points=0,
            total_play_time=10,
            pvp_rating=1000,
            leaderboard=False,
            unlocked_badges=("developer", "translator"),
            active_badge="translator",
            is_admin=False,
            admin_source="NONE",
            created_at=0,
            updated_at=0,
        )

    async def count_audit_for_player(self, *, uuid: str) -> int:
        assert uuid == "uuid-123"
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
    assert len(sent["view"].children) == 3
    fields = {field.name: field.value for field in sent["embed"].fields}
    assert "Permissions" in fields
    assert "Admin: ❌" in fields["Permissions"]
    assert "Admin source: `NONE`" in fields["Permissions"]
    assert "Profile" in fields
    assert "Description: `Profile text`" in fields["Profile"]
    assert "Language: `ru`" in fields["Profile"]
    assert "Translator language: `uk`" in fields["Profile"]
    assert "Leaderboard: `disabled`" in fields["Profile"]
    assert "Badges" in fields
    assert "Active: `translator`" in fields["Badges"]
    assert "Unlocked: `developer, translator`" in fields["Badges"]
    assert "System: `none`" in fields["Badges"]


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
        open_audit=lambda *_args, **_kwargs: None,
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
        open_audit=lambda *_args, **_kwargs: None,
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
async def test_stats_actions_view_audit_button_opens_paginated_audit() -> None:
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
        player={"nickname": "Vortex", "uuid": "uuid-123"},
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
        open_audit=lambda inter, pid, player: cmd_stats(bot, inter, pid),
    )

    audit_button = view.children[2]
    callback = audit_button.callback
    assert callback is not None
    await callback(cast(Any, interaction))

    assert len(interaction.response.sent) == 1


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
