from __future__ import annotations

import json
import logging
from collections.abc import Awaitable, Callable
from typing import Any

from xcore_protocol.generated.cases import (
    CasesEvidenceResponseV1,
    CasesReportSubmittedV1,
)
from xcore_protocol.generated.maps import (
    MapsInspectResponseV1,
    MapsListRequestV1,
    MapsListResponseV1,
    MapsRemoveRequestV1,
    MapsRemoveResponseV1,
    MapsUploadRequestV1,
    MapsUploadResponseV1,
)
from xcore_protocol.generated.security import (
    SecurityStaffResetPasswordRequestV1,
    SecurityStaffResetPasswordResponseV1,
    SecurityStaffSyncRequestV1,
    SecurityStaffSyncResponseV1,
)
from xcore_protocol.generated.sentinel import (
    SentinelSubnetRulesCheckRequestV1,
    SentinelSubnetRulesCheckResponseV1,
    SentinelSubnetRulesCommandV1,
    SentinelSubnetRulesListRequestV1,
    SentinelSubnetRulesListResponseV1,
    SentinelSubnetRulesResponseV1,
)
from xcore_protocol.generated.shared import (
    MapEntryV1,
)

from .bus.transport import RedisTransport
from .protocol_outbound import (
    build_case_evidence_request,
    build_case_notice_command,
    build_maps_inspect_request,
    build_maps_list_request,
    build_maps_load_command,
    build_maps_remove_request,
    build_maps_upload_request,
    build_sentinel_subnet_rules_check_request,
    build_sentinel_subnet_rules_command,
    build_sentinel_subnet_rules_list_request,
)

logger = logging.getLogger(__name__)

CASE_REPORT_STREAM = "xcore:evt:cases:report"
CASE_REPORT_GROUP_SUFFIX = "discord-case-report"
CASE_EVIDENCE_TTL_MS = 15_000
MAP_INSPECT_TTL_MS = 60_000


def _validate_case_evidence(event: Any) -> None:
    # Strict generated nested decoders reject identifiers/addresses in chat. Complete
    # the schema bounds that the current generator does not emit.
    if len(event.chat) > 30:
        raise ValueError("Case evidence has more than 30 chat lines")
    for line in event.chat:
        if len(line.playerName) > 64 or len(line.text) > 256:
            raise ValueError("Case chat line exceeds the protocol limits")
    if event.blockSummary is not None and len(event.blockSummary.topBlocks) > 8:
        raise ValueError("Case block summary exceeds the protocol limits")


def parse_case_report_payload(payload: dict[str, Any]) -> CasesReportSubmittedV1:
    event = CasesReportSubmittedV1.from_payload(payload)
    # PlayerRef allows IPs elsewhere; the report schema explicitly forbids them,
    # including an IP key whose value is null. Do not silently strip leaked data.
    if any("ip" in payload[party] for party in ("reporter", "target")):
        raise ValueError("Case report parties must omit ip")
    for name, limit in (("submissionId", 64), ("comment", 300), ("reporterLocale", 16)):
        value = getattr(event, name)
        if value is not None and len(value) > limit:
            raise ValueError(f"{name} exceeds the protocol limit")
    _validate_case_evidence(event)
    return event


class RedisBus(RedisTransport):
    """The typed streams, commands and RPC calls of every domain, on the transport."""

    async def consume_case_reports(
        self,
        callback: Callable[[CasesReportSubmittedV1], Awaitable[None]],
        *,
        is_active: Callable[[], bool] | None = None,
    ) -> None:
        await self.consume(
            stream=CASE_REPORT_STREAM,
            group_suffix=CASE_REPORT_GROUP_SUFFIX,
            parse_payload=parse_case_report_payload,
            callback=callback,
            is_active=is_active,
        )

    async def publish_maps_load(
        self, server: str, files: list[dict[str, object]]
    ) -> None:
        await self.publish(
            stream=f"xcore:cmd:maps-load:{server}",
            event_type="maps.load.command",
            ttl_ms=300_000,
            server=server,
            payload=build_maps_load_command(
                server=server,
                files=files,
            ).to_payload(),
            idempotency_prefix="maps.load",
        )

    async def publish_case_notice(
        self, *, notice_id: str, player_uuid: str, server: str
    ) -> None:
        request = build_case_notice_command(
            server=server, notice_id=notice_id, player_uuid=player_uuid
        )
        await self.publish(
            stream=f"xcore:cmd:cases-notice:{server}",
            event_type=request.MESSAGE_TYPE,
            ttl_ms=120_000,
            server=server,
            payload=request.to_payload(),
            idempotency_prefix="cases.notice",
        )

    async def rpc_case_evidence(
        self,
        *,
        server: str,
        player_uuid: str,
        chat_lines: int = 30,
        window_seconds: int = 600,
    ) -> CasesEvidenceResponseV1:
        request = build_case_evidence_request(
            server=server,
            player_uuid=player_uuid,
            chat_lines=chat_lines,
            window_seconds=window_seconds,
        )
        body = await self.rpc_request(
            server=server,
            rpc_type=request.MESSAGE_TYPE,
            payload=request.to_payload(),
            timeout_ms=CASE_EVIDENCE_TTL_MS,
        )
        response = CasesEvidenceResponseV1.from_payload(
            json.loads(body.get("payload_json", "{}"))
        )
        if response.server != server:
            raise ValueError("Case evidence response does not match request server")
        _validate_case_evidence(response)
        return response

    async def rpc_inspect_map(
        self, *, server: str, url: str, file_name: str, profile: str | None = None
    ) -> MapsInspectResponseV1:
        request = build_maps_inspect_request(
            server=server, url=url, file_name=file_name, profile=profile
        )
        body = await self.rpc_request(
            server=server,
            rpc_type=request.MESSAGE_TYPE,
            payload=request.to_payload(),
            timeout_ms=MAP_INSPECT_TTL_MS,
        )
        response = MapsInspectResponseV1.from_payload(
            json.loads(body.get("payload_json", "{}"))
        )
        if response.server != server:
            raise ValueError("Map inspection response does not match request server")
        return response

    async def rpc_staff_sync(
        self,
        request: SecurityStaffSyncRequestV1,
        *,
        timeout_ms: int,
    ) -> SecurityStaffSyncResponseV1:
        body = await self.rpc_request(
            server=request.server,
            rpc_type=request.MESSAGE_TYPE,
            payload=request.to_payload(),
            timeout_ms=timeout_ms,
        )
        response = SecurityStaffSyncResponseV1.from_payload(
            json.loads(body.get("payload_json", "{}"))
        )
        if (
            response.server != request.server
            or response.operationId != request.operationId
        ):
            raise ValueError("Staff sync response does not match request")
        return response

    async def rpc_staff_reset_password(
        self,
        request: SecurityStaffResetPasswordRequestV1,
        *,
        timeout_ms: int,
    ) -> SecurityStaffResetPasswordResponseV1:
        body = await self.rpc_request(
            server=request.server,
            rpc_type=request.MESSAGE_TYPE,
            payload=request.to_payload(),
            timeout_ms=timeout_ms,
        )
        response = SecurityStaffResetPasswordResponseV1.from_payload(
            json.loads(body.get("payload_json", "{}"))
        )
        if (
            response.server != request.server
            or response.operationId != request.operationId
        ):
            raise ValueError("Password reset response does not match request")
        return response

    async def rpc_maps_list(self, server: str, timeout_ms: int) -> list[dict[str, str]]:
        body = await self.rpc_request(
            server=server,
            rpc_type=MapsListRequestV1.MESSAGE_TYPE,
            payload=build_maps_list_request(server=server).to_payload(),
            timeout_ms=timeout_ms,
        )

        payload_json = body.get("payload_json", "{}")
        payload = json.loads(payload_json)
        response = MapsListResponseV1.from_payload(payload)
        return [self._normalize_map_entry_v1(entry) for entry in response.maps]

    async def rpc_subnet_rules_command(
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
        payload = build_sentinel_subnet_rules_command(
            operation=operation,
            rules=rules,
            discord_id=discord_id,
            target_server=target_server,
            source=source,
            reason=reason,
            discord_username=discord_username,
            expires_at=expires_at,
        ).to_payload()
        body = await self.rpc_request(
            server=target_server,
            rpc_type=SentinelSubnetRulesCommandV1.MESSAGE_TYPE,
            payload=payload,
            timeout_ms=timeout_ms,
        )
        return SentinelSubnetRulesResponseV1.from_payload(
            json.loads(body.get("payload_json", "{}"))
        )

    async def rpc_subnet_rules_list(
        self, target_server: str, timeout_ms: int
    ) -> SentinelSubnetRulesListResponseV1:
        body = await self.rpc_request(
            server=target_server,
            rpc_type=SentinelSubnetRulesListRequestV1.MESSAGE_TYPE,
            payload=build_sentinel_subnet_rules_list_request(
                target_server
            ).to_payload(),
            timeout_ms=timeout_ms,
        )
        return SentinelSubnetRulesListResponseV1.from_payload(
            json.loads(body.get("payload_json", "{}"))
        )

    async def rpc_subnet_rules_check(
        self, target_server: str, ip: str, timeout_ms: int
    ) -> SentinelSubnetRulesCheckResponseV1:
        body = await self.rpc_request(
            server=target_server,
            rpc_type=SentinelSubnetRulesCheckRequestV1.MESSAGE_TYPE,
            payload=build_sentinel_subnet_rules_check_request(
                target_server, ip
            ).to_payload(),
            timeout_ms=timeout_ms,
        )
        return SentinelSubnetRulesCheckResponseV1.from_payload(
            json.loads(body.get("payload_json", "{}"))
        )

    @staticmethod
    def _normalize_map_entry_v1(entry: MapEntryV1) -> dict[str, str]:
        return {
            "name": entry.name,
            "file_name": entry.fileName,
            "author": entry.author,
            "width": str(entry.width) if entry.width is not None else "",
            "height": str(entry.height) if entry.height is not None else "",
            "file_size_bytes": str(entry.fileSizeBytes)
            if entry.fileSizeBytes is not None
            else "",
            "like": str(entry.like) if entry.like is not None else "",
            "dislike": str(entry.dislike) if entry.dislike is not None else "",
            "reputation": str(entry.reputation) if entry.reputation is not None else "",
            "popularity": str(entry.popularity) if entry.popularity is not None else "",
            "interest": str(entry.interest) if entry.interest is not None else "",
            "game_mode": entry.gameMode if entry.gameMode is not None else "",
            **(
                {
                    "uploaded_by_discord_id": entry.discordUpload.uploadedBy.discordId,
                    "uploaded_by_name": entry.discordUpload.uploadedBy.discordUsername
                    or "",
                    "uploaded_at": str(entry.discordUpload.uploadedAt),
                    "uploaded_by_handle": entry.discordUpload.uploadedByHandle or "",
                    "creator_handle": entry.discordUpload.creatorHandle or "",
                    "creator_discord_id": entry.discordUpload.creator.discordId
                    if entry.discordUpload.creator
                    else "",
                    "creator_name": entry.discordUpload.creator.discordUsername or ""
                    if entry.discordUpload.creator
                    else "",
                }
                if entry.discordUpload
                else {}
            ),
        }

    async def rpc_upload_maps(
        self, *, server: str, files: list[dict[str, object]], timeout_ms: int
    ) -> MapsUploadResponseV1:
        body = await self.rpc_request(
            server=server,
            rpc_type=MapsUploadRequestV1.MESSAGE_TYPE,
            payload=build_maps_upload_request(server, files).to_payload(),
            timeout_ms=timeout_ms,
        )
        response = MapsUploadResponseV1.from_payload(
            json.loads(body.get("payload_json", "{}"))
        )
        expected = [item.get("fileName", item.get("filename")) for item in files]
        if (
            response.server != server
            or [result.fileName for result in response.results] != expected
        ):
            raise ValueError(
                "Map upload response does not match the requested files/server"
            )
        return response

    async def rpc_remove_map(self, server: str, file_name: str, timeout_ms: int) -> str:
        body = await self.rpc_request(
            server=server,
            rpc_type=MapsRemoveRequestV1.MESSAGE_TYPE,
            payload=build_maps_remove_request(
                server=server, file_name=file_name
            ).to_payload(),
            timeout_ms=timeout_ms,
        )

        payload_json = body.get("payload_json", "{}")
        payload = json.loads(payload_json)
        response = MapsRemoveResponseV1.from_payload(payload)
        return response.result
