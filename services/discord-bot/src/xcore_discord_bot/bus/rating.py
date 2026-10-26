"""The rating family: season events from the game servers and the rating RPC calls."""

from __future__ import annotations

import json
from collections.abc import Awaitable, Callable

from xcore_protocol.generated.rating import (
    RatingAccountsMergeResponseV1,
    RatingPrizeGrantUpdateResponseV1,
    RatingSeasonPrizesSetResponseV1,
    RatingSeasonRescheduleRequestV1Operation,
    RatingSeasonRescheduleResponseV1,
)
from xcore_protocol.generated.shared import SeasonPrizeV1

from ..contracts import (
    RatingSeasonEndedV1,
    RatingSeasonEndingSoonV1,
    RatingSeasonRescheduledV1,
    RatingSeasonStartedV1,
    parse_season_ended_payload,
    parse_season_ending_soon_payload,
    parse_season_rescheduled_payload,
    parse_season_started_payload,
)
from ..protocol_outbound import (
    build_prize_delivered_request,
    build_ratings_merge_request,
    build_season_prize_add_request,
    build_season_prize_remove_request,
    build_season_reschedule_request,
)
from .transport import RedisTransport


class RatingBus:
    def __init__(self, transport: RedisTransport) -> None:
        self._transport = transport

    async def consume_season_started(
        self, callback: Callable[[RatingSeasonStartedV1], Awaitable[None]]
    ) -> None:
        await self._transport.consume(
            stream="xcore:evt:rating:season-started",
            group_suffix="discord-season-started",
            parse_payload=parse_season_started_payload,
            callback=callback,
        )

    async def consume_season_ending_soon(
        self, callback: Callable[[RatingSeasonEndingSoonV1], Awaitable[None]]
    ) -> None:
        await self._transport.consume(
            stream="xcore:evt:rating:season-ending-soon",
            group_suffix="discord-season-ending-soon",
            parse_payload=parse_season_ending_soon_payload,
            callback=callback,
        )

    async def consume_season_ended(
        self, callback: Callable[[RatingSeasonEndedV1], Awaitable[None]]
    ) -> None:
        await self._transport.consume(
            stream="xcore:evt:rating:season-ended",
            group_suffix="discord-season-ended",
            parse_payload=parse_season_ended_payload,
            callback=callback,
        )

    async def consume_season_rescheduled(
        self, callback: Callable[[RatingSeasonRescheduledV1], Awaitable[None]]
    ) -> None:
        await self._transport.consume(
            stream="xcore:evt:rating:season-rescheduled",
            group_suffix="discord-season-rescheduled",
            parse_payload=parse_season_rescheduled_payload,
            callback=callback,
        )

    async def rpc_season_reschedule(
        self,
        *,
        server: str,
        ladder: str,
        operation: RatingSeasonRescheduleRequestV1Operation,
        discord_id: str,
        actor_name: str,
        timeout_ms: int,
        extend_seconds: int | None = None,
        ends_at: str | None = None,
        reason: str | None = None,
        request_id: str | None = None,
    ) -> RatingSeasonRescheduleResponseV1:
        body = await self._transport.rpc_request(
            server=server,
            rpc_type="rating.season.reschedule.request",
            payload=build_season_reschedule_request(
                server=server,
                ladder=ladder,
                operation=operation,
                discord_id=discord_id,
                actor_name=actor_name,
                extend_seconds=extend_seconds,
                ends_at=ends_at,
                reason=reason,
                request_id=request_id,
            ).to_payload(),
            timeout_ms=timeout_ms,
        )
        return RatingSeasonRescheduleResponseV1.from_payload(
            json.loads(body.get("payload_json", "{}"))
        )

    async def rpc_ratings_merge(
        self,
        *,
        server: str,
        source_uuid: str,
        target_uuid: str,
        timeout_ms: int,
    ) -> RatingAccountsMergeResponseV1:
        body = await self._transport.rpc_request(
            server=server,
            rpc_type="rating.accounts.merge.request",
            payload=build_ratings_merge_request(
                server=server,
                source_uuid=source_uuid,
                target_uuid=target_uuid,
            ).to_payload(),
            timeout_ms=timeout_ms,
        )
        return RatingAccountsMergeResponseV1.from_payload(
            json.loads(body.get("payload_json", "{}"))
        )

    async def rpc_season_prize_add(
        self,
        *,
        server: str,
        ladder: str,
        prize: SeasonPrizeV1,
        discord_id: str,
        actor_name: str,
        timeout_ms: int,
        request_id: str | None = None,
    ) -> RatingSeasonPrizesSetResponseV1:
        body = await self._transport.rpc_request(
            server=server,
            rpc_type="rating.season.prizes.set.request",
            payload=build_season_prize_add_request(
                server=server,
                ladder=ladder,
                prize=prize,
                discord_id=discord_id,
                actor_name=actor_name,
                request_id=request_id,
            ).to_payload(),
            timeout_ms=timeout_ms,
        )
        return RatingSeasonPrizesSetResponseV1.from_payload(
            json.loads(body.get("payload_json", "{}"))
        )

    async def rpc_season_prize_remove(
        self,
        *,
        server: str,
        ladder: str,
        place_from: int,
        place_to: int,
        discord_id: str,
        actor_name: str,
        timeout_ms: int,
        request_id: str | None = None,
    ) -> RatingSeasonPrizesSetResponseV1:
        body = await self._transport.rpc_request(
            server=server,
            rpc_type="rating.season.prizes.set.request",
            payload=build_season_prize_remove_request(
                server=server,
                ladder=ladder,
                place_from=place_from,
                place_to=place_to,
                discord_id=discord_id,
                actor_name=actor_name,
                request_id=request_id,
            ).to_payload(),
            timeout_ms=timeout_ms,
        )
        return RatingSeasonPrizesSetResponseV1.from_payload(
            json.loads(body.get("payload_json", "{}"))
        )

    async def rpc_prize_delivered(
        self,
        *,
        server: str,
        ladder: str,
        season: int,
        place: int,
        discord_id: str,
        actor_name: str,
        note: str | None,
        timeout_ms: int,
        player_pid: int | None = None,
    ) -> RatingPrizeGrantUpdateResponseV1:
        body = await self._transport.rpc_request(
            server=server,
            rpc_type="rating.prize.grant.update.request",
            payload=build_prize_delivered_request(
                server=server,
                ladder=ladder,
                season=season,
                place=place,
                discord_id=discord_id,
                actor_name=actor_name,
                note=note,
                player_pid=player_pid,
            ).to_payload(),
            timeout_ms=timeout_ms,
        )
        return RatingPrizeGrantUpdateResponseV1.from_payload(
            json.loads(body.get("payload_json", "{}"))
        )
