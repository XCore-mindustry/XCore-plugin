"""Sentinel: the subnet rule command, list and check RPCs."""

from __future__ import annotations

import json

from xcore_protocol.generated.sentinel import (
    SentinelSubnetRulesCheckRequestV1,
    SentinelSubnetRulesCheckResponseV1,
    SentinelSubnetRulesCommandV1,
    SentinelSubnetRulesListRequestV1,
    SentinelSubnetRulesListResponseV1,
    SentinelSubnetRulesResponseV1,
)

from ..protocol_outbound import (
    build_sentinel_subnet_rules_check_request,
    build_sentinel_subnet_rules_command,
    build_sentinel_subnet_rules_list_request,
)
from .transport import RedisTransport


class SentinelBus:
    def __init__(self, transport: RedisTransport) -> None:
        self._transport = transport

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
        body = await self._transport.rpc_request(
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
        body = await self._transport.rpc_request(
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
        body = await self._transport.rpc_request(
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
