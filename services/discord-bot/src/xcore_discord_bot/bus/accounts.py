"""Player accounts: Discord link codes, link and unlink, admin access, password resets and badge inventories."""

from __future__ import annotations

import json
from collections.abc import Awaitable, Callable
from typing import cast

from xcore_protocol.generated.shared import ActorRefV1ActorType

from ..contracts import (
    DiscordLinkStatusChangedV1,
    parse_discord_link_status_payload,
)
from ..protocol_outbound import (
    build_discord_admin_access_changed_command,
    build_discord_link_confirm_command,
    build_discord_unlink_command,
    build_player_badge_inventory_changed_command,
    build_player_password_reset_command,
)
from ..registry import LiveServerRegistry
from .transport import RedisTransport


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

    async def publish_discord_link_confirm(
        self,
        *,
        code: str,
        player_uuid: str,
        player_pid: int,
        player_name: str,
        discord_id: str,
        discord_username: str,
    ) -> None:
        await self._transport.publish_to_all_servers(
            servers=self._servers.names(),
            stream_prefix="xcore:cmd:discord-link-confirm",
            event_type="discord.link.confirm.command",
            ttl_ms=120_000,
            idempotency_prefix="discord.link_confirm",
            payload_builder=lambda server: build_discord_link_confirm_command(
                code=code,
                player_uuid=player_uuid,
                player_pid=player_pid,
                player_name=player_name,
                discord_id=discord_id,
                discord_username=discord_username,
                server=server,
            ).to_payload(),
        )

    async def publish_discord_unlink(
        self,
        *,
        player_uuid: str,
        player_pid: int,
        player_name: str,
        discord_id: str,
        discord_username: str,
        actor_name: str,
        actor_discord_id: str,
    ) -> None:
        await self._transport.publish_to_all_servers(
            servers=self._servers.names(),
            stream_prefix="xcore:cmd:discord-unlink",
            event_type="discord.unlink.command",
            ttl_ms=120_000,
            idempotency_prefix="discord.unlink",
            payload_builder=lambda server: build_discord_unlink_command(
                player_uuid=player_uuid,
                player_pid=player_pid,
                player_name=player_name,
                discord_id=discord_id,
                discord_username=discord_username,
                actor_name=actor_name,
                actor_discord_id=actor_discord_id,
                server=server,
            ).to_payload(),
        )

    async def publish_discord_admin_access_changed(
        self,
        *,
        player_uuid: str,
        player_pid: int,
        player_name: str,
        discord_id: str,
        discord_username: str | None,
        admin: bool,
        source_name: str,
        source_type: ActorRefV1ActorType,
        actor_name: str,
        actor_discord_id: str | None,
        actor_type: ActorRefV1ActorType,
        reason: str,
    ) -> None:
        await self._transport.publish_to_all_servers(
            servers=self._servers.names(),
            stream_prefix="xcore:cmd:discord-admin-access",
            event_type="discord.admin-access.changed.command",
            ttl_ms=120_000,
            idempotency_prefix="discord.admin_access_changed",
            payload_builder=lambda server: build_discord_admin_access_changed_command(
                player_uuid=player_uuid,
                player_pid=player_pid,
                player_name=player_name,
                discord_id=discord_id,
                discord_username=discord_username,
                admin=admin,
                source_name=source_name,
                source_type=source_type,
                actor_name=actor_name,
                actor_discord_id=actor_discord_id,
                actor_type=actor_type,
                reason=reason,
                server=server,
            ).to_payload(),
        )

    async def publish_player_password_reset(self, *, uuid_value: str) -> None:
        await self._transport.publish_to_all_servers(
            servers=self._servers.names(),
            stream_prefix="xcore:cmd:player-password-reset",
            event_type="player.password-reset.command",
            ttl_ms=120_000,
            idempotency_prefix="player.password_reset",
            payload_builder=lambda server: build_player_password_reset_command(
                uuid_value=uuid_value,
                server=server,
            ).to_payload(),
        )

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
