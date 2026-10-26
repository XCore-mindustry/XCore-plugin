from datetime import UTC, datetime, timedelta
from types import SimpleNamespace

from cases_fakes import Database

from xcore_discord_bot.audit_store import AuditStore

NOW = datetime(2026, 1, 1, tzinfo=UTC)


def _audit() -> tuple[AuditStore, Database]:
    db = Database()
    return AuditStore(SimpleNamespace(database=db)), db


async def _append(audit: AuditStore, minutes: int, **overrides) -> str:
    kwargs = {
        "action": "BAN",
        "target_uuid": "uuid-1",
        "target_pid": 1,
        "target_name": "Target",
        "target_ip": None,
        "actor_discord_id": "100",
        "actor_name": "Mod",
        "reason": f"reason {minutes}",
        "occurred_at": NOW + timedelta(minutes=minutes),
        **overrides,
    }
    return await audit.append_moderation_audit(**kwargs)


async def test_player_history_is_newest_first_and_paged() -> None:
    audit, _ = _audit()
    ids = [await _append(audit, minutes) for minutes in range(3)]
    await _append(audit, 9, target_uuid="uuid-2")

    first = await audit.list_audit_for_player(uuid="uuid-1", limit=2, page=0)
    second = await audit.list_audit_for_player(uuid="uuid-1", limit=2, page=1)

    assert [row.audit_id for row in first + second] == ids[::-1]
    assert first[0].reason == "reason 2" and first[0].actor_name == "Mod"
    assert await audit.count_audit_for_player(uuid="uuid-1") == 3
    assert await audit.list_audit_for_player(uuid=" ") == []
    assert await audit.count_audit_for_player(uuid="") == 0


async def test_actor_history_matches_the_discord_id_and_the_name() -> None:
    audit, _ = _audit()
    by_discord = await _append(audit, 0)
    by_name = await _append(audit, 1, actor_discord_id=None, actor_name="Console")
    await _append(audit, 2, actor_discord_id="200")

    rows = await audit.list_audit_for_actor(actor_id="Mod", actor_discord_id="100")
    assert [row.audit_id for row in rows] == [by_discord]
    assert (
        await audit.count_audit_for_actor(actor_id="Mod", actor_discord_id="100") == 1
    )
    # An actor written before Discord ids were recorded matches by name.
    rows = await audit.list_audit_for_actor(actor_id="Console")
    assert [row.audit_id for row in rows] == [by_name]
    assert await audit.list_audit_for_actor(actor_id=" ", actor_discord_id=None) == []
    assert await audit.count_audit_for_actor(actor_id="") == 0


async def test_append_normalizes_the_written_document() -> None:
    audit, db = _audit()
    audit_id = await _append(
        audit,
        0,
        action=" note ",
        reason=" ",
        actor_name="",
        occurred_at=datetime(2026, 1, 1),
        extra={"ban_id": 7},
    )
    (doc,) = db["moderation_audit"].docs
    assert doc["audit_id"] == audit_id
    assert (doc["action"], doc["category"]) == ("NOTE", "NOTE")
    assert doc["reason"] == "Not Specified"
    assert doc["actor"]["name_snapshot"] == "Unknown"
    assert doc["occurred_at"] == NOW
    assert doc["created_at_epoch_ms"] == int(NOW.timestamp() * 1000)
    assert doc["details"]["extra"] == {"ban_id": 7}
