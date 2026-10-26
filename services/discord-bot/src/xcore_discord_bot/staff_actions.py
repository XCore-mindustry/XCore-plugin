from __future__ import annotations

import time
from collections.abc import Awaitable, Callable, Sequence
from dataclasses import dataclass, replace
from datetime import UTC, datetime, timedelta
from typing import TYPE_CHECKING

import discord
from xcore_protocol.generated.maps import MapsUploadResponseV1
from xcore_protocol.generated.shared import MapUploadResultV1

from .badges import get_badge
from .bus.transport import RpcError
from .dto import Actor, BanRecord, PlayerRecord, record_identifiers
from .moderation_logs import post_ban_log, post_mute_log
from .protocol_outbound import build_maps_upload_request
from .retry import TRANSIENT_EXCEPTIONS

if TYPE_CHECKING:
    from .bot import XCoreDiscordBot


class StaffActionError(ValueError):
    pass


class MissingPlayerIdentifiers(StaffActionError):
    pass


class ActionAlreadyProcessed(StaffActionError):
    pass


class InvalidDuration(StaffActionError):
    pass


class BanNotFound(StaffActionError):
    pass


class InvalidBanExpiry(StaffActionError):
    pass


class BadgeNotFound(StaffActionError):
    pass


class BadgeNotGrantable(StaffActionError):
    pass


class BadgeGrantUnconfirmed(StaffActionError):
    pass


class InvalidMapUpload(StaffActionError):
    pass


class MapUploadTimeout(StaffActionError):
    pass


class MapUploadFailed(StaffActionError):
    pass


@dataclass(frozen=True)
class BanResult:
    ban: BanRecord
    audit_id: str
    message: str


@dataclass(frozen=True)
class MuteResult:
    uuid: str
    expires_at: datetime
    audit_id: str
    message: str


@dataclass(frozen=True)
class BadgeGrantResult:
    badge_id: str
    changed: bool
    message: str
    audit_id: str | None = None


@dataclass(frozen=True)
class MapUploadFile:
    url: str
    filename: str
    creator: Actor | None = None


@dataclass(frozen=True)
class MapUploadResult:
    response: MapsUploadResponseV1
    audit_id: str | None = None

    @property
    def results(self) -> tuple[MapUploadResultV1, ...]:
        return self.response.results


def _subject_name(player: PlayerRecord | BanRecord) -> str:
    return player.name if isinstance(player, BanRecord) else player.nickname


def _require_duration(duration: timedelta) -> None:
    if duration <= timedelta(0):
        raise InvalidDuration("Duration must be greater than zero.")


def _audit_extra(
    *,
    ban_id: int | None = None,
    case_number: int | None = None,
    case_action_token: str | None = None,
) -> dict[str, str]:
    extra = {}
    if ban_id is not None:
        extra["ban_id"] = str(ban_id)
    if case_number is not None:
        extra["case"] = str(case_number)
        if case_action_token is not None:
            extra["case_action_token"] = str(case_action_token)
    return extra


async def _append_audit(
    bot: XCoreDiscordBot,
    *,
    action: str,
    player: PlayerRecord | BanRecord,
    reason: str,
    actor: Actor,
    duration: timedelta | None = None,
    expires_at: datetime | None = None,
    request_id: str | None = None,
    extra: dict[str, str] | None = None,
) -> str:
    uuid_value, ip_value = record_identifiers(player)
    return await bot.store.append_moderation_audit(
        action=action,
        target_uuid=uuid_value or "",
        target_pid=player.pid,
        target_name=_subject_name(player),
        target_ip=ip_value,
        actor_discord_id=actor.discord_id,
        actor_name=actor.name,
        reason=str(reason or "Not Specified").strip() or "Not Specified",
        occurred_at=bot.store.now_utc(),
        duration_ms=int(duration.total_seconds() * 1000)
        if duration is not None
        else None,
        expires_at=expires_at,
        related_audit_id=None,
        supersedes_audit_id=None,
        request_id=request_id,
        **({"extra": extra} if extra else {}),
    )


async def perform_ban(
    bot: XCoreDiscordBot,
    *,
    player: PlayerRecord,
    duration: timedelta,
    reason: str,
    actor: Actor,
    case_number: int | None = None,
    period: str | None = None,
    request_id: str | None = None,
    case_action_token: str | None = None,
) -> BanResult:
    uuid_value, ip_value = record_identifiers(player)
    if uuid_value is None and ip_value is None:
        raise MissingPlayerIdentifiers(
            "Cannot ban player: both UUID and IP are missing in player data."
        )
    _require_duration(duration)
    key_uuid = uuid_value or "none"
    key_ip = ip_value or "none"
    claim_key = f"ban-confirm:{key_uuid}:{key_ip}:{period or duration}:{reason}"
    if case_number is not None:
        claim_key += f":case:{case_number}"
    if not await bot.bus.claim_idempotency(claim_key, ttl_seconds=600):
        raise ActionAlreadyProcessed("This ban was already processed recently.")

    expire = bot.store.now_utc() + duration
    ban_id = await bot.store.next_ban_id()
    ban = BanRecord(
        uuid=uuid_value or "",
        ip=ip_value,
        pid=player.pid,
        name=player.nickname,
        admin_name=actor.name,
        admin_discord_id=actor.discord_id,
        reason=reason,
        expire_date=expire,
        ban_id=ban_id,
        case_number=case_number,
    )
    await bot.store.upsert_ban(
        uuid=uuid_value or "",
        ip=ip_value,
        pid=player.pid,
        name=ban.name,
        admin_name=actor.name,
        admin_discord_id=actor.discord_id,
        reason=reason,
        expire_date=expire,
        ban_id=ban_id,
        **({"case_number": case_number} if case_number is not None else {}),
    )
    audit_id = await _append_audit(
        bot,
        action="BAN",
        player=player,
        reason=reason,
        actor=actor,
        duration=duration,
        expires_at=expire,
        request_id=request_id,
        extra=_audit_extra(
            ban_id=ban_id, case_number=case_number, case_action_token=case_action_token
        ),
    )
    await bot.bus.publish_kick_banned(uuid_value=uuid_value or "", ip=ip_value)
    await post_ban_log(
        bot,
        pid=player.pid,
        name=ban.name,
        admin_name=actor.name,
        admin_discord_id=actor.discord_id,
        reason=reason,
        expire=expire,
        ban_id=ban_id,
        case_number=case_number,
        **(
            {"privacy_secrets": (uuid_value or "", ip_value or "")}
            if case_number is not None
            else {}
        ),
    )
    return BanResult(
        ban,
        audit_id,
        (
            f"Banned `{ban.name}` until {discord.utils.format_dt(expire, style='f')} "
            f"({discord.utils.format_dt(expire, style='R')})"
        ),
    )


# Awaited with the ban right before it is deleted or its expiry changes; raising stops
# the write. Lets a caller such as an appeal confirm the ban is still the one it acts on.
BanCheck = Callable[[BanRecord], Awaitable[None]]


async def perform_unban(
    bot: XCoreDiscordBot,
    ban: BanRecord,
    actor: Actor,
    case_number: int | None = None,
    *,
    player: PlayerRecord | None = None,
    request_id: str | None = None,
    case_action_token: str | None = None,
    check: BanCheck | None = None,
) -> BanResult:
    uuid_value = str(ban.uuid or "").strip() or None
    ip_value = str(ban.ip or "").strip() or None
    if uuid_value is None and ip_value is None:
        raise MissingPlayerIdentifiers(
            "Cannot unban player: both UUID and IP are missing in player data."
        )
    if ban.ban_id is None:
        assigned = await bot.store.ensure_ban_id(uuid=uuid_value or "", ip=ip_value)
        if assigned is None:
            raise BanNotFound("No active ban found")
        ban = assigned
    if check is not None:
        await check(ban)
    if (
        await bot.store.delete_ban(
            uuid=uuid_value or "", ip=ip_value, ban_id=ban.ban_id
        )
        <= 0
    ):
        raise BanNotFound("No active ban found")
    subject = player or ban
    audit_id = await _append_audit(
        bot,
        action="UNBAN",
        player=subject,
        reason=ban.reason,
        actor=actor,
        request_id=request_id,
        extra=_audit_extra(
            ban_id=ban.ban_id,
            case_number=case_number,
            case_action_token=case_action_token,
        ),
    )
    if uuid_value is not None:
        await bot.bus.publish_pardon_player(uuid_value=uuid_value)
    return BanResult(ban, audit_id, f"Unbanned `{_subject_name(subject)}`")


async def perform_shorten_ban(
    bot: XCoreDiscordBot,
    ban_id: int,
    new_expiry: datetime,
    actor: Actor,
    case_number: int | None = None,
    *,
    request_id: str | None = None,
    case_action_token: str | None = None,
    check: BanCheck | None = None,
) -> BanResult:
    ban = await bot.store.find_ban_by_id(ban_id)
    if ban is None:
        raise BanNotFound("No active ban found")
    if not isinstance(new_expiry, datetime):
        raise InvalidBanExpiry("The new expiry must be a datetime.")
    expire = (
        new_expiry.replace(tzinfo=UTC)
        if new_expiry.tzinfo is None
        else new_expiry.astimezone(UTC)
    )
    now = bot.store.now_utc()
    now = now.replace(tzinfo=UTC) if now.tzinfo is None else now.astimezone(UTC)
    if expire <= now:
        raise InvalidBanExpiry("The new expiry must be in the future.")
    if check is not None:
        await check(ban)
    if not await bot.store.update_ban_expiry(ban_id=ban_id, new_expiry=expire):
        raise InvalidBanExpiry(
            "The new expiry must be earlier than the current ban expiry."
        )
    updated_ban = replace(ban, expire_date=expire)
    extra = _audit_extra(
        ban_id=ban_id, case_number=case_number, case_action_token=case_action_token
    )
    extra["kind"] = "ban_shortened"
    audit_id = await _append_audit(
        bot,
        action="NOTE",
        player=ban,
        reason=ban.reason,
        actor=actor,
        duration=expire - now,
        expires_at=expire,
        request_id=request_id,
        extra=extra,
    )
    await post_ban_log(
        bot,
        pid=ban.pid,
        name=ban.name,
        admin_name=actor.name,
        admin_discord_id=actor.discord_id,
        reason=ban.reason,
        expire=expire,
        ban_id=ban_id,
        case_number=case_number,
        title="Ban Shortened",
        privacy_secrets=(ban.uuid or "", ban.ip or ""),
    )
    return BanResult(
        updated_ban,
        audit_id,
        (
            f"Shortened ban B-{ban_id} until {discord.utils.format_dt(expire, style='f')} "
            f"({discord.utils.format_dt(expire, style='R')})"
        ),
    )


async def perform_mute(
    bot: XCoreDiscordBot,
    player: PlayerRecord,
    duration: timedelta,
    reason: str,
    actor: Actor,
    case_number: int | None = None,
    *,
    request_id: str | None = None,
    case_action_token: str | None = None,
) -> MuteResult:
    uuid_value, _ = record_identifiers(player)
    if uuid_value is None:
        raise MissingPlayerIdentifiers(
            "Cannot mute player: UUID is missing in player data."
        )
    _require_duration(duration)
    expire = bot.store.now_utc() + duration
    player_name = player.nickname
    await bot.store.upsert_mute(
        uuid=uuid_value,
        pid=player.pid,
        name=player_name,
        admin_name=actor.name,
        admin_discord_id=actor.discord_id,
        reason=reason,
        expire_date=expire,
        **({"case_number": case_number} if case_number is not None else {}),
    )
    audit_id = await _append_audit(
        bot,
        action="MUTE",
        player=player,
        reason=reason,
        actor=actor,
        duration=duration,
        expires_at=expire,
        request_id=request_id,
        extra=_audit_extra(
            case_number=case_number, case_action_token=case_action_token
        ),
    )
    await post_mute_log(
        bot,
        pid=player.pid,
        name=player_name,
        admin_name=actor.name,
        admin_discord_id=actor.discord_id,
        reason=reason,
        expire=expire,
        **({"case_number": case_number} if case_number is not None else {}),
        **(
            {"privacy_secrets": (uuid_value, player.ip or "")}
            if case_number is not None
            else {}
        ),
    )
    return MuteResult(
        uuid_value,
        expire,
        audit_id,
        (
            f"Muted `{player_name}` until {discord.utils.format_dt(expire, style='f')} "
            f"({discord.utils.format_dt(expire, style='R')})"
        ),
    )


async def perform_badge_grant(
    bot: XCoreDiscordBot,
    player: PlayerRecord,
    badge_id: str,
    actor: Actor,
    case_number: int | None = None,
    *,
    request_id: str | None = None,
    case_action_token: str | None = None,
) -> BadgeGrantResult:
    uuid_value, _ = record_identifiers(player)
    if uuid_value is None:
        raise MissingPlayerIdentifiers(
            "Cannot change badge: UUID is missing in player data."
        )
    badge = get_badge(badge_id)
    if badge is None:
        raise BadgeNotFound(f"Badge `{badge_id.strip() or badge_id}` was not found.")
    if badge.system or not badge.grantable:
        raise BadgeNotGrantable(
            f"Badge `{badge.id}` cannot be granted or revoked manually."
        )
    changed = await bot.store.grant_badge(uuid=uuid_value, badge_id=badge.id)
    if not changed and case_number is not None:
        confirmed_player = await bot.store.find_player_by_uuid(uuid_value)
        if confirmed_player is None or badge.id not in confirmed_player.unlocked_badges:
            raise BadgeGrantUnconfirmed(
                "The player's badge grant could not be confirmed."
            )
    audit_id = None
    if case_number is not None:
        extra = _audit_extra(
            case_number=case_number, case_action_token=case_action_token
        )
        extra.update(
            kind="badge_granted", badge_id=badge.id, changed=str(changed).lower()
        )
        audit_id = await _append_audit(
            bot,
            action="NOTE",
            player=player,
            reason=f"Granted badge {badge.id}",
            actor=actor,
            request_id=request_id,
            extra=extra,
        )
    if changed:
        updated_player = await bot.store.find_player_by_uuid(uuid_value)
        if updated_player is not None:
            await bot.bus.publish_player_badge_inventory_changed(
                uuid_value=uuid_value,
                active_badge=updated_player.active_badge,
                unlocked_badges=updated_player.unlocked_badges,
            )
    message = (
        f"Granted badge `{badge.id}` to `{player.nickname}`"
        if changed
        else f"Player already has badge `{badge.id}`"
    )
    return BadgeGrantResult(badge.id, changed, message, audit_id)


def prepare_map_upload(
    server: str,
    files: Sequence[MapUploadFile],
    uploader: Actor,
    creator: Actor | None = None,
) -> list[dict[str, object]]:
    def identity(actor: Actor) -> dict[str, str]:
        return {"discordId": str(actor.discord_id or ""), "discordUsername": actor.name}

    payload: list[dict[str, object]] = []
    uploaded_at = int(time.time() * 1000)
    for file in files:
        if not file.filename.lower().endswith(".msav"):
            continue
        selected_creator = file.creator or creator
        upload: dict[str, object] = {
            "uploadedBy": identity(uploader),
            "uploadedAt": uploaded_at,
        }
        if uploader.handle:
            upload["uploadedByHandle"] = uploader.handle
        if selected_creator is not None:
            upload["creator"] = identity(selected_creator)
            if selected_creator.handle:
                upload["creatorHandle"] = selected_creator.handle
        payload.append(
            {"url": file.url, "filename": file.filename, "discordUpload": upload}
        )
    if not payload:
        raise InvalidMapUpload("No valid .msav files attached.")
    if len({item["filename"] for item in payload}) != len(payload):
        raise InvalidMapUpload("Each attachment must have a different filename.")
    try:
        build_maps_upload_request(server, payload)
    except (ValueError, TypeError) as error:
        raise InvalidMapUpload(
            "The upload details are invalid; nothing was sent to the server."
        ) from error
    return payload


async def perform_map_upload(
    bot: XCoreDiscordBot,
    server: str,
    files: Sequence[MapUploadFile],
    uploader: Actor,
    creator: Actor | None = None,
    *,
    case_number: int | None = None,
    case_action_token: str | None = None,
    request_id: str | None = None,
    reviewed_sha256: str | None = None,
) -> MapUploadResult:
    payload = prepare_map_upload(server, files, uploader, creator)
    if case_number is not None and len(payload) != 1:
        raise InvalidMapUpload("A map case must upload exactly one .msav file.")
    bot.map_cache.pop(server, None)
    try:
        response = await bot.container.rpc.upload_maps(
            server=server, files=payload, timeout_ms=120_000
        )
        if not isinstance(response, MapsUploadResponseV1):
            raise TypeError("Invalid map upload response")
        if response.server != server or [r.fileName for r in response.results] != [
            item["filename"] for item in payload
        ]:
            raise ValueError("Map upload response does not match the request")
    except TimeoutError as error:
        raise MapUploadTimeout(
            "The server has not confirmed the result yet. Some files may already be installed; check /map list before retrying."
        ) from error
    except (RpcError, *TRANSIENT_EXCEPTIONS, ValueError, TypeError) as error:
        raise MapUploadFailed(
            "Could not confirm the upload result. Check /map list before retrying."
        ) from error
    bot.map_cache.pop(server, None)
    audit_id = None
    if case_number is not None:
        result = response.results[0]
        status = result.status.value
        extra = _audit_extra(
            case_number=case_number, case_action_token=case_action_token
        )
        extra.update(
            kind="map_uploaded" if status == "installed" else "map_upload_result",
            server=server,
            file_name=result.fileName,
            status=status,
        )
        if result.detail is not None:
            extra["detail"] = str(result.detail)
        if reviewed_sha256 is not None:
            extra["reviewed_sha256"] = str(reviewed_sha256)
        written_id = await _append_map_upload_audit(
            bot, uploader, extra, request_id=request_id
        )
        if status == "installed":
            audit_id = written_id
    return MapUploadResult(response, audit_id)


async def _append_map_upload_audit(
    bot: XCoreDiscordBot,
    uploader: Actor,
    extra: dict[str, str],
    *,
    request_id: str | None,
) -> str:
    return await bot.store.append_moderation_audit(
        action="NOTE",
        target_uuid="",
        target_pid=None,
        target_name=extra["file_name"],
        target_ip=None,
        actor_discord_id=uploader.discord_id,
        actor_name=uploader.name,
        reason=f"Map upload {extra['file_name']}: {extra['status']}",
        occurred_at=bot.store.now_utc(),
        duration_ms=None,
        expires_at=None,
        related_audit_id=None,
        supersedes_audit_id=None,
        request_id=request_id,
        extra=extra,
    )
