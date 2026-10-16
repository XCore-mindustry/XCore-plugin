"""B7 fixture: real staff/Mongo grant helpers, fake Discord, in-memory audit recovery."""

from types import SimpleNamespace
from unittest.mock import AsyncMock

from cases_action_fakes import Players, mirror_audits
from cases_fakes import NOW, Interaction, core, member
from test_staff_actions import _action_bot

from xcore_discord_bot.cases.flows.badges import BADGE_FLOW
from xcore_discord_bot.cases.model import CaseKind
from xcore_discord_bot.cases.views import PanelButton


def player_row(index=1, *, pid=None, discord_id="42", owned=()):
    return {
        "_id": index,
        "uuid": f"private-account-{index}=",
        "pid": index if pid is None else pid,
        "nickname": f"[red]Account {index}",
        "discord_id": discord_id,
        "language": "uk",
        "ip": "203.0.113.9",
        "unlocked_badges": list(owned),
        "active_badge": None,
    }


async def badge_core(*, rows=None, enabled=True, **settings):
    service, database, bot = await core(
        flow=BADGE_FLOW, cases_badges_enabled=enabled, **settings
    )
    if not enabled:
        service.registry.disable(CaseKind.BADGE)
    action_bot, mutations, bus, _ = _action_bot()
    mutations["players"].docs = [player_row()] if rows is None else rows
    action_bot._store.now_utc = lambda: NOW
    service.players = Players(action_bot, mutations)
    service.notifier.find_player = service.players.find_player_by_uuid
    for name in (
        "grant_badge",
        "find_player_by_uuid",
        "now_utc",
        "_player_name",
        "publish_player_badge_inventory_changed",
    ):
        setattr(bot, name, getattr(action_bot, name))

    mirror_audits(bot, action_bot, database, mutations)
    role = SimpleNamespace(id=70)
    author = member(42, ())

    async def add_roles(added):
        author.roles.append(added)

    author.add_roles = AsyncMock(side_effect=add_roles)
    guild = SimpleNamespace(
        id=123,
        get_role=lambda id: role if id == 70 else None,
        get_member=lambda id: author if id == 42 else None,
        fetch_member=AsyncMock(return_value=author),
    )
    bot.get_guild = lambda id: guild if id == 123 else None
    return service, database, bot, mutations, bus, guild, author


def last_view(interaction):
    return interaction.events[-1][2]["view"]


async def choose(view, bot, value, *, user=None, id=600):
    view.select._values = [value]
    interaction = Interaction(bot, user=user or member(42, ()), id=id)
    await view.select.callback(interaction)
    return interaction


async def badge_form(bot, *, account=None, badge="map-maker", locale=None):
    kwargs = {"locale": locale} if locale else {}
    interaction = Interaction(bot, user=member(42, ()), **kwargs)
    await PanelButton(CaseKind.BADGE).callback(interaction)
    if account is not None:
        interaction = await choose(last_view(interaction), bot, account)
    interaction = await choose(last_view(interaction), bot, badge)
    return interaction.events[0][1]


async def submit_form(
    bot, form, *, id=700, statement="I create maps", user=None, locale=None
):
    form.inputs["statement"]._value = statement
    kwargs = {"locale": locale} if locale else {}
    interaction = Interaction(bot, user=user or member(42, ()), id=id, **kwargs)
    await form.on_submit(interaction)
    return interaction


async def opened_badge(service, bot, *, id=700, **kwargs):
    form = await badge_form(bot, **kwargs)
    interaction = await submit_form(bot, form, id=id)
    case = await service.store.by_submission(f"discord:{id}")
    assert case is not None, interaction.events
    return case
