from __future__ import annotations

from dataclasses import dataclass, field
from types import SimpleNamespace
from typing import Any, cast
from unittest.mock import create_autospec

import pytest
from backends import real_bot
from bot_fakes import make_settings

from xcore_discord_bot.handlers_subnets import (
    cmd_subnet_import,
    cmd_subnet_list,
    parse_import,
)


@dataclass
class _Response:
    done: bool = False
    sent: list[tuple[str, bool]] = field(default_factory=list)

    def is_done(self) -> bool:
        return self.done

    async def defer(self, *, ephemeral: bool) -> None:
        assert ephemeral
        self.done = True

    async def send_message(self, text: str, *, ephemeral: bool) -> None:
        self.done = True
        self.sent.append((text, ephemeral))


@dataclass
class _Interaction:
    response: _Response = field(default_factory=_Response)
    followups: list[tuple[str, bool]] = field(default_factory=list)
    user: Any = field(
        default_factory=lambda: SimpleNamespace(id=7, display_name="Admin")
    )

    class _Followup:
        def __init__(self, parent: _Interaction) -> None:
            self.parent = parent

        async def send(self, text: str, *, ephemeral: bool) -> None:
            self.parent.followups.append((text, ephemeral))

    @property
    def followup(self) -> _Followup:
        return self._Followup(self)


async def _bot():
    bot, _ = await real_bot(settings=make_settings(rpc_timeout_ms=1234))
    bot.container.servers.update_server("prod", 1, 0, 10, "test")
    # The game server's answers; everything up to the RPC is the bot's own code.
    sentinel = bot.container.sentinel_bus
    # autospec keeps the stubs to the real signatures.
    sentinel.rpc_subnet_rules_command = create_autospec(
        sentinel.rpc_subnet_rules_command,
        return_value=SimpleNamespace(success=True, rules=("10.0.0.0/8",)),
    )
    sentinel.rpc_subnet_rules_list = create_autospec(
        sentinel.rpc_subnet_rules_list,
        return_value=SimpleNamespace(rules=("10.0.0.0/8", "::/0")),
    )
    return bot


def test_parse_import_normalizes_and_deduplicates() -> None:
    assert parse_import("10.0.0.1/8, 10.0.0.0/8\n2001:db8::/32") == [
        "10.0.0.0/8",
        "2001:db8::/32",
    ]


@pytest.mark.asyncio
async def test_import_uses_one_rpc_and_ephemeral_followup() -> None:
    bot = await _bot()
    interaction = _Interaction()
    await cmd_subnet_import(
        cast(Any, bot), cast(Any, interaction), "10.0.0.1/8,10.0.0.0/8"
    )
    command = bot.container.sentinel_bus.rpc_subnet_rules_command
    command.assert_awaited_once()
    assert command.await_args.kwargs["operation"] == "IMPORT"
    assert command.await_args.kwargs["rules"] == ["10.0.0.0/8"]
    assert command.await_args.kwargs["target_server"] == "prod"
    assert interaction.followups[0][1] is True


@pytest.mark.asyncio
async def test_list_is_compact_and_ephemeral() -> None:
    bot = await _bot()
    interaction = _Interaction()
    await cmd_subnet_list(cast(Any, bot), cast(Any, interaction))
    bot.container.sentinel_bus.rpc_subnet_rules_list.assert_awaited_once_with(
        "prod", 1234
    )
    assert interaction.followups[0][1] is True
    assert "10.0.0.0/8" in interaction.followups[0][0]
