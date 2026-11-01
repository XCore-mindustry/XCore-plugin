"""Cases: player reports, the in-game case notice and the evidence RPC."""

from __future__ import annotations

import json
from collections.abc import Awaitable, Callable
from typing import Any

from xcore_protocol.generated.cases import (
    CasesEvidenceResponseV1,
    CasesReportSubmittedV1,
)

from ..protocol_outbound import (
    build_case_evidence_request,
    build_case_notice_command,
)
from .transport import RedisTransport

CASE_REPORT_STREAM = "xcore:evt:cases:report"
CASE_REPORT_GROUP_SUFFIX = "discord-case-report"
CASE_EVIDENCE_TTL_MS = 15_000


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


class CasesBus:
    def __init__(self, transport: RedisTransport) -> None:
        self._transport = transport

    async def consume_case_reports(
        self,
        callback: Callable[[CasesReportSubmittedV1], Awaitable[None]],
        *,
        is_active: Callable[[], bool] | None = None,
    ) -> None:
        await self._transport.consume(
            stream=CASE_REPORT_STREAM,
            group_suffix=CASE_REPORT_GROUP_SUFFIX,
            parse_payload=parse_case_report_payload,
            callback=callback,
            is_active=is_active,
        )

    async def publish_case_notice(
        self, *, notice_id: str, player_uuid: str, server: str
    ) -> None:
        request = build_case_notice_command(
            server=server, notice_id=notice_id, player_uuid=player_uuid
        )
        await self._transport.publish(
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
        body = await self._transport.rpc_request(
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
