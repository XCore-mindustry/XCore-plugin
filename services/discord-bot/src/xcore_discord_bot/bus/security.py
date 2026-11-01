"""Security: staff role sync and staff password reset RPCs."""

from __future__ import annotations

import json

from xcore_protocol.generated.security import (
    SecurityStaffResetPasswordRequestV1,
    SecurityStaffResetPasswordResponseV1,
    SecurityStaffSyncRequestV1,
    SecurityStaffSyncResponseV1,
)

from .transport import RedisTransport


class SecurityBus:
    def __init__(self, transport: RedisTransport) -> None:
        self._transport = transport

    async def rpc_staff_sync(
        self,
        request: SecurityStaffSyncRequestV1,
        *,
        timeout_ms: int,
    ) -> SecurityStaffSyncResponseV1:
        body = await self._transport.rpc_request(
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
        body = await self._transport.rpc_request(
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
