from __future__ import annotations

from collections.abc import Mapping

from .dto import BanRecord, MuteRecord, PlayerRecord


def _normalized_optional_str(value: object) -> str | None:
    if value is None:
        return None
    normalized = str(value).strip()
    return normalized or None


def _int_or_default(value: object, *, default: int = 0) -> int:
    if isinstance(value, bool):
        return int(value)
    if isinstance(value, int):
        return value
    if isinstance(value, str):
        normalized = value.strip()
        if normalized.isdigit() or (
            normalized.startswith("-") and normalized[1:].isdigit()
        ):
            return int(normalized)
    return default


def _bool_or_default(value: object, *, default: bool) -> bool:
    if isinstance(value, bool):
        return value
    if isinstance(value, int):
        return value != 0
    if isinstance(value, str):
        normalized = value.strip().lower()
        if normalized in {"true", "1", "yes", "on"}:
            return True
        if normalized in {"false", "0", "no", "off"}:
            return False
    return default


def _normalized_str_tuple(value: object) -> tuple[str, ...]:
    if not isinstance(value, (list, tuple, set, frozenset)):
        return ()

    result: list[str] = []
    seen: set[str] = set()
    for item in value:
        normalized = _normalized_optional_str(item)
        if normalized is None or normalized in seen:
            continue
        seen.add(normalized)
        result.append(normalized)
    return tuple(result)


def player_record_from_doc(doc: Mapping[str, object]) -> PlayerRecord:
    nickname = _normalized_optional_str(doc.get("nickname")) or "Unknown"
    return PlayerRecord(
        pid=_int_or_default(doc.get("pid"), default=-1),
        nickname=nickname,
        uuid=_normalized_optional_str(doc.get("uuid")),
        ip=_normalized_optional_str(doc.get("ip")),
        last_ip=_normalized_optional_str(doc.get("last_ip")),
        custom_nickname=_normalized_optional_str(doc.get("custom_nickname")),
        description=_normalized_optional_str(doc.get("description")),
        language=_normalized_optional_str(doc.get("local_language")),
        translator_language=_normalized_optional_str(doc.get("translator_language")),
        total_play_time=_int_or_default(doc.get("total_play_time"), default=0),
        pvp_rating=_int_or_default(doc.get("pvp_rating"), default=0),
        hexed_rank=_int_or_default(doc.get("hexed_rank"), default=0),
        hexed_points=_int_or_default(doc.get("hexed_points"), default=0),
        leaderboard=_bool_or_default(doc.get("leaderboard"), default=True),
        unlocked_badges=_normalized_str_tuple(doc.get("unlocked_badges")),
        active_badge=_normalized_optional_str(doc.get("active_badge")),
        blocked_private_uuids=_normalized_str_tuple(doc.get("blocked_private_uuids")),
        is_admin=bool(doc.get("is_admin", False)),
        admin_source=_normalized_optional_str(doc.get("admin_source")),
        discord_id=_normalized_optional_str(doc.get("discord_id")),
        discord_username=_normalized_optional_str(doc.get("discord_username")),
        discord_linked_at=(
            _int_or_default(doc.get("discord_linked_at"), default=0)
            if doc.get("discord_linked_at") is not None
            else None
        ),
        created_at=doc.get("created_at"),
        updated_at=doc.get("updated_at"),
    )


def ban_record_from_doc(doc: Mapping[str, object]) -> BanRecord:
    return BanRecord(
        uuid=_normalized_optional_str(doc.get("uuid")),
        ip=_normalized_optional_str(doc.get("ip")),
        pid=(
            _int_or_default(doc.get("pid"), default=-1)
            if doc.get("pid") is not None
            else None
        ),
        name=_normalized_optional_str(doc.get("name")) or "Unknown",
        admin_name=_normalized_optional_str(doc.get("admin_name")) or "Unknown",
        admin_discord_id=_normalized_optional_str(doc.get("admin_discord_id")),
        reason=_normalized_optional_str(doc.get("reason")) or "Not Specified",
        expire_date=doc.get("expire_date"),
    )


def mute_record_from_doc(doc: Mapping[str, object]) -> MuteRecord:
    return MuteRecord(
        uuid=_normalized_optional_str(doc.get("uuid")),
        name=_normalized_optional_str(doc.get("name")) or "Unknown",
        admin_name=_normalized_optional_str(doc.get("admin_name")) or "Unknown",
        admin_discord_id=_normalized_optional_str(doc.get("admin_discord_id")),
        reason=_normalized_optional_str(doc.get("reason")) or "Not Specified",
        expire_date=doc.get("expire_date"),
    )
