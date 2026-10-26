from __future__ import annotations

from dataclasses import replace
from datetime import timedelta
from unittest.mock import AsyncMock

import pytest
from bot_fakes import make_bot, make_settings
from test_ban_identity import NOW, _ban, _store
from xcore_protocol.generated.maps import MapsUploadResponseV1

from xcore_discord_bot.cases.model import Case, CaseKind, CaseStatus, Origin, Party
from xcore_discord_bot.dto import PlayerRecord
from xcore_discord_bot.moderation_logs import post_ban_log
from xcore_discord_bot.redis_bus import RpcError
from xcore_discord_bot.staff_actions import (
    ActionAlreadyProcessed,
    Actor,
    BadgeGrantUnconfirmed,
    BadgeNotFound,
    BadgeNotGrantable,
    BanNotFound,
    InvalidBanExpiry,
    InvalidDuration,
    InvalidMapUpload,
    MapUploadFailed,
    MapUploadFile,
    MapUploadTimeout,
    MissingPlayerIdentifiers,
    perform_badge_grant,
    perform_ban,
    perform_map_upload,
    perform_mute,
    perform_shorten_ban,
    perform_unban,
)

ACTOR = Actor("123", "Moderator")
PLAYER = PlayerRecord(pid=42, uuid="uuid-42", ip="1.2.3.4", nickname="Target")
FILE = MapUploadFile("https://example/arena", "arena.msav")


class _Channel:
    def __init__(self) -> None:
        self.embeds = []

    async def send(self, *, embed):
        self.embeds.append(embed)


class _Bus:
    def __init__(self) -> None:
        self.claims = []
        self.keys = set()
        self.kicks = []
        self.pardons = []
        self.inventory_events = []
        self.upload_calls = []
        self.upload_error = None
        self.statuses = ["installed"]

    async def claim_idempotency(self, key, ttl_seconds=600):
        self.claims.append((key, ttl_seconds))
        if key in self.keys:
            return False
        self.keys.add(key)
        return True

    async def publish_kick_banned(self, *, uuid_value, ip):
        self.kicks.append((uuid_value, ip))

    async def publish_pardon_player(self, *, uuid_value):
        self.pardons.append(uuid_value)

    async def publish_player_badge_inventory_changed(self, **kwargs):
        self.inventory_events.append(kwargs)

    async def rpc_upload_maps(self, *, server, files, timeout_ms):
        self.upload_calls.append((server, files, timeout_ms))
        if self.upload_error is not None:
            raise self.upload_error
        return MapsUploadResponseV1.from_payload(
            {
                "messageType": "maps.upload.response",
                "messageVersion": 1,
                "server": server,
                "results": [
                    {"fileName": file["filename"], "status": status}
                    for file, status in zip(files, self.statuses, strict=True)
                ],
            }
        )


def _action_bot():
    store, db = _store()
    store.now_utc = lambda: NOW
    db["players"].docs = [
        {
            "_id": 1,
            "pid": PLAYER.pid,
            "uuid": PLAYER.uuid,
            "nickname": PLAYER.nickname,
            "ip": PLAYER.ip,
            "unlocked_badges": [],
            "active_badge": None,
        }
    ]
    bus = _Bus()
    channel = _Channel()
    bot = make_bot(
        store=store,
        bus=bus,
        settings=make_settings(discord_bans_channel_id=10, discord_mutes_channel_id=20),
    )
    bot.map_cache = {"mini-pvp": (0, [])}
    bot.resolve_channel = AsyncMock(return_value=channel)
    bot.container.cases.get = _case
    return bot, db, bus, channel


async def _case(number):
    # The case an action is taken from; its parties' UUIDs are hidden in the logs.
    return Case(
        number,
        CaseKind.REPORT,
        CaseStatus.OPEN,
        1,
        Origin("game", "submission"),
        Party(player_uuid="uuid-opener"),
        Party(player_uuid=PLAYER.uuid),
        NOW,
        NOW,
    )


def _assert_audit(
    row,
    *,
    action,
    extra,
    reason="griefing",
    expires_at=None,
    duration_ms=None,
    request_id="request-1",
):
    assert row["action"] == action
    assert row["target"] == {
        "uuid": "uuid-42",
        "pid": 42,
        "name_snapshot": "Target",
        "ip_snapshot": "1.2.3.4",
    }
    assert row["actor"]["type"] == "DISCORD_USER"
    assert row["actor"]["id"] == row["actor"]["discord_id"] == "123"
    assert row["actor"]["name_snapshot"] == "Moderator"
    assert row["details"]["extra"] == extra
    assert row["details"]["duration_ms"] == duration_ms
    assert row["details"]["expires_at"] == expires_at
    assert row["reason"] == reason
    assert row["origin"]["request_id"] == request_id
    assert row["occurred_at"] == NOW


async def test_perform_ban_allocates_identity_audits_kicks_and_logs_case() -> None:
    bot, db, bus, channel = _action_bot()
    result = await perform_ban(
        bot,
        player=PLAYER,
        duration=timedelta(days=1),
        reason="griefing",
        actor=ACTOR,
        case_number=57,
        period="1d",
        request_id="request-1",
    )
    assert result.ban.ban_id == 1 and result.ban.case_number == 57
    assert db["bans"].docs[0]["ban_id"] == 1
    assert db["bans"].docs[0]["case_number"] == 57
    assert result.audit_id == db["moderation_audit"].docs[0]["audit_id"]
    _assert_audit(
        db["moderation_audit"].docs[0],
        action="BAN",
        extra={"ban_id": "1", "case": "57"},
        expires_at=NOW + timedelta(days=1),
        duration_ms=86_400_000,
    )
    assert bus.kicks == [("uuid-42", "1.2.3.4")]
    assert bus.claims == [("ban-confirm:uuid-42:1.2.3.4:1d:griefing:case:57", 600)]
    fields = {field.name: field.value for field in channel.embeds[0].fields}
    assert fields["Ban"] == "B-1" and fields["Case"] == "#57"
    assert fields["Admin"] == "Moderator (<@123>)"
    assert result.message.startswith("Banned `Target` until")


async def test_perform_ban_duplicate_is_a_typed_error_without_extra_side_effects() -> (
    None
):
    bot, db, bus, channel = _action_bot()
    kwargs = {
        "player": PLAYER,
        "duration": timedelta(days=1),
        "reason": "griefing",
        "actor": ACTOR,
    }
    await perform_ban(bot, **kwargs)
    with pytest.raises(ActionAlreadyProcessed, match="already processed recently"):
        await perform_ban(bot, **kwargs)
    assert (
        len(db["bans"].docs)
        == len(db["moderation_audit"].docs)
        == len(bus.kicks)
        == len(channel.embeds)
        == 1
    )
    assert db["counters"].docs[0]["seq"] == 1


async def test_perform_ban_reban_gets_a_new_identity() -> None:
    bot, db, _, _ = _action_bot()
    first = await perform_ban(
        bot, player=PLAYER, duration=timedelta(days=1), reason="one", actor=ACTOR
    )
    second = await perform_ban(
        bot, player=PLAYER, duration=timedelta(days=2), reason="two", actor=ACTOR
    )
    assert first.ban.ban_id == 1 and second.ban.ban_id == 2
    assert db["bans"].docs[0]["ban_id"] == 2
    assert "case" not in db["moderation_audit"].docs[0]["details"]["extra"]


async def test_perform_ban_missing_identifiers_is_a_typed_error() -> None:
    bot, db, bus, _ = _action_bot()
    with pytest.raises(MissingPlayerIdentifiers, match="both UUID and IP"):
        await perform_ban(
            bot,
            player=replace(PLAYER, uuid=None, ip=None),
            duration=timedelta(days=1),
            reason="griefing",
            actor=ACTOR,
        )
    assert not db["bans"].docs and not bus.claims


@pytest.mark.parametrize("duration", [timedelta(0), timedelta(seconds=-1)])
@pytest.mark.parametrize("action", ["ban", "mute"])
async def test_perform_sanctions_reject_non_positive_duration(action, duration) -> None:
    bot, db, bus, _ = _action_bot()
    function = perform_ban if action == "ban" else perform_mute
    with pytest.raises(InvalidDuration):
        await function(
            bot, player=PLAYER, duration=duration, reason="griefing", actor=ACTOR
        )
    assert not db["bans"].docs and not db["mutes"].docs and not bus.claims


@pytest.mark.parametrize(
    "player",
    [
        replace(PLAYER, uuid=None),
        replace(PLAYER, ip=None, last_ip="5.6.7.8", uuid="  uuid-42  "),
    ],
)
async def test_perform_ban_accepts_ip_only_and_normalizes_identifiers(player) -> None:
    bot, _, bus, _ = _action_bot()
    result = await perform_ban(
        bot, player=player, duration=timedelta(days=1), reason="griefing", actor=ACTOR
    )
    if player.uuid is None:
        assert result.ban.uuid == "" and bus.kicks == [("", "1.2.3.4")]
    else:
        assert result.ban.uuid == "uuid-42" and bus.kicks == [("uuid-42", "5.6.7.8")]


@pytest.mark.parametrize("legacy", [False, True])
async def test_perform_unban_removes_ban_pardons_and_audits_identity(legacy) -> None:
    bot, db, bus, channel = _action_bot()
    db["bans"].docs = [_ban()] if legacy else [_ban(ban_id=71)]
    ban = await bot.store.find_ban(uuid=PLAYER.uuid, ip=None)
    result = await perform_unban(
        bot, ban, ACTOR, case_number=57, request_id="request-1"
    )
    ban_id = 1 if legacy else 71
    assert result.ban.ban_id == ban_id
    assert not db["bans"].docs and bus.pardons == ["uuid-42"]
    _assert_audit(
        db["moderation_audit"].docs[0],
        action="UNBAN",
        extra={"ban_id": str(ban_id), "case": "57"},
    )
    assert result.message == "Unbanned `Target`"
    assert (
        not channel.embeds
    )  # preserves the command's existing reversal reply without a new log


async def test_perform_unban_ip_only_does_not_publish_pardon() -> None:
    bot, db, bus, _ = _action_bot()
    db["bans"].docs = [_ban(uuid="", ban_id=71)]
    ban = await bot.store.find_ban(uuid="", ip="1.2.3.4")
    await perform_unban(bot, ban, ACTOR)
    assert not bus.pardons and not db["bans"].docs
    assert db["moderation_audit"].docs[0]["details"]["extra"] == {"ban_id": "71"}


async def test_perform_unban_missing_identifiers_is_a_typed_error() -> None:
    bot, db, _, _ = _action_bot()
    db["bans"].docs = [_ban(ban_id=71)]
    ban = await bot.store.find_ban_by_id(71)
    with pytest.raises(MissingPlayerIdentifiers):
        await perform_unban(bot, replace(ban, uuid=None, ip=None), ACTOR)
    assert not db["moderation_audit"].docs


@pytest.mark.parametrize("legacy", [False, True])
async def test_perform_unban_missing_ban_is_a_typed_error(legacy) -> None:
    bot, db, bus, _ = _action_bot()
    db["bans"].docs = [_ban()] if legacy else [_ban(ban_id=71)]
    ban = await bot.store.find_ban(uuid=PLAYER.uuid, ip=None)
    db["bans"].docs.clear()
    with pytest.raises(BanNotFound):
        await perform_unban(bot, ban, ACTOR)
    assert not db["moderation_audit"].docs and not bus.pardons


async def test_perform_unban_stale_identity_cannot_remove_replacement_ban() -> None:
    bot, db, bus, _ = _action_bot()
    db["bans"].docs = [_ban(ban_id=71)]
    ban = await bot.store.find_ban_by_id(71)
    db["bans"].docs[0]["ban_id"] = 72
    with pytest.raises(BanNotFound):
        await perform_unban(bot, ban, ACTOR, 57)
    assert db["bans"].docs[0]["ban_id"] == 72
    assert not db["moderation_audit"].docs and not bus.pardons


async def test_perform_shorten_ban_keeps_identity_audits_note_and_logs() -> None:
    bot, db, bus, channel = _action_bot()
    db["bans"].docs = [_ban(ban_id=71, case_number=50)]
    earlier = NOW + timedelta(days=2)
    result = await perform_shorten_ban(
        bot, 71, earlier, ACTOR, 57, request_id="request-1"
    )
    assert result.ban.ban_id == 71 and result.ban.case_number == 50
    assert result.ban.expire_date == earlier
    _assert_audit(
        db["moderation_audit"].docs[0],
        action="NOTE",
        extra={"ban_id": "71", "case": "57", "kind": "ban_shortened"},
        expires_at=earlier,
        duration_ms=172_800_000,
    )
    assert channel.embeds[0].title == "Ban Shortened"
    fields = {field.name: field.value for field in channel.embeds[0].fields}
    assert fields["Ban"] == "B-71" and fields["Case"] == "#57"
    assert not bus.kicks and not bus.pardons
    assert not db["counters"].docs


async def test_perform_shorten_ban_missing_identity_is_a_typed_error() -> None:
    bot, db, _, channel = _action_bot()
    with pytest.raises(BanNotFound):
        await perform_shorten_ban(bot, 71, NOW, ACTOR)
    assert not db["moderation_audit"].docs and not channel.embeds


@pytest.mark.parametrize(
    "expiry",
    [
        NOW,
        NOW - timedelta(seconds=1),
        NOW + timedelta(days=7),
        NOW + timedelta(days=8),
        "invalid",
    ],
)
async def test_perform_shorten_ban_invalid_expiry_is_a_typed_error(expiry) -> None:
    bot, db, _, channel = _action_bot()
    db["bans"].docs = [_ban(ban_id=71)]
    with pytest.raises(InvalidBanExpiry):
        await perform_shorten_ban(bot, 71, expiry, ACTOR)
    assert db["bans"].docs == [_ban(ban_id=71)]
    assert not db["moderation_audit"].docs and not channel.embeds


async def test_perform_shorten_normalizes_naive_expiry_to_utc() -> None:
    bot, db, _, _ = _action_bot()
    db["bans"].docs = [_ban(ban_id=71)]
    expiry = NOW + timedelta(hours=1)
    result = await perform_shorten_ban(bot, 71, expiry.replace(tzinfo=None), ACTOR)
    assert result.ban.expire_date == expiry


async def test_perform_shorten_cannot_lengthen_after_a_concurrent_shortening() -> None:
    bot, db, _, _ = _action_bot()
    db["bans"].docs = [_ban(ban_id=71)]
    original_update = bot.store.update_ban_expiry

    async def shorter_wins(**kwargs):
        db["bans"].docs[0]["expire_date"] = NOW + timedelta(days=1)
        return await original_update(**kwargs)

    bot.store.update_ban_expiry = shorter_wins
    with pytest.raises(InvalidBanExpiry):
        await perform_shorten_ban(bot, 71, NOW + timedelta(days=2), ACTOR)
    assert db["bans"].docs[0]["expire_date"] == NOW + timedelta(days=1)
    assert not db["moderation_audit"].docs


async def test_perform_mute_persists_case_and_audits_without_a_view() -> None:
    bot, db, bus, channel = _action_bot()
    result = await perform_mute(
        bot, PLAYER, timedelta(minutes=10), "spam", ACTOR, 57, request_id="request-1"
    )
    assert result.uuid == "uuid-42" and result.expires_at == NOW + timedelta(minutes=10)
    assert db["mutes"].docs[0]["case_number"] == 57
    assert result.audit_id == db["moderation_audit"].docs[0]["audit_id"]
    _assert_audit(
        db["moderation_audit"].docs[0],
        action="MUTE",
        extra={"case": "57"},
        reason="spam",
        expires_at=result.expires_at,
        duration_ms=600_000,
    )
    assert result.message.startswith("Muted `Target` until")
    assert len(channel.embeds) == 1 and channel.embeds[0].title == "Mute Issued"
    assert not bus.kicks and not db["counters"].docs


async def test_perform_mute_missing_uuid_is_a_typed_error() -> None:
    bot, db, _, _ = _action_bot()
    with pytest.raises(MissingPlayerIdentifiers):
        await perform_mute(
            bot, replace(PLAYER, uuid=" "), timedelta(minutes=10), "spam", ACTOR
        )
    assert not db["mutes"].docs and not db["moderation_audit"].docs


async def test_perform_badge_grant_is_idempotent_and_publishes_inventory() -> None:
    bot, db, bus, _ = _action_bot()
    first = await perform_badge_grant(
        bot, PLAYER, " MAP-MAKER ", ACTOR, 57, request_id="request-1"
    )
    second = await perform_badge_grant(bot, PLAYER, "map-maker", ACTOR, 57)
    assert first.changed and first.badge_id == "map-maker" and first.audit_id
    assert not second.changed and second.audit_id
    assert first.message == "Granted badge `map-maker` to `Target`"
    assert second.message == "Player already has badge `map-maker`"
    assert bus.inventory_events == [
        {
            "uuid_value": "uuid-42",
            "active_badge": None,
            "unlocked_badges": ("map-maker",),
        }
    ]
    assert len(db["moderation_audit"].docs) == 2
    assert db["moderation_audit"].docs[1]["details"]["extra"]["changed"] == "false"
    _assert_audit(
        db["moderation_audit"].docs[0],
        action="NOTE",
        extra={
            "case": "57",
            "kind": "badge_granted",
            "badge_id": "map-maker",
            "changed": "true",
        },
        reason="Granted badge map-maker",
    )


async def test_perform_badge_grant_command_context_has_no_new_audit() -> None:
    bot, db, bus, _ = _action_bot()
    result = await perform_badge_grant(bot, PLAYER, "translator", ACTOR)
    assert result.changed and result.audit_id is None
    assert len(bus.inventory_events) == 1 and not db["moderation_audit"].docs


async def test_perform_badge_grant_skips_event_if_updated_player_disappears() -> None:
    bot, _, bus, _ = _action_bot()
    bot.store.find_player_by_uuid = AsyncMock(return_value=None)
    result = await perform_badge_grant(bot, PLAYER, "translator", ACTOR)
    assert result.changed and not bus.inventory_events


@pytest.mark.parametrize(
    "badge_id,error",
    [("unknown", BadgeNotFound), (" ", BadgeNotFound), ("admin", BadgeNotGrantable)],
)
async def test_perform_badge_grant_badge_errors_are_typed(badge_id, error) -> None:
    bot, db, bus, _ = _action_bot()
    with pytest.raises(error):
        await perform_badge_grant(bot, PLAYER, badge_id, ACTOR)
    assert db["players"].docs[0]["unlocked_badges"] == []
    assert not bus.inventory_events and not db["moderation_audit"].docs


async def test_perform_badge_grant_missing_uuid_is_a_typed_error() -> None:
    bot, db, _, _ = _action_bot()
    with pytest.raises(MissingPlayerIdentifiers):
        await perform_badge_grant(bot, replace(PLAYER, uuid=None), "map-maker", ACTOR)
    assert db["players"].docs[0]["unlocked_badges"] == []


async def test_perform_map_upload_attribution_and_all_file_statuses(
    monkeypatch,
) -> None:
    monkeypatch.setattr(
        "xcore_discord_bot.staff_actions.time.time", lambda: NOW.timestamp()
    )
    bot, db, bus, _ = _action_bot()
    bus.statuses = [
        "installed",
        "download_failed",
        "invalid_map",
        "superseded",
        "metadata_failed",
        "rejected",
    ]
    uploader = Actor("123", "Reviewer", "reviewer-handle")
    creator = Actor("456", "Mapper", "mapper-handle")
    override = Actor("789", "Other Mapper")
    files = [
        MapUploadFile(FILE.url, f"{index}.msav", override if index == 1 else None)
        for index in range(6)
    ]
    result = await perform_map_upload(bot, "mini-pvp", files, uploader, creator)
    assert [entry.status.value for entry in result.results] == bus.statuses
    assert result.audit_id is None and not db["moderation_audit"].docs
    assert [entry.fileName for entry in result.results] == [
        file.filename for file in files
    ]
    assert len(bus.upload_calls) == 1
    server, payload, timeout = bus.upload_calls[0]
    assert server == "mini-pvp" and timeout == 120_000
    assert payload[0]["discordUpload"] == {
        "uploadedBy": {"discordId": "123", "discordUsername": "Reviewer"},
        "uploadedByHandle": "reviewer-handle",
        "uploadedAt": int(NOW.timestamp() * 1000),
        "creator": {"discordId": "456", "discordUsername": "Mapper"},
        "creatorHandle": "mapper-handle",
    }
    assert payload[1]["discordUpload"]["creator"]["discordId"] == "789"
    assert "creatorHandle" not in payload[1]["discordUpload"]
    assert "mini-pvp" not in bot.map_cache


async def test_perform_map_upload_filters_non_msav_and_omits_unknown_creator() -> None:
    bot, _, bus, _ = _action_bot()
    await perform_map_upload(
        bot, "mini-pvp", [MapUploadFile(FILE.url, "skip.txt"), FILE], ACTOR
    )
    assert [file["filename"] for file in bus.upload_calls[0][1]] == ["arena.msav"]
    assert "creator" not in bus.upload_calls[0][1][0]["discordUpload"]


@pytest.mark.parametrize(
    "files,uploader",
    [
        ([], ACTOR),
        ([MapUploadFile(FILE.url, "skip.txt")], ACTOR),
        ([FILE, FILE], ACTOR),
        ([MapUploadFile(FILE.url, "../arena.msav")], ACTOR),
        ([MapUploadFile(None, "arena.msav")], ACTOR),
        ([FILE], Actor(None, "Unknown")),
    ],
)
async def test_perform_map_upload_invalid_inputs_are_typed_and_do_not_send(
    files, uploader
) -> None:
    bot, _, bus, _ = _action_bot()
    with pytest.raises(InvalidMapUpload):
        await perform_map_upload(bot, "mini-pvp", files, uploader)
    assert not bus.upload_calls and "mini-pvp" in bot.map_cache


@pytest.mark.parametrize(
    "error,expected",
    [
        (TimeoutError(), MapUploadTimeout),
        (ConnectionError("offline"), MapUploadFailed),
        (RpcError("maps.upload", "ERROR", "failed"), MapUploadFailed),
        (TypeError("invalid response"), MapUploadFailed),
        (ValueError("invalid response"), MapUploadFailed),
    ],
)
async def test_perform_map_upload_errors_are_typed_without_retrying(
    error, expected
) -> None:
    bot, _, bus, _ = _action_bot()
    bus.upload_error = error
    with pytest.raises(expected) as caught:
        await perform_map_upload(bot, "mini-pvp", [FILE], ACTOR)
    assert caught.value.__cause__ is error
    assert len(bus.upload_calls) == 1 and "mini-pvp" not in bot.map_cache


async def test_post_ban_log_lazily_assigns_game_ban_identity_and_case() -> None:
    bot, db, _, channel = _action_bot()
    db["bans"].docs = [_ban(case_number=57)]
    kwargs = {
        "pid": 42,
        "name": "Target",
        "admin_name": "Moderator",
        "admin_discord_id": None,
        "reason": "griefing",
        "expire": NOW + timedelta(days=7),
    }
    await post_ban_log(bot, **kwargs)
    await post_ban_log(bot, **kwargs)
    assert db["bans"].docs[0]["ban_id"] == 1
    assert db["counters"].docs[0]["seq"] == 1
    for embed in channel.embeds:
        fields = {field.name: field.value for field in embed.fields}
        assert fields["Ban"] == "B-1" and fields["Case"] == "#57"


async def test_post_ban_log_accepts_supplied_game_id_without_a_lookup() -> None:
    bot, db, _, channel = _action_bot()
    bot.store.find_player_by_pid = AsyncMock(
        side_effect=AssertionError("unnecessary lookup")
    )
    await post_ban_log(
        bot,
        pid=42,
        name="Target",
        admin_name="Game Admin",
        admin_discord_id=None,
        reason="griefing",
        expire=NOW,
        ban_id=99,
    )
    fields = {field.name: field.value for field in channel.embeds[0].fields}
    assert fields["Ban"] == "B-99" and "Case" not in fields
    assert not db["counters"].docs


@pytest.mark.parametrize("action", ["unban", "shorten"])
async def test_ban_actions_preserve_an_unknown_player_pid_in_the_audit(action) -> None:
    bot, db, _, _ = _action_bot()
    db["bans"].docs = [_ban(ban_id=71, pid=None)]
    if action == "unban":
        ban = await bot.store.find_ban_by_id(71)
        await perform_unban(bot, ban, ACTOR)
    else:
        await perform_shorten_ban(bot, 71, NOW + timedelta(hours=1), ACTOR)
    assert db["moderation_audit"].docs[0]["target"]["pid"] is None
    assert db["moderation_audit"].docs[0]["target"]["name_snapshot"] == "Target"


@pytest.mark.parametrize("failure", ["pardon", "audit"])
async def test_unban_completion_is_written_before_pardon_and_audit_failure_stops_it(
    failure,
):
    bot, db, bus, _ = _action_bot()
    db["bans"].docs = [_ban(ban_id=71)]
    ban = await bot.store.find_ban_by_id(71)
    order = []
    delete, append = bot.store.delete_ban, bot.store.append_moderation_audit

    async def deleted(**kwargs):
        order.append("delete")
        return await delete(**kwargs)

    async def audited(**kwargs):
        order.append("audit")
        assert not db["bans"].docs
        if failure == "audit":
            raise RuntimeError("audit unavailable")
        return await append(**kwargs)

    async def pardoned(**kwargs):
        order.append("pardon")
        assert db["moderation_audit"].docs
        raise RuntimeError("pardon unavailable")

    bot.store.delete_ban, bot.store.append_moderation_audit = deleted, audited
    bot.bus.publish_pardon_player = pardoned
    with pytest.raises(RuntimeError):
        await perform_unban(bot, ban, ACTOR, 57, case_action_token="attempt")
    assert order == (
        ["delete", "audit", "pardon"] if failure == "pardon" else ["delete", "audit"]
    )
    assert not db["bans"].docs and not bus.pardons
    if failure == "pardon":
        assert (
            db["moderation_audit"].docs[0]["details"]["extra"]["case_action_token"]
            == "attempt"
        )
    else:
        assert not db["moderation_audit"].docs


@pytest.mark.parametrize("failure", ["lookup", "inventory", "audit"])
async def test_badge_case_audit_precedes_fallible_followups(failure):
    bot, db, bus, _ = _action_bot()
    if failure == "audit":
        bot.store.append_moderation_audit = AsyncMock(
            side_effect=RuntimeError("audit unavailable")
        )
    if failure == "lookup":
        bot.store.find_player_by_uuid = AsyncMock(
            side_effect=RuntimeError("lookup unavailable")
        )
    if failure == "inventory":
        bot.bus.publish_player_badge_inventory_changed = AsyncMock(
            side_effect=RuntimeError("inventory unavailable")
        )
    with pytest.raises(RuntimeError):
        await perform_badge_grant(
            bot, PLAYER, "map-maker", ACTOR, 57, case_action_token="attempt"
        )
    assert db["players"].docs[0]["unlocked_badges"] == ["map-maker"]
    assert bool(db["moderation_audit"].docs) == (failure != "audit")
    assert not bus.inventory_events
    if failure == "audit":
        # No verification lookup after the acknowledged mutation if audit persistence fails.
        assert bot.store.append_moderation_audit.await_count == 1


@pytest.mark.parametrize("fresh", [None, PLAYER])
async def test_unchanged_case_badge_requires_fresh_membership_not_input_snapshot(fresh):
    bot, db, bus, _ = _action_bot()
    bot.store.grant_badge = AsyncMock(return_value=False)
    bot.store.find_player_by_uuid = AsyncMock(return_value=fresh)
    snapshot = replace(PLAYER, unlocked_badges=("map-maker",))
    with pytest.raises(BadgeGrantUnconfirmed):
        await perform_badge_grant(bot, snapshot, "map-maker", ACTOR, 57)
    bot.store.find_player_by_uuid.assert_awaited_once_with("uuid-42")
    assert not db["moderation_audit"].docs and not bus.inventory_events


async def test_legacy_unchanged_badge_does_not_add_verification_or_audit():
    bot, db, _, _ = _action_bot()
    bot.store.grant_badge = AsyncMock(return_value=False)
    bot.store.find_player_by_uuid = AsyncMock(
        side_effect=AssertionError("legacy lookup")
    )
    result = await perform_badge_grant(
        bot, PLAYER, "map-maker", ACTOR, case_action_token="ignored"
    )
    assert not result.changed and result.audit_id is None
    assert not db["moderation_audit"].docs


@pytest.mark.parametrize(
    "status",
    [
        "installed",
        "download_failed",
        "invalid_map",
        "superseded",
        "metadata_failed",
        "rejected",
    ],
)
async def test_case_map_audit_has_string_metadata_and_only_installed_completion(status):
    bot, db, bus, _ = _action_bot()
    bus.statuses = [status]
    result = await perform_map_upload(
        bot,
        "mini-pvp",
        [FILE],
        ACTOR,
        Actor("456", "Creator"),
        case_number=57,
        case_action_token="attempt",
        request_id="request-1",
        reviewed_sha256="reviewed-hash",
    )
    row = db["moderation_audit"].docs[0]
    assert row["action"] == "NOTE"
    assert row["target"] == {
        "uuid": "",
        "pid": None,
        "name_snapshot": "arena.msav",
        "ip_snapshot": None,
    }
    assert row["actor"]["discord_id"] == "123"
    assert row["origin"]["request_id"] == "request-1"
    assert row["details"]["extra"] == {
        "case": "57",
        "case_action_token": "attempt",
        "server": "mini-pvp",
        "file_name": "arena.msav",
        "status": status,
        "kind": "map_uploaded" if status == "installed" else "map_upload_result",
        "reviewed_sha256": "reviewed-hash",
    }
    assert all(isinstance(value, str) for value in row["details"]["extra"].values())
    assert FILE.url not in str(row)
    assert result.audit_id == (row["audit_id"] if status == "installed" else None)


async def test_case_map_rejects_multiple_eligible_files_before_rpc():
    bot, db, bus, _ = _action_bot()
    with pytest.raises(InvalidMapUpload, match="exactly one"):
        await perform_map_upload(
            bot,
            "mini-pvp",
            [FILE, replace(FILE, filename="second.msav")],
            ACTOR,
            case_number=57,
        )
    assert not bus.upload_calls and not db["moderation_audit"].docs
    assert "mini-pvp" in bot.map_cache


@pytest.mark.parametrize(
    "server,names",
    [
        ("wrong", ["arena.msav", "second.msav"]),
        ("mini-pvp", ["arena.msav", "arena.msav"]),
        ("mini-pvp", ["arena.msav"]),
        ("mini-pvp", ["second.msav", "arena.msav"]),
        ("mini-pvp", ["arena.msav", "other.msav"]),
    ],
)
async def test_map_response_must_match_server_and_exact_ordered_files(server, names):
    bot, db, _, _ = _action_bot()
    response = MapsUploadResponseV1.from_payload(
        {
            "messageType": "maps.upload.response",
            "messageVersion": 1,
            "server": server,
            "results": [{"fileName": name, "status": "installed"} for name in names],
        }
    )
    bot.bus.rpc_upload_maps = AsyncMock(return_value=response)
    with pytest.raises(MapUploadFailed):
        await perform_map_upload(
            bot, "mini-pvp", [FILE, replace(FILE, filename="second.msav")], ACTOR
        )
    assert not db["moderation_audit"].docs


async def test_map_audit_error_after_installed_is_not_translated_or_retried():
    bot, db, bus, _ = _action_bot()
    bot.store.append_moderation_audit = AsyncMock(
        side_effect=ValueError("audit persistence failed")
    )
    with pytest.raises(ValueError, match="audit persistence failed"):
        await perform_map_upload(bot, "mini-pvp", [FILE], ACTOR, case_number=57)
    assert len(bus.upload_calls) == 1 and not db["moderation_audit"].docs


async def test_case_map_filters_ineligible_files_and_preserves_result_detail():
    bot, db, _, _ = _action_bot()
    bot.bus.rpc_upload_maps = AsyncMock(
        return_value=MapsUploadResponseV1.from_payload(
            {
                "messageType": "maps.upload.response",
                "messageVersion": 1,
                "server": "mini-pvp",
                "results": [
                    {
                        "fileName": "arena.msav",
                        "status": "metadata_failed",
                        "detail": "attribution failed",
                    }
                ],
            }
        )
    )
    result = await perform_map_upload(
        bot,
        "mini-pvp",
        [replace(FILE, filename="skip.txt"), FILE],
        ACTOR,
        case_number=57,
    )
    assert result.audit_id is None
    assert (
        db["moderation_audit"].docs[0]["details"]["extra"]["detail"]
        == "attribution failed"
    )
    assert len(bot.bus.rpc_upload_maps.call_args.kwargs["files"]) == 1


@pytest.mark.parametrize("response", [None, {}, {"server": "mini-pvp", "results": []}])
async def test_case_map_malformed_response_writes_no_completion(response):
    bot, db, _, _ = _action_bot()
    bot.bus.rpc_upload_maps = AsyncMock(return_value=response)
    with pytest.raises(MapUploadFailed):
        await perform_map_upload(
            bot, "mini-pvp", [FILE], ACTOR, case_number=57, case_action_token="attempt"
        )
    assert not db["moderation_audit"].docs
    bot.bus.rpc_upload_maps.assert_awaited_once()
