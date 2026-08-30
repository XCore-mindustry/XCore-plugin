from __future__ import annotations

from dataclasses import dataclass, field
from types import SimpleNamespace
from typing import Any, cast

import pytest

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
    user: Any = field(default_factory=lambda: SimpleNamespace(id=7, display_name="Admin"))

    class _Followup:
        def __init__(self, parent: _Interaction) -> None:
            self.parent = parent

        async def send(self, text: str, *, ephemeral: bool) -> None:
            self.parent.followups.append((text, ephemeral))

    @property
    def followup(self) -> _Followup:
        return self._Followup(self)


class _Bot:
    settings = SimpleNamespace(rpc_timeout_ms=1234)

    def __init__(self, response: Any = None) -> None:
        self.calls: list[dict[str, Any]] = []
        self.response = response or SimpleNamespace(success=True, rules=("10.0.0.0/8",))

    async def rpc_subnet_rules_command(self, **kwargs: Any) -> Any:
        self.calls.append(kwargs)
        return self.response

    async def rpc_subnet_rules_list(self, server: str, timeout: int) -> Any:
        self.calls.append({"server": server, "timeout": timeout})
        return SimpleNamespace(rules=("10.0.0.0/8", "::/0"))



def test_parse_import_normalizes_and_deduplicates() -> None:
    assert parse_import("10.0.0.1/8, 10.0.0.0/8\n2001:db8::/32") == [
        "10.0.0.0/8",
        "2001:db8::/32",
    ]


@pytest.mark.asyncio
async def test_import_uses_one_rpc_and_ephemeral_followup() -> None:
    bot = _Bot()
    interaction = _Interaction()
    from xcore_discord_bot.registry import server_registry
    server_registry.update_server("prod", 1, 0, 10, "test")
    await cmd_subnet_import(cast(Any, bot), cast(Any, interaction), "10.0.0.1/8,10.0.0.0/8")
    assert len(bot.calls) == 1
    assert bot.calls[0]["operation"] == "IMPORT"
    assert bot.calls[0]["rules"] == ["10.0.0.0/8"]
    assert interaction.followups[0][1] is True


@pytest.mark.asyncio
async def test_list_is_compact_and_ephemeral() -> None:
    bot = _Bot()
    interaction = _Interaction()
    from xcore_discord_bot.registry import server_registry
    server_registry.update_server("prod", 1, 0, 10, "test")
    await cmd_subnet_list(cast(Any, bot), cast(Any, interaction))
    assert interaction.followups[0][1] is True
    assert "10.0.0.0/8" in interaction.followups[0][0]
