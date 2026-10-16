from __future__ import annotations

from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from cases_badges_fakes import badge_core, player_row

from xcore_discord_bot.cases.flows import badges
from xcore_discord_bot.cases.flows.badges import grant_map_maker
from xcore_discord_bot.staff_actions import Actor

ACTOR = Actor("101", "Map reviewer")


async def test_mapmaker_multiple_accounts_flag_off_role_and_retry_are_idempotent():
    service, db, _bot, mutations, bus, _, author = await badge_core(
        enabled=False,
        rows=[player_row(pid=0), player_row(2, pid=-1), player_row(3, discord_id="43")],
        discord_map_maker_role_id=70,
        cases_badges_requestable="admin",
    )
    first = await grant_map_maker(service, 42, actor=ACTOR, request_id="map:700")
    assert first == "complete"
    assert [row["unlocked_badges"] for row in mutations["players"].docs] == [
        ["map-maker"],
        ["map-maker"],
        [],
    ]
    assert len(bus.inventory_events) == 2
    author.add_roles.assert_awaited_once()
    second = await grant_map_maker(service, "42", actor=ACTOR)
    assert second == "complete"
    assert (
        len(bus.inventory_events) == 4
    )  # fresh inventories also repair missed transport delivery
    author.add_roles.assert_awaited_once()
    assert db["moderation_audit"].docs == mutations["moderation_audit"].docs == []
    assert db["cases"].docs == []  # badge grants never prove or open a map acceptance


async def test_mapmaker_no_role_configured_is_explicitly_skipped():
    service, _, bot, _, bus, *_ = await badge_core(enabled=False)
    bot.get_guild = lambda _: pytest.fail("Role zero must do no guild/member work")
    result = await grant_map_maker(service, 42, actor=ACTOR)
    assert result == "complete"
    assert len(bus.inventory_events) == 1


@pytest.mark.parametrize("role_id", [0, 70])
async def test_mapmaker_unlinked_result_keeps_b8_pending_and_grants_no_role(role_id):
    service, db, bot, _, bus, _, author = await badge_core(
        rows=[], discord_map_maker_role_id=role_id
    )
    bot.get_guild = lambda _: pytest.fail("Unlinked author must not receive a role")
    result = await grant_map_maker(service, 42, actor=ACTOR)
    assert result == "link_needed"
    assert not bus.inventory_events and not db["moderation_audit"].docs
    author.add_roles.assert_not_awaited()


@pytest.mark.parametrize(
    "failure", ["guild", "role", "member", "member_none", "add_role"]
)
async def test_configured_role_lookup_and_add_failures_propagate_and_retry(failure):
    service, _, bot, mutations, bus, guild, author = await badge_core(
        discord_map_maker_role_id=70
    )
    original_get_guild, original_get_role, original_get_member = (
        bot.get_guild,
        guild.get_role,
        guild.get_member,
    )
    original_fetch_member, original_add_roles = guild.fetch_member, author.add_roles
    if failure == "guild":
        bot.get_guild = lambda _: None
    elif failure == "role":
        guild.get_role = lambda _: None
    elif failure in {"member", "member_none"}:
        guild.get_member = lambda _: None
        guild.fetch_member = (
            AsyncMock(side_effect=RuntimeError("fetch failed"))
            if failure == "member"
            else AsyncMock(return_value=None)
        )
    else:
        author.add_roles = AsyncMock(side_effect=RuntimeError("role grant failed"))
    with pytest.raises((LookupError, RuntimeError)):
        await grant_map_maker(service, 42, actor=ACTOR)
    assert mutations["players"].docs[0]["unlocked_badges"] == ["map-maker"]
    bot.get_guild, guild.get_role, guild.get_member = (
        original_get_guild,
        original_get_role,
        original_get_member,
    )
    guild.fetch_member, author.add_roles = original_fetch_member, original_add_roles
    result = await grant_map_maker(service, 42, actor=ACTOR)
    assert result == "complete"
    assert len(bus.inventory_events) == 2


async def test_mapmaker_fetch_member_when_uncached_and_already_role_owned():
    service, _, _, _, _, guild, author = await badge_core(discord_map_maker_role_id=70)
    guild.get_member = lambda _: None
    author.roles.append(SimpleNamespace(id=70))
    result = await grant_map_maker(service, 42, actor=ACTOR)
    assert result == "complete"
    guild.fetch_member.assert_awaited_once_with(42)
    author.add_roles.assert_not_awaited()


async def test_mapmaker_unconfirmed_badge_is_pending_without_role_then_retry_completes():
    service, _, bot, mutations, _, _, author = await badge_core(
        discord_map_maker_role_id=70
    )
    real_grant = bot.grant_badge
    bot.grant_badge = AsyncMock(return_value=False)
    result = await grant_map_maker(service, 42, actor=ACTOR)
    assert result == "pending_accounts"
    author.add_roles.assert_not_awaited()
    assert mutations["players"].docs[0]["unlocked_badges"] == []
    bot.grant_badge = real_grant
    assert await grant_map_maker(service, 42, actor=ACTOR) == "complete"


async def test_mapmaker_inventory_failure_propagates_and_owned_retry_repairs_current_inventory():
    service, _, bot, mutations, bus, _, author = await badge_core(
        discord_map_maker_role_id=70
    )
    publish = bot.publish_player_badge_inventory_changed
    bot.publish_player_badge_inventory_changed = AsyncMock(
        side_effect=RuntimeError("inventory unavailable")
    )
    with pytest.raises(RuntimeError, match="inventory unavailable"):
        await grant_map_maker(service, 42, actor=ACTOR)
    author.add_roles.assert_not_awaited()
    assert mutations["players"].docs[0]["unlocked_badges"] == ["map-maker"]
    # Retry sends the current record, including subsequent grants; no stale snapshot.
    mutations["players"].docs[0]["unlocked_badges"].append("translator")
    bot.publish_player_badge_inventory_changed = publish
    result = await grant_map_maker(service, 42, actor=ACTOR)
    assert result == "complete"
    assert bus.inventory_events[0]["unlocked_badges"] == ("map-maker", "translator")


@pytest.mark.parametrize("failure", ["unlink", "missing", "wrong_uuid"])
async def test_mapmaker_fresh_membership_refuses_changed_accounts(failure):
    service, _, bot, mutations, _, _, author = await badge_core(
        discord_map_maker_role_id=70
    )
    snapshot = await service.players.find_player_by_uuid("private-account-1=")
    service.players.find_players_by_discord_id = AsyncMock(return_value=[snapshot])
    if failure == "unlink":
        mutations["players"].docs[0]["discord_id"] = "43"
    elif failure == "missing":
        mutations["players"].docs.clear()
    else:
        from dataclasses import replace

        service.players.find_player_by_uuid = AsyncMock(
            return_value=replace(snapshot, uuid="other")
        )
    bot.grant_badge = AsyncMock(side_effect=AssertionError("Wrong account mutation"))
    result = await grant_map_maker(service, 42, actor=ACTOR)
    assert result == "pending_accounts"
    bot.grant_badge.assert_not_awaited()
    author.add_roles.assert_not_awaited()


async def test_mapmaker_partial_success_retries_all_accounts_without_regranting():
    service, _, bot, mutations, _, _, author = await badge_core(
        rows=[player_row(), player_row(2)], discord_map_maker_role_id=70
    )
    real_grant = bot.grant_badge

    async def partial(**kwargs):
        return (
            False
            if kwargs["uuid"] == "private-account-2="
            else await real_grant(**kwargs)
        )

    bot.grant_badge = partial
    result = await grant_map_maker(service, 42, actor=ACTOR)
    assert result == "pending_accounts"
    assert [row["unlocked_badges"] for row in mutations["players"].docs] == [
        ["map-maker"],
        [],
    ]
    author.add_roles.assert_not_awaited()
    bot.grant_badge = real_grant
    result = await grant_map_maker(service, 42, actor=ACTOR)
    assert result == "complete"
    assert all(
        row["unlocked_badges"] == ["map-maker"] for row in mutations["players"].docs
    )


async def test_mapmaker_link_changes_after_grant_keep_pending_without_role():
    service, _, bot, mutations, _, _, author = await badge_core(
        discord_map_maker_role_id=70
    )
    real_grant = bot.grant_badge

    async def link_after_grant(**kwargs):
        result = await real_grant(**kwargs)
        mutations["players"].docs.append(player_row(2))
        return result

    bot.grant_badge = link_after_grant
    result = await grant_map_maker(service, 42, actor=ACTOR)
    assert result == "pending_accounts"
    author.add_roles.assert_not_awaited()
    bot.grant_badge = real_grant
    assert await grant_map_maker(service, 42, actor=ACTOR) == "complete"


@pytest.mark.parametrize("id", ["", "wrong", 0, -1])
async def test_mapmaker_invalid_discord_id_refused_without_side_effects(id):
    service, _, bot, _, _, _, author = await badge_core()
    bot.grant_badge = AsyncMock()
    with pytest.raises(ValueError):
        await grant_map_maker(service, id, actor=ACTOR)
    bot.grant_badge.assert_not_awaited()
    author.add_roles.assert_not_awaited()


async def test_mapmaker_never_grants_system_or_ungrantable_catalog_replacement(
    monkeypatch,
):
    service, _, bot, *_ = await badge_core()
    monkeypatch.setattr(
        badges,
        "get_badge",
        lambda _: SimpleNamespace(id="admin", system=True, grantable=False),
    )
    bot.grant_badge = AsyncMock()
    with pytest.raises(ValueError):
        await grant_map_maker(service, 42, actor=ACTOR)
    bot.grant_badge.assert_not_awaited()
