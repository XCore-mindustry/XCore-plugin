from __future__ import annotations

import json
from datetime import UTC, datetime
from uuid import NAMESPACE_URL, uuid4, uuid5

from xcore_protocol.generated.cases import CasesEvidenceRequestV1, CasesNoticeCommandV1
from xcore_protocol.generated.chat import (
    ChatDiscordIngressCommandV1,
)
from xcore_protocol.generated.discord import (
    DiscordAccountOperationRequestV1,
    DiscordAccountOperationRequestV1Action,
)
from xcore_protocol.generated.identity import (
    PlayerActiveBadgeChangedCommandV1,
    PlayerBadgeInventoryChangedCommandV1,
)
from xcore_protocol.generated.maps import (
    MapsInspectRequestV1,
    MapsListRequestV1,
    MapsLoadCommandV1,
    MapsRemoveRequestV1,
    MapsUploadRequestV1,
)
from xcore_protocol.generated.moderation import (
    ModerationKickBannedCommandV1,
    ModerationPardonCommandV1,
)
from xcore_protocol.generated.rating import (
    RatingAccountsMergeRequestV1,
    RatingPrizeGrantUpdateRequestV1,
    RatingPrizeGrantUpdateRequestV1Status,
    RatingSeasonPrizesSetRequestV1,
    RatingSeasonPrizesSetRequestV1Operation,
    RatingSeasonRescheduleRequestV1,
    RatingSeasonRescheduleRequestV1Operation,
)
from xcore_protocol.generated.sentinel import (
    SentinelSubnetRulesCheckRequestV1,
    SentinelSubnetRulesCommandV1,
    SentinelSubnetRulesCommandV1Operation,
    SentinelSubnetRulesListRequestV1,
)
from xcore_protocol.generated.shared import (
    ActorRefV1,
    ActorRefV1ActorType,
    DiscordIdentityRefV1,
    MapFileSourceV1,
    PlayerCommandTargetV1,
    PlayerRefV1,
    SeasonPrizeV1,
)


def utc_now_iso8601() -> str:
    return datetime.now(UTC).isoformat(timespec="milliseconds")


def build_sentinel_subnet_rules_command(
    operation: SentinelSubnetRulesCommandV1Operation | str,
    rules: list[str] | tuple[str, ...],
    discord_id: str,
    request: str | None = None,
    idempotency: str | None = None,
    target_server: str | None = None,
    source: str | None = None,
    reason: str | None = None,
    discord_username: str | None = None,
    actor_name: str | None = None,
    expires_at: int | None = None,
) -> SentinelSubnetRulesCommandV1:
    return SentinelSubnetRulesCommandV1(
        request=request or str(uuid4()),
        idempotency=idempotency or str(uuid4()),
        actor=ActorRefV1(
            actorName=actor_name or discord_username or str(discord_id),
            actorDiscordId=str(discord_id),
            actorType=ActorRefV1ActorType.DISCORD,
        ),
        operation=(
            operation
            if isinstance(operation, SentinelSubnetRulesCommandV1Operation)
            else SentinelSubnetRulesCommandV1Operation(operation)
        ),
        targetServer=target_server,
        rules=tuple(rules),
        source=source,
        reason=reason,
        expiresAt=expires_at,
    )


def build_sentinel_subnet_rules_list_request(
    target_server: str, request: str | None = None
) -> SentinelSubnetRulesListRequestV1:
    return SentinelSubnetRulesListRequestV1(
        request=request or str(uuid4()), targetServer=target_server
    )


def build_sentinel_subnet_rules_check_request(
    target_server: str, ip: str, request: str | None = None
) -> SentinelSubnetRulesCheckRequestV1:
    return SentinelSubnetRulesCheckRequestV1(
        request=request or str(uuid4()), targetServer=target_server, ip=ip
    )


def build_moderation_kick_banned_command(
    uuid_value: str,
    ip: str | None,
    server: str,
) -> ModerationKickBannedCommandV1:
    return ModerationKickBannedCommandV1(
        target=PlayerCommandTargetV1(
            playerUuid=uuid_value or None,
            ip=ip,
        ),
        server=server,
        requestedAt=utc_now_iso8601(),
    )


def build_moderation_pardon_command(
    uuid_value: str,
    server: str,
) -> ModerationPardonCommandV1:
    return ModerationPardonCommandV1(
        target=PlayerCommandTargetV1(
            playerUuid=uuid_value,
        ),
        server=server,
        requestedAt=utc_now_iso8601(),
    )


def build_discord_account_operation_request(
    *,
    action: DiscordAccountOperationRequestV1Action,
    server: str,
    source_id: int,
    player_uuid: str,
    player_pid: int,
    player_name: str,
    discord_id: str,
    discord_username: str,
    issued_at: int,
    timeout_ms: int,
    code: str | None = None,
    expected_linked_at: int | None = None,
) -> DiscordAccountOperationRequestV1:
    # One Discord interaction or message is one intent: its operation ID depends only on it
    # and the exact request, so the same intent gets the same ID and a later one a new ID.
    # The target server is part of it: a request sent to another server is a new operation.
    intent = json.dumps(
        [
            action.value,
            source_id,
            server,
            discord_id,
            player_uuid,
            code,
            expected_linked_at,
        ]
    )
    return DiscordAccountOperationRequestV1(
        server=server,
        operationId=str(uuid5(NAMESPACE_URL, f"xcore:discord-bot:account:{intent}")),
        action=action,
        player=PlayerRefV1(
            playerUuid=player_uuid,
            playerPid=player_pid,
            playerName=player_name,
        ),
        discord=DiscordIdentityRefV1(
            discordId=discord_id,
            discordUsername=discord_username,
        ),
        issuedAt=issued_at,
        expiresAt=issued_at + timeout_ms,
        code=code,
        expectedLinkedAt=expected_linked_at,
    )


def build_player_active_badge_changed_command(
    uuid_value: str,
    active_badge: str,
    server: str,
) -> PlayerActiveBadgeChangedCommandV1:
    return PlayerActiveBadgeChangedCommandV1(
        playerUuid=uuid_value,
        activeBadge=active_badge,
        server=server,
    )


def build_player_badge_inventory_changed_command(
    uuid_value: str,
    active_badge: str,
    unlocked_badges: list[str] | tuple[str, ...],
    server: str,
) -> PlayerBadgeInventoryChangedCommandV1:
    return PlayerBadgeInventoryChangedCommandV1(
        playerUuid=uuid_value,
        activeBadge=active_badge,
        unlockedBadges=tuple(unlocked_badges),
        server=server,
    )


def build_chat_discord_ingress_command(
    author_name: str,
    message: str,
    server: str,
) -> ChatDiscordIngressCommandV1:
    return ChatDiscordIngressCommandV1(
        authorName=author_name,
        message=message,
        server=server,
    )


def build_maps_load_command(
    server: str,
    files: list[dict[str, object]],
) -> MapsLoadCommandV1:
    return MapsLoadCommandV1(
        server=server,
        files=tuple(
            MapFileSourceV1.from_payload(
                {
                    "url": item["url"],
                    "fileName": item.get("fileName", item.get("filename", "")),
                    **(
                        {"discordUpload": item["discordUpload"]}
                        if "discordUpload" in item
                        else {}
                    ),
                }
            )
            for item in files
        ),
    )


def build_maps_upload_request(
    server: str, files: list[dict[str, object]]
) -> MapsUploadRequestV1:
    return MapsUploadRequestV1(
        server=server, files=build_maps_load_command(server, files).files
    )


def build_maps_list_request(
    server: str,
) -> MapsListRequestV1:
    return MapsListRequestV1(
        server=server,
    )


def build_maps_remove_request(
    server: str,
    file_name: str,
) -> MapsRemoveRequestV1:
    return MapsRemoveRequestV1(
        server=server,
        fileName=file_name,
    )


def build_season_reschedule_request(
    *,
    server: str,
    ladder: str,
    operation: RatingSeasonRescheduleRequestV1Operation,
    discord_id: str,
    actor_name: str,
    extend_seconds: int | None = None,
    ends_at: str | None = None,
    reason: str | None = None,
    request_id: str | None = None,
) -> RatingSeasonRescheduleRequestV1:
    return RatingSeasonRescheduleRequestV1(
        server=server,
        ladder=ladder,
        operation=operation,
        actor=ActorRefV1(
            actorName=actor_name,
            actorDiscordId=str(discord_id),
            actorType=ActorRefV1ActorType.DISCORD,
        ),
        extendSeconds=extend_seconds,
        endsAt=ends_at,
        reason=reason or None,
        requestId=request_id,
    )


def build_ratings_merge_request(
    *,
    server: str,
    source_uuid: str,
    target_uuid: str,
) -> RatingAccountsMergeRequestV1:
    return RatingAccountsMergeRequestV1(
        server=server,
        sourceUuid=source_uuid,
        targetUuid=target_uuid,
    )


def _discord_actor(discord_id: str, actor_name: str) -> ActorRefV1:
    return ActorRefV1(
        actorName=actor_name,
        actorDiscordId=str(discord_id),
        actorType=ActorRefV1ActorType.DISCORD,
    )


def build_season_prize_add_request(
    *,
    server: str,
    ladder: str,
    prize: SeasonPrizeV1,
    discord_id: str,
    actor_name: str,
    request_id: str | None = None,
) -> RatingSeasonPrizesSetRequestV1:
    return RatingSeasonPrizesSetRequestV1(
        server=server,
        ladder=ladder,
        operation=RatingSeasonPrizesSetRequestV1Operation.ADD,
        prize=prize,
        actor=_discord_actor(discord_id, actor_name),
        requestId=request_id,
    )


def build_season_prize_remove_request(
    *,
    server: str,
    ladder: str,
    place_from: int,
    place_to: int,
    discord_id: str,
    actor_name: str,
    request_id: str | None = None,
) -> RatingSeasonPrizesSetRequestV1:
    return RatingSeasonPrizesSetRequestV1(
        server=server,
        ladder=ladder,
        operation=RatingSeasonPrizesSetRequestV1Operation.REMOVE,
        placeFrom=place_from,
        placeTo=place_to,
        actor=_discord_actor(discord_id, actor_name),
        requestId=request_id,
    )


def build_prize_delivered_request(
    *,
    server: str,
    ladder: str,
    season: int,
    place: int,
    discord_id: str,
    actor_name: str,
    note: str | None = None,
    player_pid: int | None = None,
) -> RatingPrizeGrantUpdateRequestV1:
    return RatingPrizeGrantUpdateRequestV1(
        server=server,
        ladder=ladder,
        season=season,
        place=place,
        playerPid=player_pid,
        status=RatingPrizeGrantUpdateRequestV1Status.DELIVERED,
        actor=_discord_actor(discord_id, actor_name),
        note=note or None,
    )


def build_case_notice_command(
    *, server: str, notice_id: str, player_uuid: str
) -> CasesNoticeCommandV1:
    return CasesNoticeCommandV1(
        server=server,
        noticeId=notice_id,
        playerUuid=player_uuid,
        requestedAt=utc_now_iso8601(),
    )


def build_case_evidence_request(
    *, server: str, player_uuid: str, chat_lines: int = 30, window_seconds: int = 600
) -> CasesEvidenceRequestV1:
    request = CasesEvidenceRequestV1(
        server=server,
        playerUuid=player_uuid,
        chatLines=chat_lines,
        windowSeconds=window_seconds,
    )
    # Generated models currently enforce minimums but omit schema maximums.
    if chat_lines > 30 or window_seconds > 1800:
        raise ValueError("Evidence request exceeds the protocol limits")
    return request


def build_maps_inspect_request(
    *, server: str, url: str, file_name: str, profile: str | None = None
) -> MapsInspectRequestV1:
    return MapsInspectRequestV1(
        server=server,
        file=MapFileSourceV1(url=url, fileName=file_name),
        profile=profile,
    )
