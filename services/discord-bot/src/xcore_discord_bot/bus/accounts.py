"""Player accounts: Discord link codes, link and unlink, and badge inventories."""

from __future__ import annotations

import json
from collections.abc import Awaitable, Callable
from typing import cast

from xcore_protocol.generated import DISCORD_ACCOUNT_OPERATION_REQUEST_V1
from xcore_protocol.generated.discord import (
    DiscordAccountOperationRequestV1,
    DiscordAccountOperationResponseV1,
)
from xcore_protocol.generated.discord import (
    DiscordAccountOperationResponseV1Status as Status,
)

from ..contracts import (
    DiscordLinkStatusChangedV1,
    parse_discord_link_status_payload,
)
from ..protocol_outbound import build_player_badge_inventory_changed_command
from ..registry import LiveServerRegistry
from ..retry import TRANSIENT_EXCEPTIONS
from .transport import RedisTransport, RpcError, RpcRejected

# How long the bot waits for an answer. A request also expires after it, so a server that
# reads it late does not act on it.
ACCOUNT_OPERATION_TIMEOUT_MS = DISCORD_ACCOUNT_OPERATION_REQUEST_V1.ttlMs


def _outcome(
    request: DiscordAccountOperationRequestV1, status: Status, reason: str | None
) -> DiscordAccountOperationResponseV1:
    return DiscordAccountOperationResponseV1(
        server=request.server,
        operationId=request.operationId,
        status=status,
        reason=reason or None,
    )


class AccountsBus:
    def __init__(self, transport: RedisTransport, servers: LiveServerRegistry) -> None:
        self._transport = transport
        # Commands for every server go to the ones that sent a heartbeat lately.
        self._servers = servers

    async def get_discord_link_code(self, code: str) -> dict[str, object] | None:
        normalized = code.strip().upper()
        if not normalized:
            return None

        payload_json = await self._transport.redis.get(
            f"xcore:discord-link:code:{normalized}"
        )
        if payload_json is None or payload_json == "":
            return None

        payload = json.loads(payload_json)
        if not isinstance(payload, dict):
            return None
        return cast(dict[str, object], payload)

    async def consume_discord_link_status_changed(
        self, callback: Callable[[DiscordLinkStatusChangedV1], Awaitable[None]]
    ) -> None:
        await self._transport.consume(
            stream="xcore:evt:discord:link-status",
            group_suffix="discord-link-status",
            parse_payload=parse_discord_link_status_payload,
            callback=callback,
        )

    async def rpc_account_operation(
        self, request: DiscordAccountOperationRequestV1
    ) -> DiscordAccountOperationResponseV1:
        """Sends one link or unlink to ``request.server`` alone. An answer that does not
        prove the outcome comes back UNKNOWN, and the bot never sends it again on its own."""
        try:
            body = await self._transport.rpc_request(
                server=request.server,
                rpc_type=request.MESSAGE_TYPE,
                payload=request.to_payload(),
                timeout_ms=ACCOUNT_OPERATION_TIMEOUT_MS,
            )
            response = DiscordAccountOperationResponseV1.from_payload(
                json.loads(body.get("payload_json", "{}"))
            )
        except RpcRejected as error:  # refused before the server touched the account
            return _outcome(request, Status.REJECTED, error.error_message)
        except (RpcError, ValueError, *TRANSIENT_EXCEPTIONS):
            # A timeout or broken answer says nothing about whether the write happened.
            return _outcome(request, Status.UNKNOWN, None)
        if (
            response.server != request.server
            or response.operationId != request.operationId
        ):
            return _outcome(request, Status.UNKNOWN, None)
        return response

    async def publish_player_badge_inventory_changed(
        self,
        *,
        uuid_value: str,
        active_badge: str | None,
        unlocked_badges: list[str] | tuple[str, ...],
    ) -> None:
        normalized_badge = (active_badge or "").strip()
        badges = [str(badge).strip() for badge in unlocked_badges if str(badge).strip()]
        await self._transport.publish_to_all_servers(
            servers=self._servers.names(),
            stream_prefix="xcore:cmd:player-badge-inventory",
            event_type="player.badge-inventory.changed.command",
            ttl_ms=120_000,
            idempotency_prefix="player.badge_inventory",
            payload_builder=lambda server: build_player_badge_inventory_changed_command(
                uuid_value=uuid_value,
                active_badge=normalized_badge,
                unlocked_badges=badges,
                server=server,
            ).to_payload(),
        )
