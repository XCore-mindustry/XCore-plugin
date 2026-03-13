from __future__ import annotations

from types import SimpleNamespace

import pytest

from xcore_discord_bot.bot import XCoreDiscordBot
from xcore_discord_bot.dto import PlayerRecord


@pytest.mark.asyncio
async def test_reconcile_discord_admin_access_revokes_missing_role_and_applies_present_role() -> (
    None
):
    bot = object.__new__(XCoreDiscordBot)
    bot.__dict__["_settings"] = SimpleNamespace(admin_reconcile_interval_seconds=300)

    linked_admin = PlayerRecord(
        pid=7,
        uuid="uuid-7",
        nickname="TargetA",
        discord_id="111",
        discord_username="a",
        is_admin=True,
        admin_source="DISCORD_ROLE",
    )
    linked_non_admin = PlayerRecord(
        pid=8,
        uuid="uuid-8",
        nickname="TargetB",
        discord_id="222",
        discord_username="b",
        is_admin=False,
        admin_source="NONE",
    )

    async def get_discord_admin_member_ids() -> set[str]:
        return {"222"}

    async def find_discord_admin_players() -> list[PlayerRecord]:
        return [linked_admin]

    async def find_players_by_discord_id(discord_id: str) -> list[PlayerRecord]:
        if discord_id == "222":
            return [linked_non_admin]
        return []

    set_calls: list[tuple[str, bool, str]] = []
    published: list[dict[str, object]] = []

    async def set_admin_access(
        *, uuid: str, is_admin: bool, admin_source: str
    ) -> tuple[bool, bool]:
        set_calls.append((uuid, is_admin, admin_source))
        return True, True

    async def publish_discord_admin_access_changed(**payload):
        published.append(payload)

    bot.__dict__["get_discord_admin_member_ids"] = get_discord_admin_member_ids
    bot.__dict__["find_discord_admin_players"] = find_discord_admin_players
    bot.__dict__["find_players_by_discord_id"] = find_players_by_discord_id
    bot.__dict__["set_admin_access"] = set_admin_access
    bot.__dict__["publish_discord_admin_access_changed"] = (
        publish_discord_admin_access_changed
    )

    result = await XCoreDiscordBot.reconcile_discord_admin_access(bot)

    assert result == {
        "applied": 1,
        "revoked": 1,
        "discord_admins": 1,
        "skipped_empty_snapshot": 0,
        "applied_players": [{"discord_id": "222", "pid": 8, "nickname": "TargetB"}],
        "revoked_players": [{"discord_id": "111", "pid": 7, "nickname": "TargetA"}],
        "skipped": [],
    }
    assert set_calls == [
        ("uuid-7", False, "NONE"),
        ("uuid-8", True, "DISCORD_ROLE"),
    ]
    assert published[0]["admin"] is False
    assert published[1]["admin"] is True


@pytest.mark.asyncio
async def test_reconcile_discord_admin_access_skips_revoke_on_empty_snapshot_with_existing_admins() -> (
    None
):
    bot = object.__new__(XCoreDiscordBot)
    bot.__dict__["_settings"] = SimpleNamespace(admin_reconcile_interval_seconds=300)

    linked_admin = PlayerRecord(
        pid=7,
        uuid="uuid-7",
        nickname="TargetA",
        discord_id="111",
        discord_username="a",
        is_admin=True,
        admin_source="DISCORD_ROLE",
    )

    async def get_discord_admin_member_ids() -> set[str]:
        return set()

    async def find_discord_admin_players() -> list[PlayerRecord]:
        return [linked_admin]

    set_calls: list[tuple[str, bool, str]] = []
    published: list[dict[str, object]] = []

    async def set_admin_access(
        *, uuid: str, is_admin: bool, admin_source: str
    ) -> tuple[bool, bool]:
        set_calls.append((uuid, is_admin, admin_source))
        return True, True

    async def publish_discord_admin_access_changed(**payload):
        published.append(payload)

    bot.__dict__["get_discord_admin_member_ids"] = get_discord_admin_member_ids
    bot.__dict__["find_discord_admin_players"] = find_discord_admin_players
    bot.__dict__["set_admin_access"] = set_admin_access
    bot.__dict__["publish_discord_admin_access_changed"] = (
        publish_discord_admin_access_changed
    )

    result = await XCoreDiscordBot.reconcile_discord_admin_access(bot)

    assert result == {
        "applied": 0,
        "revoked": 0,
        "discord_admins": 0,
        "skipped_empty_snapshot": 1,
        "applied_players": [],
        "revoked_players": [],
        "skipped": [],
    }
    assert set_calls == []
    assert published == []


@pytest.mark.asyncio
async def test_reconcile_discord_admin_access_applies_to_all_linked_accounts() -> None:
    bot = object.__new__(XCoreDiscordBot)
    bot.__dict__["_settings"] = SimpleNamespace(admin_reconcile_interval_seconds=300)

    first = PlayerRecord(
        pid=8, uuid="uuid-8", nickname="TargetB", discord_id="222", discord_username="b"
    )
    second = PlayerRecord(
        pid=9, uuid="uuid-9", nickname="TargetC", discord_id="222", discord_username="b"
    )

    async def get_discord_admin_member_ids() -> set[str]:
        return {"222"}

    async def find_discord_admin_players() -> list[PlayerRecord]:
        return []

    async def find_players_by_discord_id(discord_id: str) -> list[PlayerRecord]:
        assert discord_id == "222"
        return [first, second]

    set_calls: list[tuple[str, bool, str]] = []
    published: list[dict[str, object]] = []

    async def set_admin_access(
        *, uuid: str, is_admin: bool, admin_source: str
    ) -> tuple[bool, bool]:
        set_calls.append((uuid, is_admin, admin_source))
        return True, True

    async def publish_discord_admin_access_changed(**payload):
        published.append(payload)

    bot.__dict__["get_discord_admin_member_ids"] = get_discord_admin_member_ids
    bot.__dict__["find_discord_admin_players"] = find_discord_admin_players
    bot.__dict__["find_players_by_discord_id"] = find_players_by_discord_id
    bot.__dict__["set_admin_access"] = set_admin_access
    bot.__dict__["publish_discord_admin_access_changed"] = (
        publish_discord_admin_access_changed
    )

    result = await XCoreDiscordBot.reconcile_discord_admin_access(bot)

    assert result == {
        "applied": 2,
        "revoked": 0,
        "discord_admins": 1,
        "skipped_empty_snapshot": 0,
        "applied_players": [
            {"discord_id": "222", "pid": 8, "nickname": "TargetB"},
            {"discord_id": "222", "pid": 9, "nickname": "TargetC"},
        ],
        "revoked_players": [],
        "skipped": [],
    }
    assert set_calls == [
        ("uuid-8", True, "DISCORD_ROLE"),
        ("uuid-9", True, "DISCORD_ROLE"),
    ]
    assert len(published) == 2


@pytest.mark.asyncio
async def test_reconcile_discord_admin_access_reports_skipped_unlinked_and_missing_uuid() -> (
    None
):
    bot = object.__new__(XCoreDiscordBot)
    bot.__dict__["_settings"] = SimpleNamespace(admin_reconcile_interval_seconds=300)

    missing_uuid = PlayerRecord(
        pid=8,
        uuid=None,
        nickname="TargetB",
        discord_id="222",
        discord_username="b",
    )

    async def get_discord_admin_member_ids() -> set[str]:
        return {"222", "333"}

    async def find_discord_admin_players() -> list[PlayerRecord]:
        return []

    async def find_players_by_discord_id(discord_id: str) -> list[PlayerRecord]:
        if discord_id == "222":
            return [missing_uuid]
        if discord_id == "333":
            return []
        return []

    set_calls: list[tuple[str, bool, str]] = []
    published: list[dict[str, object]] = []

    async def set_admin_access(
        *, uuid: str, is_admin: bool, admin_source: str
    ) -> tuple[bool, bool]:
        set_calls.append((uuid, is_admin, admin_source))
        return True, True

    async def publish_discord_admin_access_changed(**payload):
        published.append(payload)

    bot.__dict__["get_discord_admin_member_ids"] = get_discord_admin_member_ids
    bot.__dict__["find_discord_admin_players"] = find_discord_admin_players
    bot.__dict__["find_players_by_discord_id"] = find_players_by_discord_id
    bot.__dict__["set_admin_access"] = set_admin_access
    bot.__dict__["publish_discord_admin_access_changed"] = (
        publish_discord_admin_access_changed
    )

    result = await XCoreDiscordBot.reconcile_discord_admin_access(bot)

    assert result == {
        "applied": 0,
        "revoked": 0,
        "discord_admins": 2,
        "skipped_empty_snapshot": 0,
        "applied_players": [],
        "revoked_players": [],
        "skipped": [
            {
                "discord_id": "222",
                "player": "TargetB",
                "reason": "linked account is missing UUID",
            },
            {
                "discord_id": "333",
                "player": "-",
                "reason": "no linked Mindustry accounts",
            },
        ],
    }
    assert set_calls == []
    assert published == []
