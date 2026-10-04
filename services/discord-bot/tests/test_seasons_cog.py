from __future__ import annotations

from dataclasses import dataclass, field
from datetime import UTC, datetime, timedelta
from types import SimpleNamespace
from typing import Any

import pytest
from discord import app_commands
from xcore_protocol.generated.rating import RatingSeasonRescheduleResponseV1
from xcore_protocol.generated.shared import SeasonRefV1

from xcore_discord_bot.cogs.checks import head_admin_check
from xcore_discord_bot.cogs.seasons import SeasonsCog, parse_end_at
from xcore_discord_bot.permissions import head_admin_role_ids
from xcore_discord_bot.redis_bus import RpcRejected
from xcore_discord_bot.rpc.mindustry_rpc import NoLiveServerError

ADMIN, HEAD = 10, 20


@dataclass
class _Role:
    id: int


@dataclass
class _Response:
    sent: list[dict[str, Any]] = field(default_factory=list)
    deferred: bool = False

    async def send_message(self, content=None, **kwargs) -> None:
        self.sent.append({"content": content, **kwargs})

    async def defer(self, **kwargs) -> None:
        self.deferred = True


@dataclass
class _Followup:
    sent: list[dict[str, Any]] = field(default_factory=list)

    async def send(self, content=None, **kwargs) -> None:
        self.sent.append({"content": content, **kwargs})


class _Interaction:
    def __init__(self, roles: list[int], client: Any = None) -> None:
        self.user = SimpleNamespace(
            id=5, display_name="Head", roles=[_Role(r) for r in roles]
        )
        self.client = client
        self.response = _Response()
        self.followup = _Followup()


def _settings(general: int | None = HEAD) -> SimpleNamespace:
    return SimpleNamespace(
        discord_admin_role_id=ADMIN, discord_general_admin_role_id=general
    )


def test_head_admin_role_is_the_general_role_and_falls_back_to_admin() -> None:
    assert head_admin_role_ids(_settings(HEAD)) == (HEAD,)
    assert head_admin_role_ids(_settings(None)) == (ADMIN,)


@pytest.mark.asyncio
async def test_head_admin_check_rejects_plain_admins() -> None:
    client = SimpleNamespace(settings=_settings())
    check = head_admin_check()
    predicate = check.__closure__[0].cell_contents

    assert await predicate(_Interaction([HEAD], client)) is True
    with pytest.raises(app_commands.CheckFailure):
        await predicate(_Interaction([ADMIN], client))


def test_parse_end_at_reads_utc_dates() -> None:
    assert parse_end_at("2026-07-01") == datetime(2026, 7, 1, tzinfo=UTC)
    assert parse_end_at("2026-07-01 18:30") == datetime(2026, 7, 1, 18, 30, tzinfo=UTC)
    with pytest.raises(ValueError):
        parse_end_at("next friday")


class _RatingService:
    def __init__(self, error: Exception | None = None) -> None:
        self.error = error
        self.calls: list[tuple[str, dict[str, Any]]] = []

    def _answer(self, name: str, kwargs: dict[str, Any], ended: bool = False):
        self.calls.append((name, kwargs))
        if self.error:
            raise self.error
        return RatingSeasonRescheduleResponseV1(
            server="s",
            season=SeasonRefV1(
                ladder="minipvp",
                season=2,
                name="Spring",
                startsAt="2026-01-01T00:00:00Z",
                endsAt="2026-05-01T00:00:00Z",
            ),
            previousEndsAt="2026-04-01T00:00:00Z",
            ended=ended,
        )

    async def extend_season(self, **kwargs):
        return self._answer("extend", kwargs)

    async def set_season_end(self, **kwargs):
        return self._answer("set_end", kwargs)

    async def end_season_now(self, **kwargs):
        return self._answer("end_now", kwargs, ended=True)


def _cog(service: _RatingService) -> SeasonsCog:
    bot = SimpleNamespace(container=SimpleNamespace(rating_service=service))
    return SeasonsCog(bot)


@pytest.mark.asyncio
async def test_extend_parses_the_duration_and_defers_for_the_rpc() -> None:
    service = _RatingService()
    interaction = _Interaction([HEAD])

    await _cog(service).cmd_extend.callback(
        _cog(service), interaction, "minipvp", "3d", "  tournament  "
    )

    name, kwargs = service.calls[0]
    assert name == "extend"
    assert kwargs["by"] == timedelta(days=3)
    assert kwargs["reason"] == "tournament"
    assert kwargs["discord_id"] == "5"
    assert interaction.response.deferred is True
    assert "now ends" in interaction.followup.sent[0]["content"]


@pytest.mark.asyncio
async def test_extend_rejects_a_bad_duration_without_calling_the_server() -> None:
    service = _RatingService()
    interaction = _Interaction([HEAD])
    cog = _cog(service)

    await cog.cmd_extend.callback(cog, interaction, "minipvp", "soon", None)

    assert service.calls == []
    assert interaction.response.sent[0]["ephemeral"] is True


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("error", "expected"),
    [
        (RpcRejected("rating.season.reschedule.request", "REJECTED", "too late"), "too late"),
        (NoLiveServerError("none"), "No Mindustry server"),
        (TimeoutError(), "did not answer"),
    ],
)
async def test_admin_failures_are_worded_for_the_admin(error, expected) -> None:
    interaction = _Interaction([HEAD])
    cog = _cog(_RatingService(error))

    await cog.cmd_end_at.callback(cog, interaction, "minipvp", "2026-07-01", None)

    assert expected in interaction.followup.sent[0]["content"]
    assert interaction.followup.sent[0]["content"].startswith("❌")


@pytest.mark.asyncio
async def test_end_now_asks_for_confirmation_first() -> None:
    service = _RatingService()
    interaction = _Interaction([HEAD])
    interaction.original_response = lambda: _async(SimpleNamespace())
    cog = _cog(service)

    await cog.cmd_end_now.callback(cog, interaction, "minipvp", None)

    assert service.calls == []
    assert interaction.response.sent[0]["view"] is not None
    assert interaction.response.sent[0]["ephemeral"] is True


async def _async(value):
    return value
