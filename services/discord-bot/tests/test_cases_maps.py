from __future__ import annotations

import asyncio
import json
from dataclasses import replace
from datetime import timedelta
from types import SimpleNamespace
from unittest.mock import AsyncMock

import discord
import pytest
from cases_badges_fakes import last_view, player_row
from cases_fakes import NOW, Interaction, member
from cases_maps_fakes import (
    Attachment,
    authored_message,
    inspect_payload,
    map_core,
    reviewer,
)
from test_cases_msav import msav, v13_assets

from xcore_discord_bot.cases.flows import maps
from xcore_discord_bot.cases.model import (
    AccessDenied,
    ActionFailed,
    CaseKind,
    CaseRace,
    CaseStatus,
)
from xcore_discord_bot.cases.reconcile import reconcile_cases
from xcore_discord_bot.cases.startup import initialize_flows
from xcore_discord_bot.cogs.maps import MapsCog
from xcore_discord_bot.i18n import t
from xcore_discord_bot.rpc.mindustry_rpc import NoLiveServerError
from xcore_discord_bot.staff_actions import MapUploadFailed


@pytest.fixture(autouse=True)
def servers(monkeypatch):
    registry = SimpleNamespace(
        get_all_servers=lambda: [SimpleNamespace(name="mini-pvp", mode="pvp")]
    )
    monkeypatch.setattr(maps, "server_registry", registry)
    return registry


async def opened(**settings):
    env = await map_core(**settings)
    service, _, _, _, _, _, _, thread, _, _ = env
    await maps.on_thread_create(service, thread)
    case = await service.store.by_submission(f"map:{thread.id}")
    assert case and service.creation_complete(case), case
    return env, case


async def do_accept(service, case, *, values=None, user=None, bot=None):
    return await service.dispatch_action(
        case.number,
        "accept",
        user or reviewer(),
        "en",
        values or {"server": "mini-pvp"},
        interaction=Interaction(bot or service.bot),
    )


@pytest.mark.parametrize("tag", ["Report a map", "Balance a map"])
async def test_ignored_tags_leave_post_untouched(tag):
    service, db, bot, _, _, _, _, thread, starter, _ = await map_core()
    thread.applied_tags.append(
        next(t for t in bot.channels[400].available_tags if t.name == tag)
    )
    await maps.on_thread_create(service, thread)
    await maps.on_message(service, starter)
    assert not db["cases"].docs and not db["case_map_intake"].docs
    assert thread.messages == [starter] and not thread.edits
    service.transport.rpc_inspect_map.assert_not_awaited()


async def test_missing_file_prompt_is_idempotent_and_author_file_starts_after_restart():
    service, db, bot, _, _, _, author, thread, starter, attachment = await map_core()
    starter.attachments = []
    await maps.on_thread_create(service, thread)
    await maps.on_thread_create(service, thread)
    assert not db["cases"].docs and len(thread.messages) == 2
    assert thread.messages[-1].content == t("en", "maps-file-needed")
    assert len(db["case_map_intake"].docs) == 1
    service.started_at = NOW + timedelta(days=1)
    later = authored_message(bot, thread, author, attachment)
    await maps.on_message(service, later)
    case = await service.store.by_submission(f"map:{thread.id}")
    assert case and case.payload["message_id"] == str(later.id)
    assert len([m for m in thread.messages if m.embeds]) == 1


async def test_redelivered_creation_and_restart_adopt_one_case_card():
    env, case = await opened()
    service, db, _bot, _, _, _, _, thread, _, _ = env
    await asyncio.gather(
        maps.on_thread_create(service, thread), maps.on_thread_create(service, thread)
    )
    assert len(db["cases"].docs) == 1
    assert len([m for m in thread.messages if m.embeds]) == 1
    assert case.discord["thread_id"] == str(thread.id) and not case.user_threads
    assert thread.name == "Author’s title"
    assert [tag.name for tag in thread.applied_tags] == ["PvP", "Waiting"]
    # Lose the write recording a successfully sent card, as on process death.
    service2, db2, bot2, _, _, _, _, thread2, _, _ = await map_core()
    bot2.failures.arm("post_send", after=True)
    await maps.on_thread_create(service2, thread2)
    service2.store._active.clear()
    await maps.on_thread_create(service2, thread2)
    assert (
        len(db2["cases"].docs) == 1
        and len([m for m in thread2.messages if m.embeds]) == 1
    )


async def test_new_author_file_revalidates_refreshes_same_card_ignores_older_redelivery():
    env, case = await opened()
    service, db, bot, _, _, _, author, thread, starter, _ = env
    replacement = Attachment(3000, msav(meta={"name": "New map"}), "new.msav")
    service.transport.rpc_inspect_map = AsyncMock(
        return_value=inspect_payload(replacement)
    )
    later = authored_message(bot, thread, author, replacement)
    await maps.on_message(service, later)
    current = await service.get(case.number)
    assert (
        current.payload["file_name"] == "new.msav"
        and current.payload["info"]["name"] == "New map"
    )
    assert current.payload["sha256"] != case.payload["sha256"]
    assert current.discord["card_message_id"] == case.discord["card_message_id"]
    assert len([m for m in thread.messages if m.embeds]) == 1
    await maps.on_message(service, starter)
    assert (await service.get(case.number)).payload["file_name"] == "new.msav"
    assert len(db["cases"].docs) == 1
    assert not any("https://" in str(row["payload"]) for row in db["cases"].docs)


async def test_reviewer_file_cannot_replace_author_attachment():
    env, case = await opened()
    service, _, bot, _, _, _, _, thread, _, _ = env
    message = authored_message(bot, thread, reviewer(), Attachment(3000))
    await maps.on_message(service, message)
    assert (await service.get(case.number)).payload == case.payload


@pytest.mark.parametrize("error", [NoLiveServerError(), TimeoutError()])
async def test_pending_inspection_reconcile_retries_with_fresh_url(error):
    service, _, _, _, _, _, _, thread, _, attachment = await map_core()
    service.transport.rpc_inspect_map.side_effect = error
    await maps.on_thread_create(service, thread)
    case = await service.store.by_submission(f"map:{thread.id}")
    assert "deep_checks_pending" in case.flags
    attachment.url += "&renewed=1"
    service.transport.rpc_inspect_map = AsyncMock(
        return_value=inspect_payload(attachment)
    )
    await reconcile_cases(service)
    current = await service.get(case.number)
    assert "deep_checks_pending" not in current.flags
    assert service.transport.rpc_inspect_map.call_args.kwargs == {
        "url": attachment.url,
        "file_name": "arena.msav",
        "profile": "pvp",
    }


@pytest.mark.parametrize("mutation", ["hash", "size", "missing_info", "malformed"])
async def test_deep_checks_never_use_mismatched_or_malformed_inspection(mutation):
    service, _, _, _, _, _, _, thread, _, attachment = await map_core()
    response = inspect_payload(attachment)
    if mutation == "hash":
        response["info"]["sha256"] = "a" * 64
    elif mutation == "size":
        response["info"]["sizeBytes"] += 1
    elif mutation == "missing_info":
        response.pop("info")
    else:
        response["findings"] = [{"code": "no-core", "severity": "fatal"}]
    service.transport.rpc_inspect_map = AsyncMock(return_value=response)
    await maps.on_thread_create(service, thread)
    case = await service.store.by_submission(f"map:{thread.id}")
    assert "deep_checks_pending" in case.flags
    if mutation in {"hash", "size"}:
        with pytest.raises(ActionFailed):
            await do_accept(service, case)


async def test_data_patches_are_attached_once_as_json():
    service, _, _, _, _, _, _, thread, _, attachment = await map_core()
    attachment.data = msav(
        meta={"name": "Arena"},
        tail=v13_assets((0, "patch.json", b'{"rules": {"x": 1}}'), (0, "raw", b"a: b")),
    )
    attachment.size = len(attachment.data)
    service.transport.rpc_inspect_map = AsyncMock(
        return_value=inspect_payload(attachment)
    )
    await maps.on_thread_create(service, thread)
    case = await service.store.by_submission(f"map:{thread.id}")
    await maps._refresh(service, await service.get(case.number))
    sent = [m for m in thread.messages if getattr(m, "file", None)]
    assert len(sent) == 1 and sent[0].file.filename == "arena-patches.json"
    assert json.loads(sent[0].file.fp.read()) == {
        "patch.json": {"rules": {"x": 1}},
        "raw": "a: b",
    }


async def test_unreadable_map_is_a_finding_not_a_pending_check():
    service, _, _, _, _, _, _, thread, _, attachment = await map_core()
    response = inspect_payload(attachment, status="invalid_map")
    response.pop("info")
    response["detail"] = "Gameplay patches/content are unsupported"
    service.transport.rpc_inspect_map = AsyncMock(return_value=response)
    await maps.on_thread_create(service, thread)
    case = await service.store.by_submission(f"map:{thread.id}")
    assert case.payload["profile"] == "pvp"
    assert "deep_checks_pending" not in case.flags
    assert "Gameplay patches/content are unsupported" in str(
        maps.card_builder(case, "en").to_dict()
    )


@pytest.mark.parametrize(
    "requested,applied",
    [("pvp", "attack"), ("pvp", None), ("pvp", "pvp"), (None, "attack")],
)
async def test_inspection_binds_requested_profile_and_allows_inference(
    requested, applied
):
    service, _, bot, _, bus, _, _, thread, _, attachment = await map_core(
        maps_tag_profiles=f"PvP={requested}" if requested else ""
    )
    response = inspect_payload(
        attachment, findings=[{"code": "missing-author", "severity": "warning"}]
    )
    response["info"]["name"] = "Deep name"
    if applied is None:
        response["info"].pop("profile")
    else:
        response["info"]["profile"] = applied
    service.transport.rpc_inspect_map = AsyncMock(return_value=response)
    await maps.on_thread_create(service, thread)
    case = await service.store.by_submission(f"map:{thread.id}")
    pending = requested is not None and requested != applied
    assert ("deep_checks_pending" in case.flags) == pending
    assert case.payload["findings"] == ([] if pending else response["findings"])
    assert case.payload["info"]["name"] == ("Arena" if pending else "Deep name")
    interaction = Interaction(bot, user=reviewer())
    await service.dispatch_action(
        case.number,
        "accept",
        reviewer(),
        "en",
        {"server": "mini-pvp"},
        interaction=interaction,
    )
    assert len(bus.upload_calls) == (0 if pending else 1)
    if pending:
        assert "pending" in interaction.events[-1][1]
        service.transport.rpc_inspect_map.return_value = inspect_payload(attachment)
        await service.dispatch_action(case.number, "recheck", reviewer(), "en")
        assert "deep_checks_pending" not in (await service.get(case.number)).flags


async def test_accept_reuses_slash_payload_author_creator_and_installed_audit_then_badge_role():
    env, case = await opened(discord_map_maker_role_id=70)
    service, db, bot, mutations, bus, _, author, thread, _, attachment = env
    attachment.url += "&fresh=1"
    final = await do_accept(service, case)
    assert final.resolution.code == "accepted" and final.status == CaseStatus.RESOLVED
    assert final.resolution.audit_id and final.resolution.server == "mini-pvp"
    assert final.resolution.file_name == "arena.msav"
    assert len(bus.upload_calls) == 1
    server, files, timeout = bus.upload_calls[0]
    assert server == "mini-pvp" and timeout == 120_000
    expected = maps.prepare_map_upload(
        server,
        [maps.MapUploadFile(attachment.url, attachment.filename)],
        maps.Actor("101", "Staff 101", "reviewer-101"),
        maps.Actor("42", "Staff 42", case.payload["creator_handle"]),
    )
    expected[0]["discordUpload"]["uploadedAt"] = files[0]["discordUpload"]["uploadedAt"]
    assert files == expected
    assert files[0]["discordUpload"]["creator"]["discordId"] == "42"
    audit = db["moderation_audit"].docs[0]
    extra = audit["details"]["extra"]
    assert (
        extra["case"] == str(case.number)
        and extra["reviewed_sha256"] == case.payload["sha256"]
    )
    assert extra["case_action_token"] == final.payload["upload_attempt"]["token"]
    assert (
        audit["target"]["name_snapshot"] == "arena.msav"
        and audit["actor"]["discord_id"] == "101"
    )
    assert len(db["moderation_audit"].docs) == 1
    assert mutations["players"].docs[0]["unlocked_badges"] == ["map-maker"]
    author.add_roles.assert_awaited_once()
    assert final.payload["badge_state"] == "complete"
    assert "pending_badge" not in final.payload
    assert [tag.name for tag in thread.applied_tags] == ["PvP", "Accepted"]
    assert not thread.archived and not thread.locked
    assert any("accepted" in m.content for m in thread.messages)
    assert len(bot.users[42].messages) == 1
    notice = db["case_notices"].docs[0]
    assert notice["kind"] == "map_accepted"
    assert notice["player_uuid"] == "private-account-1="
    assert notice["params"]["map"] == "Arena"
    assert notice["delivered_at"] is None
    assert final.opener == case.opener and final.opener.player_uuid is None


async def test_badge_helper_runs_only_after_durable_accepted_close(monkeypatch):
    env, case = await opened()
    service, _, _, _, _, _, _, _, _, _ = env
    called = []

    async def grant(actual_service, discord_id, *, actor, request_id):
        stored = await actual_service.get(case.number)
        assert (
            stored.resolution.code == "accepted"
            and not stored.active
            and not stored.pending_action
        )
        assert stored.payload["pending_badge"]
        called.append(discord_id)
        return "complete"

    monkeypatch.setattr(maps, "grant_map_maker", grant)
    await do_accept(service, case)
    assert called == ["42"]


async def test_error_findings_require_explicit_accept_anyway_and_server_severity_is_preserved():
    service, _, bot, _, bus, _, _, thread, _, attachment = await map_core()
    response = inspect_payload(
        attachment,
        findings=[{"code": "td-waves-enabled", "severity": "error", "x": 4, "y": 9}],
    )
    service.transport.rpc_inspect_map = AsyncMock(return_value=response)
    await maps.on_thread_create(service, thread)
    case = await service.store.by_submission(f"map:{thread.id}")
    interaction = Interaction(bot, user=reviewer())
    await service.dispatch_action(
        case.number,
        "accept",
        reviewer(),
        "en",
        {"server": "mini-pvp"},
        interaction=interaction,
    )
    view = last_view(interaction)
    assert not bus.upload_calls and "error-level" in interaction.events[-1][1]
    confirm = Interaction(bot, user=reviewer())
    await view.children[0].callback(confirm)
    assert (
        len(bus.upload_calls) == 1
        and (await service.get(case.number)).resolution.code == "accepted"
    )


async def test_server_selection_prioritizes_matching_profile_and_caps_25(servers):
    env, case = await opened()
    service, _, bot, _, bus, _, _, _, _, _ = env
    servers.get_all_servers = lambda: (
        [SimpleNamespace(name=f"server-{i}", mode="attack") for i in range(30)]
        + [SimpleNamespace(name="matching", mode="pvp")]
    )
    interaction = Interaction(bot, user=reviewer())
    await service.dispatch_action(
        case.number, "accept", reviewer(), "en", interaction=interaction
    )
    view = last_view(interaction)
    assert len(view.select.options) == 25 and view.select.options[0].value == "matching"
    assert not bus.upload_calls


async def test_accept_pending_checks_needs_explicit_confirmation():
    env, case = await opened()
    service, _, bot, _, bus, _, _, _, _, _ = env
    await service.checkpoint(case.number, set_fields={"flags": ["deep_checks_pending"]})
    interaction = Interaction(bot, user=reviewer())
    await service.dispatch_action(
        case.number,
        "accept",
        reviewer(),
        "en",
        {"server": "mini-pvp"},
        interaction=interaction,
    )
    assert not bus.upload_calls and "pending" in interaction.events[-1][1]
    await last_view(interaction).children[0].callback(Interaction(bot, user=reviewer()))
    assert len(bus.upload_calls) == 1


@pytest.mark.parametrize(
    "status",
    ["download_failed", "invalid_map", "superseded", "metadata_failed", "rejected"],
)
async def test_noninstalled_status_stays_open_shows_file_and_never_grants_badge(
    status, monkeypatch
):
    env, case = await opened()
    service, db, _, _, bus, _, _, thread, _, _ = env
    bus.statuses = [status]
    grant = AsyncMock()
    monkeypatch.setattr(maps, "grant_map_maker", grant)
    with pytest.raises(ActionFailed, match="arena.msav"):
        await do_accept(service, case)
    current = await service.get(case.number)
    assert (
        current.actionable and not current.pending_action and current.resolution is None
    )
    assert current.payload["last_upload"]["status"] == status
    assert current.payload["upload_attempt"]["state"] == "result"
    assert current.payload["server"] == "mini-pvp"
    grant.assert_not_awaited()
    assert (
        db["moderation_audit"].docs[0]["details"]["extra"]["kind"]
        == "map_upload_result"
    )
    assert [t.name for t in thread.applied_tags] == ["PvP", "Waiting"]
    card = next(m for m in thread.messages if m.embeds).embeds[0]
    assert any(
        t("en", "maps-upload-status-" + status) in field.value for field in card.fields
    )
    await reconcile_cases(service)
    assert len(bus.upload_calls) == 1 and (await service.get(case.number)).actionable


async def test_concurrent_accept_installs_once():
    env, case = await opened()
    service, _, bot, _, bus, _, _, _, _, _ = env
    entered, release = asyncio.Event(), asyncio.Event()
    original = bot.bus.rpc_upload_maps

    async def delayed(**kw):
        entered.set()
        await release.wait()
        return await original(**kw)

    bot.bus.rpc_upload_maps = delayed
    first = asyncio.create_task(do_accept(service, case, user=reviewer(101)))
    await entered.wait()
    with pytest.raises(CaseRace):
        await do_accept(service, case, user=reviewer(102))
    release.set()
    assert (await first).resolution.code == "accepted"
    assert len(bus.upload_calls) == 1


@pytest.mark.parametrize("error", [TimeoutError(), ValueError("invalid response")])
async def test_unknown_upload_blocks_second_press_recheck_and_replacement_without_replay(
    error,
):
    env, case = await opened()
    service, _, bot, _, bus, _, author, thread, _, _ = env
    bus.upload_error = error
    with pytest.raises((ActionFailed, MapUploadFailed)):
        await do_accept(service, case)
    current = await service.get(case.number)
    assert current.actionable and not current.pending_action and maps._unknown(current)
    with pytest.raises(ActionFailed, match="unknown"):
        await do_accept(service, current)
    with pytest.raises(ActionFailed, match="unknown"):
        await service.dispatch_action(case.number, "recheck", reviewer(), "en")
    later = authored_message(
        bot, thread, author, Attachment(3000, msav(meta={"name": "new"}))
    )
    await maps.on_message(service, later)
    await reconcile_cases(service)
    assert (
        len(bus.upload_calls) == 1
        and (await service.get(case.number)).payload["sha256"] == case.payload["sha256"]
    )


async def test_crash_after_upload_before_case_close_recovers_from_audit_without_upload(
    monkeypatch,
):
    env, case = await opened()
    service, _, _, _, bus, _, _, _, _, _ = env
    original = service._finish_action
    service._finish_action = AsyncMock(
        side_effect=RuntimeError("crash before case write")
    )
    with pytest.raises(RuntimeError, match="crash"):
        await do_accept(service, case)
    current = await service.get(case.number)
    assert current.pending_action and current.actionable
    assert current.payload["upload_attempt"]["state"] == "installed"
    service._finish_action = original
    await reconcile_cases(service)
    final = await service.get(case.number)
    assert (
        final.resolution.code == "accepted"
        and final.payload["badge_state"] == "complete"
    )
    assert len(bus.upload_calls) == 1


async def test_audit_write_response_failure_after_persisted_completion_recovers_without_second_upload():
    env, case = await opened()
    service, _, bot, _, bus, _, _, _, _, _ = env
    original = bot.container.audit.append_moderation_audit

    async def failing(**kw):
        await original(**kw)
        raise RuntimeError("audit reply lost")

    bot.container.audit.append_moderation_audit = failing
    final = await do_accept(service, case)
    assert final.resolution.code == "accepted" and len(bus.upload_calls) == 1


async def test_crash_without_completion_then_abandoned_guard_still_blocks_upload(
    monkeypatch,
):
    env, case = await opened()
    service, _, bot, _, bus, _, _, _, _, _ = env

    class ProcessDeath(BaseException):
        pass

    bot.bus.rpc_upload_maps = AsyncMock(side_effect=ProcessDeath())
    with pytest.raises(ProcessDeath):
        await do_accept(service, case)
    service.now = lambda: NOW + timedelta(minutes=11)
    await reconcile_cases(service)
    current = await service.get(case.number)
    assert not current.pending_action and maps._unknown(current) and current.actionable
    with pytest.raises(ActionFailed):
        await do_accept(service, current)
    assert not bus.upload_calls


@pytest.mark.parametrize(
    "change", ["bytes", "attachment_id", "filename", "author", "deleted"]
)
async def test_accept_checks_immutable_reviewed_attachment_and_hash(change):
    env, case = await opened()
    service, _, _bot, _, bus, _, _, _, starter, attachment = env
    if change == "bytes":
        attachment.data = msav(meta={"name": "replacement"})
    elif change == "attachment_id":
        attachment.id += 1
    elif change == "filename":
        attachment.filename = "replacement.msav"
    elif change == "author":
        starter.author = reviewer()
    else:
        starter.attachments = []
    with pytest.raises(ActionFailed):
        await do_accept(service, case)
    assert not bus.upload_calls and (await service.get(case.number)).actionable


async def test_review_change_invalidates_confirmation_before_upload():
    env, case = await opened()
    service, _, bot, _, bus, _, author, thread, _, _ = env
    values = {
        "server": "mini-pvp",
        "review_stamp": case.payload["review_stamp"],
        "accept_anyway": "yes",
    }
    replacement = Attachment(3000, msav(meta={"name": "new"}))
    service.transport.rpc_inspect_map = AsyncMock(
        return_value=inspect_payload(replacement)
    )
    await maps.on_message(service, authored_message(bot, thread, author, replacement))
    with pytest.raises(ActionFailed, match="changed"):
        await do_accept(service, case, values=values)
    assert not bus.upload_calls


@pytest.mark.parametrize("verb", ["accept", "reject", "recheck"])
async def test_map_actions_need_reviewer_and_block_author_including_senior(verb):
    env, case = await opened()
    service = env[0]
    for user in (member(42, (30, 20)), member(102, (10, 20)), member(103, ())):
        with pytest.raises(AccessDenied):
            await service.dispatch_action(
                case.number,
                verb,
                user,
                "en",
                {"server": "mini-pvp", "reason_text": "No"},
            )


async def test_reject_locks_post_switches_tag_by_name_and_notifies_post_and_dm():
    env, case = await opened()
    service, db, bot, mutations, bus, _, _, thread, _, _ = env
    # Resolve the link at close, including an account linked after intake.
    mutations["players"].docs = [player_row(2)]
    mutations["players"].docs[0]["online_server"] = "current-server"
    service.transport.publish_case_notice = AsyncMock()
    # Replace tag objects, verifying use-time lookup by name.
    bot.channels[400].available_tags = [
        discord.ForumTag(name=n) for n in ("PvP", "Waiting", "Accepted", "Rejected")
    ]
    final = await service.reject(
        case.number, reviewer(), reason_code="other", reason_text="No route to the core"
    )
    assert final.status == CaseStatus.REJECTED and thread.locked and not thread.archived
    assert [tag.name for tag in thread.applied_tags] == ["PvP", "Rejected"]
    assert thread.applied_tags[-1] is bot.channels[400].available_tags[-1]
    assert any("No route to the core" in m.content for m in thread.messages)
    assert "No route to the core" in bot.users[42].messages[0]
    assert len(bot.users[42].messages) == 1
    notice = db["case_notices"].docs[0]
    assert notice["kind"] == "map_rejected"
    assert notice["player_uuid"] == "private-account-2="
    assert notice["params"]["map"] == "Arena"
    service.transport.publish_case_notice.assert_awaited_once_with(
        notice_id=notice["_id"],
        player_uuid="private-account-2=",
        server="current-server",
    )
    assert final.opener == case.opener and final.opener.player_uuid is None
    assert not bus.upload_calls


@pytest.mark.parametrize("rows", [[], [player_row(), player_row(2)]])
async def test_map_outcome_without_one_linked_account_is_discord_only(rows):
    env, case = await opened(rows=rows)
    service, db, bot, _, _, _, _, _, _, _ = env
    await service.reject(case.number, reviewer(), reason_code="other", reason_text="No")
    assert not db["case_notices"].docs
    assert len(bot.users[42].messages) == 1


async def test_map_outcome_retry_keeps_one_dm_if_author_links_change():
    env, case = await opened()
    service, db, bot, mutations, _, _, _, _, _, _ = env
    original = service.threads.post_note
    service.threads.post_note = AsyncMock(side_effect=RuntimeError("post unavailable"))
    final = await service.reject(
        case.number, reviewer(), reason_code="other", reason_text="No"
    )
    assert not final.progress.get("closed_hook_done")
    assert not bot.users.get(42)
    mutations["players"].docs = [player_row(2)]
    service.threads.post_note = original
    await service.finish_close(case.number)
    await service.finish_close(case.number)
    assert (await service.get(case.number)).progress["outcomes_done"]
    assert len(bot.users[42].messages) == len(db["case_notices"].docs) == 1
    assert db["case_notices"].docs[0]["player_uuid"] == "private-account-1="


async def test_old_posts_untouched_until_manual_review_even_on_new_author_message():
    service, db, bot, _, _, _, author, thread, _, attachment = await map_core()
    thread.created_at = NOW - timedelta(days=30)
    await maps.on_thread_create(service, thread)
    await maps.on_message(service, authored_message(bot, thread, author, attachment))
    assert not db["cases"].docs and not thread.edits
    interaction = Interaction(bot, user=reviewer())
    interaction.channel = thread
    await MapsCog.cmd_review_map.callback(MapsCog(bot), interaction)
    case = await service.store.by_submission(f"map:{thread.id}")
    assert case and case.payload["message_id"] == str(thread.messages[1].id)
    assert interaction.events[0][0] == "defer"


async def test_feature_off_unset_ids_startup_and_events_do_not_touch_forum():
    service, db, bot, _, _, _, _, thread, starter, _ = await map_core()
    service.settings = service.settings.model_copy(
        update={
            "cases_maps_enabled": False,
            "discord_maps_forum_id": 0,
            "cases_badges_enabled": False,
        }
    )
    await initialize_flows(service)
    await service.registry.dispatch("on_thread_create", service, thread)
    await service.registry.dispatch("on_message", service, starter)
    interaction = Interaction(bot, user=reviewer())
    interaction.channel = thread
    await MapsCog.cmd_review_map.callback(MapsCog(bot), interaction)
    assert not db["cases"].docs and not thread.edits and thread.messages == [starter]
    assert not service.registry.enabled(CaseKind.MAP)
    service.transport.rpc_inspect_map.assert_not_awaited()


@pytest.mark.parametrize("state", ["link_needed", "pending_accounts", "exception"])
async def test_badge_pending_retry_only_after_acceptance_and_stops_at_30_days(
    state, monkeypatch
):
    env, case = await opened()
    service, _, _bot, _, bus, _, _, thread, _, _ = env
    pending = RuntimeError("role failure") if state == "exception" else state
    grant = (
        AsyncMock(side_effect=pending)
        if isinstance(pending, Exception)
        else AsyncMock(return_value=pending)
    )
    monkeypatch.setattr(maps, "grant_map_maker", grant)
    final = await do_accept(service, case)
    assert final.payload["pending_badge"] and final.progress["closed_done"]
    if state == "link_needed":
        assert any(
            t("en", "maps-badge-link-needed") == m.content for m in thread.messages
        )
    grant.reset_mock()
    grant.side_effect = None
    grant.return_value = "complete"
    service.now = lambda: NOW + timedelta(days=30, seconds=1)
    await reconcile_cases(service)
    await maps.reconcile(service, await service.get(case.number))
    grant.assert_not_awaited()
    service.now = lambda: NOW + timedelta(days=30)
    await reconcile_cases(service)
    grant.assert_awaited_once()
    assert "pending_badge" not in (await service.get(case.number)).payload
    assert len(bus.upload_calls) == 1


async def test_real_unlinked_author_links_later_receives_badge_without_reupload():
    env, case = await opened(rows=[])
    service, _, _, mutations, bus, _, _, _thread, _, _ = env
    final = await do_accept(service, case)
    assert (
        final.payload["pending_badge"] and final.payload["badge_state"] == "link_needed"
    )
    from cases_badges_fakes import player_row

    mutations["players"].docs.append(player_row())
    await reconcile_cases(service)
    assert mutations["players"].docs[0]["unlocked_badges"] == ["map-maker"]
    assert "pending_badge" not in (await service.get(case.number)).payload
    assert len(bus.upload_calls) == 1


async def test_invalid_container_blocks_upload_and_never_asks_server():
    service, _, _, _, bus, _, _, thread, _, attachment = await map_core()
    attachment.data = b"not zlib"
    attachment.size = len(attachment.data)
    await maps.on_thread_create(service, thread)
    case = await service.store.by_submission(f"map:{thread.id}")
    assert case.payload["validation_error"] == "zlib"
    service.transport.rpc_inspect_map.assert_not_awaited()
    with pytest.raises(ActionFailed):
        await do_accept(service, case)
    assert not bus.upload_calls


async def test_recheck_authorizes_and_refreshes_latest_file():
    env, case = await opened()
    service, _, bot, _, _, _, author, thread, _, _ = env
    replacement = Attachment(3000, msav(meta={"name": "manual latest"}))
    authored_message(bot, thread, author, replacement)
    service.transport.rpc_inspect_map = AsyncMock(
        return_value=inspect_payload(replacement)
    )
    result = await service.dispatch_action(case.number, "recheck", reviewer(), "en")
    assert result == t("en", "maps-rechecked")
    assert (await service.get(case.number)).payload["info"]["name"] == "manual latest"


@pytest.mark.parametrize("locale", ["en", "ru", "uk"])
async def test_card_groups_findings_coordinates_visible_truncation_and_privacy(locale):
    _env, case = await opened()
    secret = "6fd431ed-d97a-49ec-81d5-e03a2f3e03ce"
    info = {
        "name": f"Private 203.0.113.9 {secret}",
        "width": 128,
        "height": 256,
        "profile": "pvp",
    }
    findings = [
        {
            "code": "td-border-wall-missing",
            "severity": severity,
            "x": i,
            "y": 3,
            "detail": f"203.0.113.9 {secret} " + "a" * 400,
            "count": 200,
        }
        for severity in ("error", "warning", "info")
        for i in range(200)
    ]
    current = replace(
        case, payload={**case.payload, "info": info, "findings": findings}
    )
    card = maps.card_builder(current, locale)
    rendered = str(card.to_dict())
    assert "203.0.113.9" not in rendered and secret not in rendered
    assert len(card) <= 6000 and len(card.fields) <= 25
    assert all(
        len(field.name) <= 256 and len(field.value) <= 1024 for field in card.fields
    )
    for severity in ("error", "warning", "info"):
        field = next(
            f for f in card.fields if f.name == t(locale, "maps-severity-" + severity)
        )
        assert "(0, 3)" in field.value
        assert (
            "and" if locale == "en" else "ещё" if locale == "ru" else "ще"
        ) in field.value


async def test_map_status_tag_has_slot_when_five_gameplay_tags_are_present():
    env, case = await opened()
    service, _, _, _, _, _, _, thread, _, _ = env
    thread.applied_tags = [discord.ForumTag(name=f"gameplay-{i}") for i in range(5)]
    await service.project(case.number)
    assert len(thread.applied_tags) == 5 and thread.applied_tags[-1].name == "Waiting"


def test_discord_calls_match_installed_271_signatures():
    import inspect

    assert discord.__version__ == "2.7.1"
    assert "applied_tags" in inspect.signature(discord.Thread.edit).parameters
    assert "use_cached" in inspect.signature(discord.Attachment.read).parameters
    assert "limit" in inspect.signature(discord.Thread.history).parameters
    assert (
        "allowed_mentions" in inspect.signature(discord.abc.Messageable.send).parameters
    )


async def test_author_file_arriving_while_first_intake_finishes_is_not_lost():
    service, _, bot, _, _, _, author, thread, _, original_attachment = await map_core()
    entered, release = asyncio.Event(), asyncio.Event()
    original = service.store.by_submission
    original_open = service.open

    async def delayed_open(**kwargs):
        entered.set()
        await release.wait()
        return await original_open(**kwargs)

    service.open = delayed_open
    creation = asyncio.create_task(maps.on_thread_create(service, thread))
    await entered.wait()
    replacement = Attachment(3000, msav(meta={"name": "arrived during intake"}))
    message = authored_message(bot, thread, author, replacement)
    response = inspect_payload(original_attachment)
    service.transport.rpc_inspect_map.side_effect = None
    service.transport.rpc_inspect_map = AsyncMock(
        side_effect=lambda **kw: (
            response
            if kw["url"] == original_attachment.url
            else inspect_payload(replacement)
        )
    )
    message_task = asyncio.create_task(maps.on_message(service, message))
    await asyncio.sleep(0)
    release.set()
    await asyncio.gather(creation, message_task)
    case = await original(f"map:{thread.id}")
    assert case.payload["attachment_id"] == "3000"
    assert case.payload["info"]["name"] == "arrived during intake"
    assert len([m for m in thread.messages if m.embeds]) == 1


async def test_accept_refuses_newer_author_file_even_if_gateway_message_was_missed():
    env, case = await opened()
    service, _, bot, _, bus, _, author, thread, _, _ = env
    authored_message(bot, thread, author, Attachment(3000, msav(meta={"name": "new"})))
    with pytest.raises(ActionFailed, match="changed"):
        await do_accept(service, case)
    assert not bus.upload_calls


@pytest.mark.parametrize("stage", ["download", "checkpoint"])
async def test_new_author_file_during_accept_preparation_aborts_and_refreshes(stage):
    env, case = await opened()
    service, _, bot, _, bus, _, author, thread, _, attachment = env
    entered, release = asyncio.Event(), asyncio.Event()
    original_read, original_checkpoint = attachment.read, service.checkpoint

    async def read():
        entered.set()
        await release.wait()
        return await original_read()

    async def checkpoint(number, **kwargs):
        result = await original_checkpoint(number, **kwargs)
        if "payload.upload_attempt" in kwargs.get("set_fields", {}):
            entered.set()
            await release.wait()
        return result

    if stage == "download":
        attachment.read = read
    else:
        service.checkpoint = checkpoint
    accepting = asyncio.create_task(do_accept(service, case))
    await entered.wait()
    replacement = Attachment(3000, msav(meta={"name": "New during preparation"}))
    service.transport.rpc_inspect_map = AsyncMock(
        return_value=inspect_payload(replacement)
    )
    await maps.on_message(service, authored_message(bot, thread, author, replacement))
    assert (await service.get(case.number)).payload["attachment_id"] == "2000"
    release.set()
    with pytest.raises(ActionFailed, match="changed"):
        await accepting
    current = await service.get(case.number)
    assert not bus.upload_calls and current.actionable and not current.pending_action
    assert current.payload["attachment_id"] == "3000"
    assert "upload_attempt" not in current.payload
    cards = [m for m in thread.messages if m.embeds]
    assert len(cards) == 1 and "New during preparation" in str(
        cards[0].embeds[0].to_dict()
    )


async def test_new_author_file_during_failed_upload_refreshes_after_guard_release():
    env, case = await opened()
    service, _, bot, _, bus, _, author, thread, _, _ = env
    entered, release = asyncio.Event(), asyncio.Event()
    original = bot.bus.rpc_upload_maps
    bus.statuses = ["invalid_map"]

    async def upload(**kwargs):
        entered.set()
        await release.wait()
        return await original(**kwargs)

    bot.bus.rpc_upload_maps = upload
    accepting = asyncio.create_task(do_accept(service, case))
    await entered.wait()
    replacement = Attachment(3000, msav(meta={"name": "New during upload"}))
    service.transport.rpc_inspect_map = AsyncMock(
        return_value=inspect_payload(replacement)
    )
    await maps.on_message(service, authored_message(bot, thread, author, replacement))
    assert (await service.get(case.number)).payload["attachment_id"] == "2000"
    release.set()
    with pytest.raises(ActionFailed, match="arena.msav"):
        await accepting
    current = await service.get(case.number)
    assert (
        len(bus.upload_calls) == 1 and current.actionable and not current.pending_action
    )
    assert current.payload["attachment_id"] == "3000"
    assert (
        "upload_attempt" not in current.payload and "last_upload" not in current.payload
    )
    cards = [m for m in thread.messages if m.embeds]
    assert len(cards) == 1 and "New during upload" in str(cards[0].embeds[0].to_dict())


async def test_replacement_clears_status_from_previous_failed_upload():
    env, case = await opened()
    service, _, bot, _, bus, _, author, thread, _, _ = env
    bus.statuses = ["invalid_map"]
    with pytest.raises(ActionFailed):
        await do_accept(service, case)
    replacement = Attachment(3000, msav(meta={"name": "fixed"}))
    service.transport.rpc_inspect_map = AsyncMock(
        return_value=inspect_payload(replacement)
    )
    await maps.on_message(service, authored_message(bot, thread, author, replacement))
    current = await service.get(case.number)
    assert (
        "upload_attempt" not in current.payload
        and "last_upload" not in current.payload
        and "server" not in current.payload
    )


async def test_confirmed_upload_state_is_known_when_senior_reopens_case():
    env, case = await opened()
    service, _, _, _, bus, _, _, _, _, _ = env
    final = await do_accept(service, case)
    assert final.payload["upload_attempt"]["state"] == "installed"
    await service.reopen(case.number, member(102, (20,)))
    assert not maps._unknown(await service.get(case.number))
    await service.dispatch_action(case.number, "recheck", reviewer(), "en")
    assert len(bus.upload_calls) == 1


async def test_badge_role_failure_is_pending_and_real_role_retry_does_not_upload_again():
    env, case = await opened(discord_map_maker_role_id=70)
    service, _, _, mutations, bus, _, author, _, _, _ = env
    original = author.add_roles.side_effect
    author.add_roles.side_effect = RuntimeError("Discord role unavailable")
    final = await do_accept(service, case)
    assert final.payload["pending_badge"] and final.progress["closed_done"]
    assert mutations["players"].docs[0]["unlocked_badges"] == ["map-maker"]
    author.add_roles.side_effect = original
    await reconcile_cases(service)
    current = await service.get(case.number)
    assert "pending_badge" not in current.payload
    assert author.add_roles.await_count == 2 and len(bus.upload_calls) == 1


async def test_starter_is_retried_briefly_when_message_arrives_after_thread(
    monkeypatch,
):
    service, _, _, _, _, _, _, thread, _starter, _ = await map_core()
    original = thread.fetch_message
    attempts = 0

    async def fetch(id):
        nonlocal attempts
        attempts += 1
        if attempts == 1:
            raise discord.NotFound(
                SimpleNamespace(status=404, reason="Not found"), "starter pending"
            )
        return await original(id)

    thread.fetch_message = fetch
    monkeypatch.setattr(maps.asyncio, "sleep", AsyncMock())
    await maps.on_thread_create(service, thread)
    assert await service.store.by_submission(f"map:{thread.id}")
    assert attempts >= 2
    maps.asyncio.sleep.assert_awaited_once_with(0.25)


async def test_post_before_flag_activation_is_untouched_even_if_created_after_process_start():
    service, db, _, _, _, _, _, thread, _, _ = await map_core()
    service.settings = service.settings.model_copy(
        update={"cases_badges_enabled": False}
    )
    service.now = lambda: NOW + timedelta(minutes=1)
    await initialize_flows(service)
    assert service.maps_enabled_at == NOW + timedelta(minutes=1)
    await maps.on_thread_create(service, thread)
    assert not db["cases"].docs and not db["case_map_intake"].docs


async def test_delayed_closure_and_badge_retry_finish_author_delivery_within_30_days():
    env, case = await opened(rows=[])
    service, _, bot, _, bus, _, _, thread, _, _ = env
    original = service.finish_close
    service.finish_close = AsyncMock()
    final = await do_accept(service, case)
    assert final.payload["pending_badge"] and not final.progress.get("outcomes_done")
    service.finish_close = original
    service.now = lambda: NOW + timedelta(days=2)
    await reconcile_cases(service)
    final = await service.get(case.number)
    assert final.progress["outcomes_done"] and final.progress["closed_done"]
    assert any("accepted" in m.content for m in thread.messages)
    assert bot.users[42].messages and len(bus.upload_calls) == 1


async def test_late_matching_installed_audit_closes_unknown_attempt_without_reupload():
    env, case = await opened()
    service, db, _, _, bus, _, _, _, _, _ = env
    bus.upload_error = TimeoutError()
    with pytest.raises(ActionFailed):
        await do_accept(service, case)
    unknown = await service.get(case.number)
    token = unknown.payload["upload_attempt"]["token"]
    await db["moderation_audit"].insert_one(
        {
            "audit_id": "late-confirmed-install",
            "action": "NOTE",
            "occurred_at": NOW,
            "target": {
                "uuid": "",
                "pid": None,
                "name_snapshot": "arena.msav",
                "ip_snapshot": None,
            },
            "actor": {"discord_id": "101", "name_snapshot": "Staff 101"},
            "details": {
                "extra": {
                    "case": str(case.number),
                    "case_action_token": token,
                    "kind": "map_uploaded",
                    "status": "installed",
                    "server": "mini-pvp",
                    "file_name": "arena.msav",
                    "reviewed_sha256": case.payload["sha256"],
                }
            },
        }
    )
    await reconcile_cases(service)
    final = await service.get(case.number)
    assert (
        final.resolution.code == "accepted"
        and final.resolution.audit_id == "late-confirmed-install"
    )
    assert final.payload["upload_attempt"]["state"] == "installed"
    assert final.payload["badge_state"] == "complete" and len(bus.upload_calls) == 1


async def test_restart_adopts_card_after_more_than_200_messages_without_duplicate():
    service, db, bot, _, _, _, author, thread, _, _ = await map_core()
    bot.failures.arm("post_send", after=True)
    await maps.on_thread_create(service, thread)
    orphan = next(message for message in thread.messages if message.embeds)
    for _ in range(250):
        authored_message(bot, thread, author)
    await maps.on_thread_create(service, thread)
    case = await service.store.by_submission(f"map:{thread.id}")
    assert service.creation_complete(case)
    assert case.discord["card_message_id"] == str(orphan.id)
    assert (
        len([m for m in thread.messages if m.embeds]) == 1
        and len(db["cases"].docs) == 1
    )


async def test_latest_author_file_remains_selected_after_more_than_200_discussion_messages():
    env, case = await opened()
    service, _, bot, _, bus, _, author, thread, _, _ = env
    replacement = Attachment(
        3000, msav(meta={"name": "latest after discussion"}), "latest.msav"
    )
    service.transport.rpc_inspect_map = AsyncMock(
        return_value=inspect_payload(replacement)
    )
    await maps.on_message(service, authored_message(bot, thread, author, replacement))
    for _ in range(250):
        authored_message(bot, thread, author)
    await service.dispatch_action(case.number, "recheck", reviewer(), "en")
    current = await service.get(case.number)
    assert current.payload["attachment_id"] == "3000"
    final = await do_accept(service, current)
    assert final.resolution.file_name == "latest.msav" and len(bus.upload_calls) == 1
