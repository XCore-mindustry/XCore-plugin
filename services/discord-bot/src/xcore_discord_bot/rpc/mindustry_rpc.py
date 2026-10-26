from __future__ import annotations

import asyncio
import uuid
from collections.abc import Awaitable, Callable
from typing import TypeVar

from xcore_protocol.generated.cases import CasesEvidenceResponseV1
from xcore_protocol.generated.maps import MapsInspectResponseV1, MapsUploadResponseV1
from xcore_protocol.generated.rating import (
    RatingAccountsMergeResponseV1,
    RatingPrizeGrantUpdateResponseV1,
    RatingSeasonPrizesSetResponseV1,
    RatingSeasonRescheduleRequestV1Operation,
    RatingSeasonRescheduleResponseV1,
)
from xcore_protocol.generated.security import (
    SecurityStaffResetPasswordRequestV1,
    SecurityStaffResetPasswordResponseV1,
    SecurityStaffSyncRequestV1,
    SecurityStaffSyncResponseV1,
)
from xcore_protocol.generated.sentinel import (
    SentinelSubnetRulesCheckResponseV1,
    SentinelSubnetRulesListResponseV1,
    SentinelSubnetRulesResponseV1,
)
from xcore_protocol.generated.shared import SeasonPrizeV1

from ..bus.transport import RpcFailed
from ..redis_bus import RedisBus
from ..registry import server_registry
from ..retry import TRANSIENT_EXCEPTIONS, retry_read_rpc

T = TypeVar("T")

# A dead server stays in the registry until its heartbeat times out; do not wait out
# every one of them before giving up.
MAX_SERVERS_TRIED = 3


class NoLiveServerError(RuntimeError):
    """No Mindustry server is online to carry out a request that any server can answer."""


class MindustryRpcClient:
    def __init__(self, bus: RedisBus) -> None:
        self._bus = bus

    async def _retry_staff(self, call: Callable[[], Awaitable[T]]) -> T:
        for attempt in range(2):
            try:
                return await call()
            except RpcFailed as error:
                if error.error_code != "UNAVAILABLE" or attempt == 1:
                    raise
            except TRANSIENT_EXCEPTIONS:
                if attempt == 1:
                    raise
            await asyncio.sleep(0.5)
        raise AssertionError("Staff RPC retry exhausted")

    async def sync_staff(
        self,
        *,
        server: str,
        player_uuid: str,
        discord_id: str,
        role_ids: tuple[str, ...],
        timeout_ms: int,
    ) -> SecurityStaffSyncResponseV1:
        request = SecurityStaffSyncRequestV1(
            server=server,
            operationId=self._request_id(),
            playerUuid=player_uuid,
            discordId=discord_id,
            roleIds=tuple(sorted(set(role_ids))),
            complete=True,
        )
        return await self._retry_staff(
            lambda: self._bus.rpc_staff_sync(request, timeout_ms=timeout_ms)
        )

    async def reset_staff_password(
        self,
        *,
        server: str,
        player_uuid: str,
        timeout_ms: int,
    ) -> SecurityStaffResetPasswordResponseV1:
        request = SecurityStaffResetPasswordRequestV1(
            server=server,
            operationId=self._request_id(),
            playerUuid=player_uuid,
        )
        return await self._retry_staff(
            lambda: self._bus.rpc_staff_reset_password(request, timeout_ms=timeout_ms)
        )

    async def on_any_live_server(self, call: Callable[[str], Awaitable[T]]) -> T:
        """Runs ``call`` against a live server, trying the next one when a server stays
        silent or says it could not carry the request out.

        For requests whose answer lives in shared storage and so does not depend on which
        server gives it. A server that answers with a refusal ends the attempt: another
        server would refuse for the same reason.

        A silent server may still apply the request after the next one was asked, so a
        change that must not happen twice has to carry a request id (see ``_request_id``),
        which makes every server but the first refuse it.
        """
        names = sorted(
            (server.name for server in server_registry.get_all_servers()),
            key=str.lower,
        )[:MAX_SERVERS_TRIED]
        if not names:
            raise NoLiveServerError(
                "No Mindustry server is online to carry out this request."
            )
        last_error: TimeoutError | RpcFailed | None = None
        for name in names:
            try:
                return await call(name)
            except (TimeoutError, RpcFailed) as error:
                last_error = error
        assert last_error is not None
        raise last_error

    @staticmethod
    def _request_id() -> str:
        """One id per administrator action, repeated on every server that is tried."""
        return str(uuid.uuid4())

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
        request_id = self._request_id()
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
                request_id=request_id,
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
        request_id = self._request_id()
        return await self.on_any_live_server(
            lambda server: self._bus.rpc_season_prize_add(
                server=server,
                ladder=ladder,
                prize=prize,
                discord_id=discord_id,
                actor_name=actor_name,
                timeout_ms=timeout_ms,
                request_id=request_id,
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
        request_id = self._request_id()
        return await self.on_any_live_server(
            lambda server: self._bus.rpc_season_prize_remove(
                server=server,
                ladder=ladder,
                place_from=place_from,
                place_to=place_to,
                discord_id=discord_id,
                actor_name=actor_name,
                timeout_ms=timeout_ms,
                request_id=request_id,
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
        player_pid: int | None = None,
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
                player_pid=player_pid,
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

    async def rpc_case_evidence(
        self,
        *,
        server: str,
        player_uuid: str,
        chat_lines: int = 30,
        window_seconds: int = 600,
    ) -> CasesEvidenceResponseV1:
        # Evidence belongs to this server's recent chat/history; another server
        # cannot answer for it. An offline target is a valid typed response.
        return await self._bus.rpc_case_evidence(
            server=server,
            player_uuid=player_uuid,
            chat_lines=chat_lines,
            window_seconds=window_seconds,
        )

    async def rpc_inspect_map(
        self,
        *,
        url: str,
        file_name: str,
        profile: str | None = None,
        server: str | None = None,
    ) -> MapsInspectResponseV1:
        async def inspect(name: str) -> MapsInspectResponseV1:
            return await self._bus.rpc_inspect_map(
                server=name, url=url, file_name=file_name, profile=profile
            )

        if server is not None:
            return await inspect(server)
        return await self.on_any_live_server(inspect)

    # Reads are retried on a transient failure; writes are not, since a write that timed
    # out may still have been applied.

    async def list_maps(self, *, server: str, timeout_ms: int) -> list[dict[str, str]]:
        return await retry_read_rpc(
            lambda: self._bus.rpc_maps_list(server=server, timeout_ms=timeout_ms)
        )

    async def remove_map(self, *, server: str, file_name: str, timeout_ms: int) -> str:
        return await self._bus.rpc_remove_map(
            server=server, file_name=file_name, timeout_ms=timeout_ms
        )

    async def upload_maps(
        self, *, server: str, files: list[dict[str, object]], timeout_ms: int
    ) -> MapsUploadResponseV1:
        return await self._bus.rpc_upload_maps(
            server=server, files=files, timeout_ms=timeout_ms
        )

    async def subnet_rules_command(
        self,
        *,
        operation: str,
        rules: list[str] | tuple[str, ...],
        discord_id: str,
        target_server: str,
        source: str | None = None,
        reason: str | None = None,
        discord_username: str | None = None,
        expires_at: int | None = None,
        timeout_ms: int,
    ) -> SentinelSubnetRulesResponseV1:
        return await self._bus.rpc_subnet_rules_command(
            operation=operation,
            rules=rules,
            discord_id=discord_id,
            target_server=target_server,
            source=source,
            reason=reason,
            discord_username=discord_username,
            expires_at=expires_at,
            timeout_ms=timeout_ms,
        )

    async def subnet_rules_list(
        self, *, server: str, timeout_ms: int
    ) -> SentinelSubnetRulesListResponseV1:
        return await retry_read_rpc(
            lambda: self._bus.rpc_subnet_rules_list(server, timeout_ms)
        )

    async def subnet_rules_check(
        self, *, server: str, ip: str, timeout_ms: int
    ) -> SentinelSubnetRulesCheckResponseV1:
        return await retry_read_rpc(
            lambda: self._bus.rpc_subnet_rules_check(server, ip, timeout_ms)
        )
