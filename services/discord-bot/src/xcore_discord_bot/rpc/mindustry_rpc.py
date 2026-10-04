from __future__ import annotations

from collections.abc import Awaitable, Callable
from typing import Any, TypeVar

from xcore_protocol.generated.rating import (
    RatingAccountsMergeResponseV1,
    RatingPrizeGrantUpdateResponseV1,
    RatingSeasonPrizesSetResponseV1,
    RatingSeasonRescheduleRequestV1Operation,
    RatingSeasonRescheduleResponseV1,
)
from xcore_protocol.generated.shared import SeasonPrizeV1

from ..redis_bus import RedisBus
from ..registry import server_registry

T = TypeVar("T")

# A dead server stays in the registry until its heartbeat times out; do not wait out
# every one of them before giving up.
MAX_SERVERS_TRIED = 3


class NoLiveServerError(RuntimeError):
    """No Mindustry server is online to carry out a request that any server can answer."""


class MindustryRpcClient:
    def __init__(self, bus: RedisBus) -> None:
        self._bus = bus

    async def on_any_live_server(self, call: Callable[[str], Awaitable[T]]) -> T:
        """Runs ``call`` against a live server, trying the next one when a server stays silent.

        For requests whose answer lives in shared storage and so does not depend on which
        server gives it. A server that answers with a refusal ends the attempt: another
        server would refuse for the same reason.
        """
        names = sorted(
            (server.name for server in server_registry.get_all_servers()),
            key=str.lower,
        )[:MAX_SERVERS_TRIED]
        if not names:
            raise NoLiveServerError(
                "No Mindustry server is online to carry out this request."
            )
        last_timeout: TimeoutError | None = None
        for name in names:
            try:
                return await call(name)
            except TimeoutError as error:
                last_timeout = error
        assert last_timeout is not None
        raise last_timeout

    async def reschedule_season(
        self,
        *,
        ladder: str,
        operation: RatingSeasonRescheduleRequestV1Operation,
        discord_id: str,
        actor_name: str,
        timeout_ms: int,
        extend_seconds: int | None = None,
        ends_at: str | None = None,
        reason: str | None = None,
    ) -> RatingSeasonRescheduleResponseV1:
        return await self.on_any_live_server(
            lambda server: self._bus.rpc_season_reschedule(
                server=server,
                ladder=ladder,
                operation=operation,
                discord_id=discord_id,
                actor_name=actor_name,
                timeout_ms=timeout_ms,
                extend_seconds=extend_seconds,
                ends_at=ends_at,
                reason=reason,
            )
        )

    async def add_season_prize(
        self,
        *,
        ladder: str,
        prize: SeasonPrizeV1,
        discord_id: str,
        actor_name: str,
        timeout_ms: int,
    ) -> RatingSeasonPrizesSetResponseV1:
        return await self.on_any_live_server(
            lambda server: self._bus.rpc_season_prize_add(
                server=server,
                ladder=ladder,
                prize=prize,
                discord_id=discord_id,
                actor_name=actor_name,
                timeout_ms=timeout_ms,
            )
        )

    async def remove_season_prizes(
        self,
        *,
        ladder: str,
        place_from: int,
        place_to: int,
        discord_id: str,
        actor_name: str,
        timeout_ms: int,
    ) -> RatingSeasonPrizesSetResponseV1:
        return await self.on_any_live_server(
            lambda server: self._bus.rpc_season_prize_remove(
                server=server,
                ladder=ladder,
                place_from=place_from,
                place_to=place_to,
                discord_id=discord_id,
                actor_name=actor_name,
                timeout_ms=timeout_ms,
            )
        )

    async def mark_prize_delivered(
        self,
        *,
        ladder: str,
        season: int,
        place: int,
        discord_id: str,
        actor_name: str,
        note: str | None,
        timeout_ms: int,
    ) -> RatingPrizeGrantUpdateResponseV1:
        return await self.on_any_live_server(
            lambda server: self._bus.rpc_prize_delivered(
                server=server,
                ladder=ladder,
                season=season,
                place=place,
                discord_id=discord_id,
                actor_name=actor_name,
                note=note,
                timeout_ms=timeout_ms,
            )
        )

    async def merge_ratings(
        self,
        *,
        source_uuid: str,
        target_uuid: str,
        timeout_ms: int,
    ) -> RatingAccountsMergeResponseV1:
        return await self.on_any_live_server(
            lambda server: self._bus.rpc_ratings_merge(
                server=server,
                source_uuid=source_uuid,
                target_uuid=target_uuid,
                timeout_ms=timeout_ms,
            )
        )

    async def rpc_subnet_rules_command(self, **kwargs: Any) -> Any:
        return await self._bus.rpc_subnet_rules_command(**kwargs)

    async def rpc_subnet_rules_list(self, target_server: str, timeout_ms: int) -> Any:
        return await self._bus.rpc_subnet_rules_list(target_server, timeout_ms)

    async def rpc_subnet_rules_check(
        self, target_server: str, ip: str, timeout_ms: int
    ) -> Any:
        return await self._bus.rpc_subnet_rules_check(target_server, ip, timeout_ms)

    async def rpc_maps_list(
        self, *, server: str, timeout_ms: int
    ) -> list[dict[str, str]]:
        return await self._bus.rpc_maps_list(server=server, timeout_ms=timeout_ms)

    async def claim_idempotency(self, key: str, *, ttl_seconds: int = 600) -> bool:
        return await self._bus.claim_idempotency(key, ttl_seconds=ttl_seconds)

    async def rpc_remove_map(
        self,
        *,
        server: str,
        file_name: str,
        timeout_ms: int,
    ) -> str:
        return await self._bus.rpc_remove_map(
            server=server,
            file_name=file_name,
            timeout_ms=timeout_ms,
        )

    async def publish_maps_load(
        self,
        *,
        server: str,
        files: list[dict[str, str]],
    ) -> None:
        await self._bus.publish_maps_load(server=server, files=files)
