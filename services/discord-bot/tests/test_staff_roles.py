from __future__ import annotations

from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock

import discord
import pytest
from aiohttp import ClientConnectionError
from pydantic import ValidationError
from xcore_protocol.generated.discord import (
    DiscordLinkStatusChangedV1,
    DiscordLinkStatusChangedV1Action,
)
from xcore_protocol.generated.security import (
    SecurityStaffResetPasswordResponseV1,
    SecurityStaffSyncResponseV1,
)
from xcore_protocol.generated.shared import DiscordIdentityRefV1, PlayerRefV1

from xcore_discord_bot.bot import XCoreDiscordBot
from xcore_discord_bot.container import ServiceContainer
from xcore_discord_bot.daemons.staff_sync_daemon import StaffSyncDaemon
from xcore_discord_bot.dto import PlayerRecord
from xcore_discord_bot.mongo_store import MongoStore
from xcore_discord_bot.redis_bus import RedisBus, RpcFailed
from xcore_discord_bot.rpc.mindustry_rpc import MindustryRpcClient
from xcore_discord_bot.services.player_service import PlayerService
from xcore_discord_bot.settings import Settings


@pytest.fixture
def settings(tmp_path):
    path = tmp_path / "permissions.toml"
    path.write_text("""schemaVersion = 1
[discord]
guildId = "123"
[[discord.bindings]]
roleId = "10"
role = "moderator"
[[discord.bindings]]
roleId = "20"
role = "admin"
""")
    return Settings(
        discord_token="token",
        discord_admin_role_id=99,
        discord_private_channel_id=1,
        discord_guild_id=123,
        permissions_mode="roles",
        permissions_config_path=str(path),
        permissions_rpc_server="mini-pvp",
    )


@pytest.fixture
def setup(settings):
    guild = MagicMock()
    guild.fetch_roles = AsyncMock(
        return_value=[SimpleNamespace(id=10), SimpleNamespace(id=20)]
    )
    guild.fetch_member = AsyncMock(return_value=SimpleNamespace(_roles=[10, 10, 999]))

    async def members(*, limit):
        assert limit is None
        yield SimpleNamespace(id=456, _roles=[10, 10, 999])

    guild.fetch_members = MagicMock(side_effect=members)
    bot = MagicMock()
    bot.get_guild.return_value = guild
    store = MagicMock()
    store.find_players_by_discord_id = AsyncMock(
        return_value=[
            PlayerRecord(pid=1, uuid="uuid-1", nickname="player", discord_id="456")
        ]
    )
    rpc = MagicMock()
    rpc.sync_staff = AsyncMock()
    daemon = StaffSyncDaemon(bot, store, rpc, settings)
    daemon.MIN_REQUEST_INTERVAL_SECONDS = 0
    return daemon, guild, store, rpc


async def test_successful_sync_filters_and_deduplicates(setup):
    daemon, guild, _, rpc = setup
    assert await daemon.sync_player("uuid-1", "456")
    guild.fetch_member.assert_awaited_once_with(456)
    rpc.sync_staff.assert_awaited_once_with(
        server="mini-pvp",
        player_uuid="uuid-1",
        discord_id="456",
        role_ids=("10",),
        timeout_ms=5000,
    )


@pytest.mark.parametrize(
    "failure", ["api", "missing-role", "forbidden", "network", "rate-limit"]
)
async def test_discord_failure_sends_nothing(setup, failure):
    daemon, guild, _, rpc = setup
    if failure == "api":
        guild.fetch_member.side_effect = discord.HTTPException(
            SimpleNamespace(status=503, reason="unavailable"), "unavailable"
        )
    elif failure == "missing-role":
        guild.fetch_roles.return_value = [SimpleNamespace(id=10)]
    elif failure == "network":
        guild.fetch_member.side_effect = ClientConnectionError("disconnected")
    elif failure == "rate-limit":
        guild.fetch_member.side_effect = discord.RateLimited(1)
    else:
        guild.fetch_member.side_effect = discord.Forbidden(
            SimpleNamespace(status=403, reason="forbidden"), "forbidden"
        )
    assert not await daemon.sync_player("uuid-1", "456")
    rpc.sync_staff.assert_not_awaited()


@pytest.mark.parametrize(
    "error",
    [TimeoutError(), RpcFailed("security.staff.sync.request", "UNAVAILABLE", "legacy")],
)
async def test_retry_reuses_operation_id_and_new_attempt_changes_it(error):
    bus = MagicMock()
    requests = []

    async def sync(request, **kwargs):
        requests.append(request)
        if len(requests) == 1:
            raise error
        return SecurityStaffSyncResponseV1(request.server, request.operationId, 1, True)

    bus.rpc_staff_sync = AsyncMock(side_effect=sync)
    rpc = MindustryRpcClient(bus)
    kwargs = {
        "server": "mini-pvp",
        "player_uuid": "uuid-1",
        "discord_id": "456",
        "role_ids": ("10", "10"),
        "timeout_ms": 100,
    }
    await rpc.sync_staff(**kwargs)
    await rpc.sync_staff(**kwargs)
    assert requests[0] is requests[1]
    assert requests[0].operationId != requests[2].operationId
    assert requests[0].complete is True
    assert requests[0].roleIds == ("10",)


def test_guild_mismatch_at_startup(settings):
    with pytest.raises(ValidationError, match="guildId differs"):
        Settings(**{**settings.model_dump(), "discord_guild_id": 321})


def test_roles_requires_config_and_target(settings):
    for field in ("permissions_rpc_server", "permissions_config_path"):
        with pytest.raises(ValidationError):
            Settings(**{**settings.model_dump(), field: ""})


@pytest.mark.parametrize("action", list(DiscordLinkStatusChangedV1Action))
async def test_confirmed_link_and_unlink_sync_old_uuid(setup, action):
    daemon, guild, _, rpc = setup
    event = DiscordLinkStatusChangedV1(
        player=PlayerRefV1("old-uuid", "player"),
        discord=DiscordIdentityRefV1("456"),
        action=action,
        server="mini-pvp",
        occurredAt="2026-10-06T12:00:00Z",
    )
    await daemon.on_link_status(event)
    assert rpc.sync_staff.await_args.kwargs["player_uuid"] == "old-uuid"
    if action == DiscordLinkStatusChangedV1Action.UNLINKED:
        guild.fetch_member.assert_not_awaited()
        assert rpc.sync_staff.await_args.kwargs["role_ids"] == ()
    else:
        guild.fetch_member.assert_awaited_once()
        assert rpc.sync_staff.await_args.kwargs["role_ids"] == ("10",)


async def test_member_events_fetch_and_leave_revokes(setup, settings):
    daemon, guild, _, rpc = setup
    bot = object.__new__(XCoreDiscordBot)
    bot._settings = settings
    bot.container = SimpleNamespace(staff_sync_daemon=daemon)
    before = SimpleNamespace(roles=[], guild=SimpleNamespace(id=123), id=456)
    after = SimpleNamespace(roles=[SimpleNamespace(id=10)], guild=before.guild, id=456)
    await bot.on_member_update(before, after)
    assert rpc.sync_staff.await_count == 1
    await bot.on_member_update(after, after)
    assert rpc.sync_staff.await_count == 1
    guild.fetch_member.reset_mock()
    await bot.on_raw_member_remove(
        SimpleNamespace(guild_id=123, user=SimpleNamespace(id=456))
    )
    guild.fetch_member.assert_not_awaited()
    assert rpc.sync_staff.await_args.kwargs["role_ids"] == ()


async def test_scheduled_check_every_linked_account_rate_limited(setup, monkeypatch):
    daemon, _, store, rpc = setup
    store.find_linked_players = AsyncMock(
        return_value=[
            PlayerRecord(pid=i, uuid=f"uuid-{i}", nickname="p", discord_id="456")
            for i in (1, 2, 3)
        ]
    )
    sleeps = AsyncMock()
    monkeypatch.setattr(
        "xcore_discord_bot.daemons.staff_sync_daemon.asyncio.sleep", sleeps
    )
    monkeypatch.setattr(
        "xcore_discord_bot.daemons.staff_sync_daemon.time.monotonic", lambda: 0
    )
    daemon.MIN_REQUEST_INTERVAL_SECONDS = 0.25
    result = await daemon.reconcile()
    assert result["synced"] == 3
    assert result["skipped_count"] == 0
    assert rpc.sync_staff.await_count == 3
    assert [call.args for call in sleeps.await_args_list if call.args != (0,)] == [
        (0.25,),
        (0.25,),
    ]
    assert daemon.CHECK_INTERVAL_SECONDS == 600


@pytest.mark.parametrize("mode", ["legacy", "roles"])
async def test_password_reset_both_modes(settings, mode):
    settings = settings.model_copy(update={"permissions_mode": mode})
    store = MagicMock()
    store.reset_password = AsyncMock(return_value=True)
    bus = MagicMock()
    bus.publish_player_password_reset = AsyncMock()
    rpc = MagicMock()
    rpc.reset_staff_password = AsyncMock(return_value=SimpleNamespace(changed=True))
    service = PlayerService(store, bus, settings=settings, rpc=rpc)
    assert await service.reset_password(uuid="uuid-1")
    if mode == "legacy":
        store.reset_password.assert_awaited_once_with(uuid="uuid-1")
        bus.publish_player_password_reset.assert_awaited_once()
        rpc.reset_staff_password.assert_not_awaited()
    else:
        store.reset_password.assert_not_awaited()
        bus.publish_player_password_reset.assert_not_awaited()
        rpc.reset_staff_password.assert_awaited_once_with(
            server="mini-pvp",
            player_uuid="uuid-1",
            timeout_ms=5000,
        )


async def test_password_reset_retry_reuses_request():
    bus = MagicMock()
    requests = []

    async def reset(request, **kwargs):
        requests.append(request)
        if len(requests) == 1:
            raise TimeoutError()
        return SecurityStaffResetPasswordResponseV1(
            request.server, request.operationId, True
        )

    bus.rpc_staff_reset_password = AsyncMock(side_effect=reset)
    await MindustryRpcClient(bus).reset_staff_password(
        server="mini-pvp", player_uuid="uuid-1", timeout_ms=100
    )
    assert requests[0] is requests[1]


async def test_roles_direct_mongo_writes_forbidden(settings):
    store = MongoStore(settings)
    with pytest.raises(RuntimeError, match="Direct admin writes"):
        await store.set_admin_access(
            uuid="uuid", is_admin=True, admin_source="DISCORD_ROLE"
        )
    with pytest.raises(RuntimeError, match="Direct password writes"):
        await store.reset_password(uuid="uuid")


def test_legacy_container_does_not_load_roles_config(settings):
    legacy = settings.model_copy(
        update={"permissions_mode": "legacy", "permissions_config_path": "/missing"}
    )
    assert ServiceContainer.create(legacy, MagicMock()).staff_sync_daemon is None


async def test_generated_rpc_wire_payload_and_response(settings):
    import json

    bus = RedisBus(settings)

    async def respond(**kwargs):
        payload = kwargs["payload"]
        assert kwargs["server"] == "mini-pvp"
        assert payload["complete"] is True
        assert payload["roleIds"] == ["10"]
        assert kwargs["rpc_type"] == "security.staff.sync.request"
        response = SecurityStaffSyncResponseV1(
            payload["server"], payload["operationId"], 5, True
        )
        return {"payload_json": json.dumps(response.to_payload())}

    bus._rpc_request = AsyncMock(side_effect=respond)
    response = await MindustryRpcClient(bus).sync_staff(
        server="mini-pvp",
        player_uuid="uuid-1",
        discord_id="456",
        role_ids=("10",),
        timeout_ms=100,
    )
    assert response.revision == 5


async def test_schedule_loop_starts_after_ready_and_continues(setup, monkeypatch):
    daemon, _, _, _ = setup
    daemon._bot.wait_until_ready = AsyncMock()
    daemon._bot.is_closed.side_effect = [False, False, True]
    daemon.reconcile = AsyncMock(
        side_effect=[RuntimeError("db unavailable"), {"synced": 0}]
    )
    sleep = AsyncMock()
    monkeypatch.setattr(
        "xcore_discord_bot.daemons.staff_sync_daemon.asyncio.sleep", sleep
    )
    await daemon._run_loop()
    daemon._bot.wait_until_ready.assert_awaited_once()
    assert [call.args[0] for call in sleep.await_args_list] == pytest.approx(
        [600, 600], abs=0.1
    )
    assert daemon.reconcile.await_count == 2


async def test_roles_command_link_does_not_write_admin(settings):
    from datetime import UTC, datetime

    from xcore_discord_bot.handlers_linking import cmd_link

    bot = MagicMock()
    bot.roles_mode = True
    bot._claim_mutation = AsyncMock(return_value=True)
    bot.find_discord_link_code = AsyncMock(return_value={"playerUuid": "uuid-1"})
    bot.now_utc = AsyncMock(return_value=datetime.now(UTC))
    bot.find_player_by_uuid = AsyncMock(
        return_value=PlayerRecord(pid=1, uuid="uuid-1", nickname="p")
    )
    bot.publish_discord_link_confirm = AsyncMock()
    bot.set_admin_access = AsyncMock()
    bot.get_discord_admin_member_ids = AsyncMock(return_value={"456"})
    interaction = SimpleNamespace(
        user=SimpleNamespace(id=456, display_name="user"),
        response=SimpleNamespace(send_message=AsyncMock()),
    )
    await cmd_link(bot, interaction, "CODE")
    bot.publish_discord_link_confirm.assert_awaited_once()
    bot.get_discord_admin_member_ids.assert_not_awaited()
    bot.set_admin_access.assert_not_awaited()


async def test_roles_password_command_uses_rpc_without_legacy_event(settings):
    from xcore_discord_bot.handlers_moderation import cmd_reset_password

    bus = MagicMock()
    bus.publish_player_password_reset = AsyncMock()
    store = MagicMock()
    store.reset_password = AsyncMock()
    rpc = MagicMock()
    rpc.reset_staff_password = AsyncMock(return_value=SimpleNamespace(changed=True))
    bot = object.__new__(XCoreDiscordBot)
    bot._settings = settings
    bot._bus = bus
    bot._store = store
    bot.container = SimpleNamespace(
        players=PlayerService(store, bus, settings=settings, rpc=rpc)
    )
    bot._get_player_or_reply = AsyncMock(
        return_value=PlayerRecord(pid=1, uuid="uuid-1", nickname="p")
    )
    bot._claim_mutation = AsyncMock(return_value=True)
    bot._require_player_uuid = AsyncMock(return_value="uuid-1")
    interaction = SimpleNamespace(
        response=SimpleNamespace(send_message=AsyncMock(), defer=AsyncMock()),
        followup=SimpleNamespace(send=AsyncMock()),
    )
    await cmd_reset_password(bot, interaction, 1)
    rpc.reset_staff_password.assert_awaited_once()
    store.reset_password.assert_not_awaited()
    bus.publish_player_password_reset.assert_not_awaited()


async def test_reset_password_generated_wire_payload(settings):
    import json

    bus = RedisBus(settings)

    async def respond(**kwargs):
        payload = kwargs["payload"]
        assert kwargs["rpc_type"] == "security.staff.reset-password.request"
        assert payload["playerUuid"] == "uuid-1"
        assert payload["server"] == "mini-pvp"
        response = SecurityStaffResetPasswordResponseV1(
            payload["server"], payload["operationId"], True
        )
        return {"payload_json": json.dumps(response.to_payload())}

    bus._rpc_request = AsyncMock(side_effect=respond)
    assert (
        await MindustryRpcClient(bus).reset_staff_password(
            server="mini-pvp", player_uuid="uuid-1", timeout_ms=100
        )
    ).changed


async def test_not_found_is_not_retried():
    bus = MagicMock()
    bus.rpc_staff_sync = AsyncMock(
        side_effect=RpcFailed("security.staff.sync.request", "NOT_FOUND", "not linked")
    )
    with pytest.raises(RpcFailed, match="NOT_FOUND"):
        await MindustryRpcClient(bus).sync_staff(
            server="mini-pvp",
            player_uuid="uuid-1",
            discord_id="456",
            role_ids=("10",),
            timeout_ms=100,
        )
    assert bus.rpc_staff_sync.await_count == 1


def _discord_not_found(code):
    return discord.NotFound(
        SimpleNamespace(status=404, reason="Not Found"),
        {"code": code, "message": "not found"},
    )


async def test_unknown_member_syncs_empty_roles(setup):
    daemon, guild, _, rpc = setup
    guild.fetch_member.side_effect = _discord_not_found(10007)
    assert await daemon.sync_player("uuid-1", "456")
    assert rpc.sync_staff.await_args.kwargs["role_ids"] == ()


@pytest.mark.parametrize("where,code", [("member", 10004), ("guild", 10007)])
async def test_other_not_found_sends_nothing(setup, where, code):
    daemon, guild, _, rpc = setup
    if where == "member":
        guild.fetch_member.side_effect = _discord_not_found(code)
    else:
        guild.fetch_roles.side_effect = _discord_not_found(code)
    assert not await daemon.sync_player("uuid-1", "456")
    rpc.sync_staff.assert_not_awaited()


async def test_member_role_ids_do_not_depend_on_guild_cache(setup):
    daemon, guild, _, rpc = setup
    guild.get_role.return_value = None
    assert await daemon.sync_player("uuid-1", "456")
    assert rpc.sync_staff.await_args.kwargs["role_ids"] == ("10",)
    guild.get_role.assert_not_called()


async def test_scheduled_snapshot_once_and_absent_account_revoked(setup, caplog):
    import logging

    daemon, guild, store, rpc = setup
    store.find_linked_players = AsyncMock(
        return_value=[
            PlayerRecord(
                pid=i, uuid=f"uuid-{i}", nickname="p", discord_id=str(discord_id)
            )
            for i, discord_id in enumerate((456, 789, 456), 1)
        ]
    )
    with caplog.at_level(logging.INFO):
        result = await daemon.reconcile()
    assert result["synced"] == 3
    guild.fetch_roles.assert_awaited_once()
    guild.fetch_members.assert_called_once_with(limit=None)
    guild.fetch_member.assert_not_awaited()
    assert [call.kwargs["role_ids"] for call in rpc.sync_staff.await_args_list] == [
        ("10",),
        (),
        ("10",),
    ]
    assert (
        len(
            [
                record
                for record in caplog.records
                if "Staff sync pass:" in record.message
            ]
        )
        == 1
    )


@pytest.mark.parametrize(
    "failure", ["partial-members", "missing-role", "members-forbidden", "network"]
)
async def test_incomplete_scheduled_snapshot_revokes_nothing(setup, failure):
    daemon, guild, store, rpc = setup
    store.find_linked_players = AsyncMock(
        return_value=[
            PlayerRecord(pid=1, uuid="uuid-1", nickname="p", discord_id="789")
        ]
    )

    async def members(*, limit):
        yield SimpleNamespace(id=456, _roles=[10])
        if failure == "members-forbidden":
            raise discord.Forbidden(
                SimpleNamespace(status=403, reason="forbidden"), "forbidden"
            )
        if failure == "network":
            raise ClientConnectionError("failed on page two")
        raise OSError("failed on page two")

    if failure == "missing-role":
        guild.fetch_roles.return_value = []
    else:
        guild.fetch_members.side_effect = members
    result = await daemon.reconcile()
    assert result["synced"] == 0
    assert result["skipped_count"] == 1
    assert result["reason"]
    rpc.sync_staff.assert_not_awaited()


async def test_pass_does_not_overlap_or_block_event_sync(setup):
    import asyncio

    daemon, guild, store, rpc = setup
    store.find_linked_players = AsyncMock(
        return_value=[
            PlayerRecord(pid=1, uuid="uuid-1", nickname="p", discord_id="456")
        ]
    )
    entered = asyncio.Event()
    release = asyncio.Event()

    async def members(*, limit):
        entered.set()
        await release.wait()
        yield SimpleNamespace(id=456, _roles=[])

    guild.fetch_members.side_effect = members
    task = asyncio.create_task(daemon.reconcile())
    try:
        await entered.wait()
        result = await daemon.reconcile()
        assert "already running" in result["reason"]
        guild.fetch_roles.assert_awaited_once()
        assert await daemon.sync_player("uuid-1", "456")
        release.set()
        result = await task
        # The old snapshot cannot undo the event that ran while it was fetched.
        assert result["synced"] == 0
        assert result["skipped_count"] == 1
        rpc.sync_staff.assert_awaited_once()
    finally:
        task.cancel()
        await asyncio.gather(task, return_exceptions=True)


@pytest.mark.parametrize("legacy", [False, True])
async def test_expected_rpc_errors_summarized_and_legacy_stops_pass(
    setup, caplog, legacy
):
    import logging

    daemon, _, store, rpc = setup
    store.find_linked_players = AsyncMock(
        return_value=[
            PlayerRecord(pid=i, uuid=f"uuid-{i}", nickname="p", discord_id="456")
            for i in range(10)
        ]
    )
    errors = [
        RpcFailed("sync", "NOT_FOUND", "no player"),
        RpcFailed(
            "sync",
            "UNAVAILABLE",
            "Roles are switched off on mini-pvp" if legacy else "storage down",
        ),
    ]
    rpc.sync_staff.side_effect = errors if legacy else errors * 5
    with caplog.at_level(logging.INFO):
        result = await daemon.reconcile()
    assert result["skipped_count"] == 10
    assert rpc.sync_staff.await_count == (2 if legacy else 10)
    assert len([r for r in caplog.records if r.levelname == "WARNING"]) == 2
    assert all(r.exc_info is None for r in caplog.records)
    assert len([r for r in caplog.records if "Staff sync pass:" in r.message]) == 1
    assert bool(result["reason"]) is legacy


@pytest.mark.parametrize("command", ["reset", "add", "remove", "sync"])
@pytest.mark.parametrize(
    "error,expected",
    [
        (RpcFailed("rpc", "NOT_FOUND", "missing"), "NOT_FOUND"),
        (RpcFailed("rpc", "UNAVAILABLE", "storage down"), "UNAVAILABLE"),
        (TimeoutError(), "timed out"),
    ],
)
async def test_roles_deferred_command_reports_rpc_failure(command, error, expected):
    from xcore_discord_bot.handlers_moderation import (
        cmd_add_admin,
        cmd_remove_admin,
        cmd_reset_password,
        cmd_sync_admins,
    )

    player = PlayerRecord(pid=1, uuid="uuid-1", nickname="p", discord_id="456")
    bot = MagicMock()
    bot.roles_mode = True
    bot._get_player_or_reply = AsyncMock(return_value=player)
    bot._claim_mutation = AsyncMock(return_value=True)
    bot._require_player_uuid = AsyncMock(return_value="uuid-1")
    bot.find_players_by_discord_id = AsyncMock(return_value=[player])
    bot.set_discord_admin_role = AsyncMock(return_value=True)
    bot.reset_password = AsyncMock(side_effect=error)
    bot.publish_player_password_reset = AsyncMock()
    daemon = SimpleNamespace(
        sync_member=AsyncMock(side_effect=error), reconcile=AsyncMock(side_effect=error)
    )
    bot._ensure_container.return_value = SimpleNamespace(staff_sync_daemon=daemon)
    interaction = SimpleNamespace(
        user=SimpleNamespace(display_name="admin"),
        response=SimpleNamespace(defer=AsyncMock(), send_message=AsyncMock()),
        followup=SimpleNamespace(send=AsyncMock()),
    )
    functions = {
        "add": cmd_add_admin,
        "remove": cmd_remove_admin,
        "reset": cmd_reset_password,
        "sync": cmd_sync_admins,
    }
    args = () if command == "sync" else (1,)
    await functions[command](bot, interaction, *args)
    interaction.response.defer.assert_awaited_once()
    interaction.followup.send.assert_awaited_once()
    assert expected in interaction.followup.send.await_args.args[0]
    bot.publish_player_password_reset.assert_not_awaited()


@pytest.mark.parametrize("command", ["add", "remove", "sync"])
@pytest.mark.parametrize(
    "synced,skipped,errors", [(1, 0, []), (0, 1, []), (0, 1, ["UNAVAILABLE"])]
)
async def test_roles_commands_report_sync_outcome(command, synced, skipped, errors):
    from xcore_discord_bot.handlers_moderation import (
        cmd_add_admin,
        cmd_remove_admin,
        cmd_sync_admins,
    )

    player = PlayerRecord(pid=1, uuid="uuid-1", nickname="p", discord_id="456")
    bot = MagicMock()
    bot.roles_mode = True
    bot._get_player_or_reply = AsyncMock(return_value=player)
    bot._claim_mutation = AsyncMock(return_value=True)
    bot.find_players_by_discord_id = AsyncMock(return_value=[player])
    bot.set_discord_admin_role = AsyncMock(return_value=True)
    result = {"synced": synced, "skipped_count": skipped, "errors": errors}
    daemon = SimpleNamespace(
        sync_member=AsyncMock(return_value=result),
        reconcile=AsyncMock(return_value=result),
    )
    bot._ensure_container.return_value = SimpleNamespace(staff_sync_daemon=daemon)
    interaction = SimpleNamespace(
        user=SimpleNamespace(display_name="admin"),
        response=SimpleNamespace(defer=AsyncMock(), send_message=AsyncMock()),
        followup=SimpleNamespace(send=AsyncMock()),
    )
    functions = {
        "add": cmd_add_admin,
        "remove": cmd_remove_admin,
        "sync": cmd_sync_admins,
    }
    await functions[command](bot, interaction, *((1,) if command != "sync" else ()))
    message = interaction.followup.send.await_args.args[0]
    assert f"{synced} synced, {skipped} skipped or failed" in message
    if errors:
        assert "UNAVAILABLE" in message


async def test_event_sync_runs_between_scheduled_requests(setup):
    import asyncio

    daemon, _, store, rpc = setup
    store.find_linked_players = AsyncMock(
        return_value=[
            PlayerRecord(pid=i, uuid=f"scheduled-{i}", nickname="p", discord_id=str(i))
            for i in (1, 2)
        ]
    )
    first_request = asyncio.Event()
    release_first = asyncio.Event()
    event_started = asyncio.Event()
    order = []

    async def send(**kwargs):
        order.append(kwargs["player_uuid"])
        if len(order) == 1:
            first_request.set()
            await release_first.wait()

    rpc.sync_staff.side_effect = send
    scheduled = asyncio.create_task(daemon.reconcile())

    async def event_sync():
        event_started.set()
        return await daemon.sync_player("event-player", "456")

    event = None
    try:
        await first_request.wait()
        event = asyncio.create_task(event_sync())
        await event_started.wait()
        release_first.set()
        assert await event
        assert (await scheduled)["synced"] == 2
        assert order == ["scheduled-1", "event-player", "scheduled-2"]
    finally:
        scheduled.cancel()
        if event is not None:
            event.cancel()
        await asyncio.gather(
            scheduled, *([event] if event else []), return_exceptions=True
        )


async def test_schedule_does_not_add_pass_duration_to_interval(setup, monkeypatch):
    daemon, _, _, _ = setup
    daemon._bot.wait_until_ready = AsyncMock()
    daemon._bot.is_closed.side_effect = [False, False, True]
    clock = [0.0]

    async def sleep(seconds):
        clock[0] += seconds

    async def reconcile():
        clock[0] += 250
        return {"synced": 1000}

    sleeps = AsyncMock(side_effect=sleep)
    daemon.reconcile = AsyncMock(side_effect=reconcile)
    monkeypatch.setattr(
        "xcore_discord_bot.daemons.staff_sync_daemon.time.monotonic", lambda: clock[0]
    )
    monkeypatch.setattr(
        "xcore_discord_bot.daemons.staff_sync_daemon.asyncio.sleep", sleeps
    )
    await daemon._run_loop()
    assert [call.args[0] for call in sleeps.await_args_list] == [600, 350]
