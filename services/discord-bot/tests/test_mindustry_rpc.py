from __future__ import annotations

from unittest.mock import AsyncMock, MagicMock

import pytest
from bot_fakes import make_rpc

from xcore_discord_bot.rpc.mindustry_rpc import MindustryRpcClient


@pytest.fixture(autouse=True)
def _no_retry_wait(monkeypatch):
    monkeypatch.setattr("xcore_discord_bot.retry.asyncio.sleep", AsyncMock())


def _client(method: str, *results) -> tuple[MindustryRpcClient, AsyncMock]:
    bus = MagicMock()
    call = AsyncMock(side_effect=list(results))
    setattr(bus, method, call)
    return make_rpc(bus), call


@pytest.mark.parametrize(
    ("method", "call"),
    [
        ("rpc_maps_list", lambda rpc: rpc.list_maps(server="s", timeout_ms=1)),
        (
            "rpc_subnet_rules_list",
            lambda rpc: rpc.subnet_rules_list(server="s", timeout_ms=1),
        ),
        (
            "rpc_subnet_rules_check",
            lambda rpc: rpc.subnet_rules_check(server="s", ip="1.2.3.4", timeout_ms=1),
        ),
    ],
)
async def test_reads_are_retried_once_on_a_transient_failure(method, call) -> None:
    rpc, bus_call = _client(method, TimeoutError(), "answer")

    assert await call(rpc) == "answer"
    assert bus_call.await_count == 2


async def test_a_read_that_keeps_failing_raises_after_the_retry() -> None:
    rpc, bus_call = _client("rpc_maps_list", ConnectionError(), ConnectionError())

    with pytest.raises(ConnectionError):
        await rpc.list_maps(server="s", timeout_ms=1)
    assert bus_call.await_count == 2


@pytest.mark.parametrize(
    ("method", "call"),
    [
        (
            "rpc_remove_map",
            lambda rpc: rpc.remove_map(server="s", file_name="a.msav", timeout_ms=1),
        ),
        (
            "rpc_upload_maps",
            lambda rpc: rpc.upload_maps(server="s", files=[], timeout_ms=1),
        ),
        (
            "rpc_subnet_rules_command",
            lambda rpc: rpc.subnet_rules_command(
                operation="ADD",
                rules=["10.0.0.0/8"],
                discord_id="1",
                target_server="s",
                timeout_ms=1,
            ),
        ),
    ],
)
async def test_writes_are_not_retried(method, call) -> None:
    # A write that timed out may still have been applied on the server.
    rpc, bus_call = _client(method, TimeoutError(), "answer")

    with pytest.raises(TimeoutError):
        await call(rpc)
    assert bus_call.await_count == 1
