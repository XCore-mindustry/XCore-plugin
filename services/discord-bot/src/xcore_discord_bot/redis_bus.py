from __future__ import annotations

import asyncio
import hashlib
import json
import logging
from collections.abc import Awaitable, Callable
from typing import Any, cast

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
    ActorRefV1ActorType,
    MapEntryV1,
)

from .bus.transport import RedisTransport
from .contracts import (
    DiscordLinkStatusChangedV1,
    ModerationBanCreatedV1,
    ModerationMuteCreatedV1,
    ModerationVoteKickCreatedV1,
    parse_ban_payload,
    parse_discord_link_status_payload,
    parse_mute_payload,
    parse_vote_kick_payload,
)
from .protocol_outbound import (
    build_case_evidence_request,
    build_case_notice_command,
    build_discord_admin_access_changed_command,
    build_discord_link_confirm_command,
    build_discord_unlink_command,
    build_maps_inspect_request,
    build_maps_list_request,
    build_maps_load_command,
    build_maps_remove_request,
    build_maps_upload_request,
    build_moderation_kick_banned_command,
    build_moderation_pardon_command,
    build_player_active_badge_changed_command,
    build_player_badge_inventory_changed_command,
    build_player_password_reset_command,
    build_sentinel_subnet_rules_check_request,
    build_sentinel_subnet_rules_command,
    build_sentinel_subnet_rules_list_request,
)
from .settings import Settings

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

    def __init__(self, settings: Settings) -> None:
        super().__init__(settings)
        self._ban_log_lock = asyncio.Lock()

    async def get_discord_link_code(self, code: str) -> dict[str, object] | None:
        normalized = code.strip().upper()
        if not normalized:
            return None

        payload_json = await self.redis.get(f"xcore:discord-link:code:{normalized}")
        if payload_json is None or payload_json == "":
            return None

        payload = json.loads(payload_json)
        if not isinstance(payload, dict):
            return None
        return cast(dict[str, object], payload)

    async def consume_bans(
        self, callback: Callable[[ModerationBanCreatedV1], Awaitable[None]]
    ) -> None:
        await self.consume(
            stream="xcore:evt:moderation:ban",
            group_suffix="discord-ban",
            parse_payload=parse_ban_payload,
            callback=callback,
        )

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

    async def consume_mutes(
        self, callback: Callable[[ModerationMuteCreatedV1], Awaitable[None]]
    ) -> None:
        await self.consume(
            stream="xcore:evt:moderation:mute",
            group_suffix="discord-mute",
            parse_payload=parse_mute_payload,
            callback=callback,
        )

    async def consume_vote_kicks(
        self, callback: Callable[[ModerationVoteKickCreatedV1], Awaitable[None]]
    ) -> None:
        await self.consume(
            stream="xcore:evt:moderation:votekick",
            group_suffix="discord-votekick",
            parse_payload=parse_vote_kick_payload,
            callback=callback,
        )

    async def consume_discord_link_status_changed(
        self, callback: Callable[[DiscordLinkStatusChangedV1], Awaitable[None]]
    ) -> None:
        await self.consume(
            stream="xcore:evt:discord:link-status",
            group_suffix="discord-link-status",
            parse_payload=parse_discord_link_status_payload,
            callback=callback,
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
        await self.publish_to_all_servers(
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

    async def publish_kick_banned(self, uuid_value: str, ip: str | None) -> None:
        await self.publish_to_all_servers(
            stream_prefix="xcore:cmd:kick-banned",
            event_type="moderation.kick-banned.command",
            ttl_ms=120_000,
            idempotency_prefix="moderation.kick_banned",
            payload_builder=lambda server: build_moderation_kick_banned_command(
                uuid_value=uuid_value,
                ip=ip,
                server=server,
            ).to_payload(),
        )

    async def publish_pardon_player(self, uuid_value: str) -> None:
        await self.publish_to_all_servers(
            stream_prefix="xcore:cmd:pardon-player",
            event_type="moderation.pardon.command",
            ttl_ms=120_000,
            idempotency_prefix="moderation.pardon",
            payload_builder=lambda server: build_moderation_pardon_command(
                uuid_value=uuid_value,
                server=server,
            ).to_payload(),
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

    async def publish_player_active_badge_changed(
        self, *, uuid_value: str, active_badge: str | None
    ) -> None:
        normalized_badge = (active_badge or "").strip()
        await self.publish_to_all_servers(
            stream_prefix="xcore:cmd:player-active-badge",
            event_type="player.active-badge.changed.command",
            ttl_ms=120_000,
            idempotency_prefix="player.active_badge",
            payload_builder=lambda server: build_player_active_badge_changed_command(
                uuid_value=uuid_value,
                active_badge=normalized_badge,
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
        await self.publish_to_all_servers(
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

    async def publish_player_password_reset(self, *, uuid_value: str) -> None:
        await self.publish_to_all_servers(
            stream_prefix="xcore:cmd:player-password-reset",
            event_type="player.password-reset.command",
            ttl_ms=120_000,
            idempotency_prefix="player.password_reset",
            payload_builder=lambda server: build_player_password_reset_command(
                uuid_value=uuid_value,
                server=server,
            ).to_payload(),
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
        await self.publish_to_all_servers(
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
        await self.publish_to_all_servers(
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

    async def run_ban_log_once(
        self,
        event: ModerationBanCreatedV1,
        callback: Callable[[], Awaitable[None]],
    ) -> None:
        """Checkpoint only the log for this stream delivery, not the case callback.

        Mark only after success so a failed Discord send is retryable. As with stream
        ACKs, a process death between sending and recording completion can redeliver.
        Direct calls without stream metadata have no checkpoint: payload equality
        cannot distinguish redelivery from independent legacy bans.
        """
        delivery = self.current_delivery()
        if delivery is None or delivery[0] is not event:
            await callback()
            return
        digest = hashlib.sha256(delivery[1].encode()).hexdigest()
        key = f"ban-event-log:{digest}"
        async with self._ban_log_lock:
            if await self.redis.exists(f"xcore:idmp:{key}"):
                return
            await callback()
            await self.claim_idempotency(key, ttl_seconds=604_800)

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
