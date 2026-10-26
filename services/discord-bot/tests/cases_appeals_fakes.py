"""B6 fakes run real staff helpers and Mongo mutation guards; no external services."""

from bot_fakes import FAMILY_BUSES
from cases_action_fakes import Claims, Players, mirror_audits
from cases_fakes import NOW, Interaction, core, member
from test_staff_actions import _action_bot

from xcore_discord_bot.cases.flows.appeals import APPEAL_FLOW
from xcore_discord_bot.cases.model import CaseKind
from xcore_discord_bot.cases.views import PanelButton


def player_row(index=1, *, pid=None, discord_id="42"):
    return {
        "_id": index,
        "uuid": f"private-{index}=",
        "pid": index if pid is None else pid,
        "nickname": f"[red]Player {index}",
        "discord_id": discord_id,
        "language": "uk",
        "ip": f"203.0.113.{index}",
    }


_DEFAULT_PID = object()


def ban_row(index=1, *, pid=_DEFAULT_PID, **kwargs):
    from datetime import timedelta

    return {
        "_id": index,
        "uuid": f"private-{index}=",
        "pid": index if pid is _DEFAULT_PID else pid,
        "ip": f"203.0.113.{index}",
        "ban_id": 100 + index,
        "name": f"[red]Banned {index}",
        "admin_name": "Issuer",
        "admin_discord_id": "99",
        "reason": "private ban reason",
        "expire_date": NOW + timedelta(days=7),
        **kwargs,
    }


async def appeal_core(*, players=None, bans=None, enabled=True, **settings):
    service, database, bot = await core(
        flow=APPEAL_FLOW,
        cases_appeals_enabled=enabled,
        cases_open_cooldown_seconds=0,
        **settings,
    )
    if not enabled:
        service.registry.disable(CaseKind.APPEAL)
    action_bot, mutations, bus, log = _action_bot()
    mutations["players"].docs = [player_row()] if players is None else players
    mutations["bans"].docs = [ban_row()] if bans is None else bans
    action_bot.store.now_utc = lambda: NOW
    # Same raw bans as the real Mongo helper. Core cases/audits retain their richer fakes.
    database.collections["bans"] = mutations["bans"]
    service.player_store = Players(action_bot, mutations)
    service.notifier.find_player = service.player_store.find_player_by_uuid
    service.claims = Claims()
    bot.settings = service.settings.model_copy(
        update={"discord_bans_channel_id": 10, "discord_mutes_channel_id": 20}
    )
    bot.store = action_bot.store
    bot.bus = action_bot.bus
    for family in FAMILY_BUSES:
        setattr(bot.container, family, getattr(action_bot.container, family))
    bot.resolve_channel = action_bot.resolve_channel

    mirror_audits(bot, action_bot, database, mutations)
    return service, database, bot, mutations, bus, log


async def form(bot, *, choice=None, user=None, locale=None):
    kwargs = {"locale": locale} if locale else {}
    interaction = Interaction(bot, user=user or member(42, ()), **kwargs)
    await PanelButton(CaseKind.APPEAL).callback(interaction)
    view = interaction.events[-1][2]["view"]
    step = Interaction(bot, user=user or member(42, ()), id=600, **kwargs)
    if hasattr(view, "select"):
        view.select._values = [choice or "0"]
        await view.select.callback(step)
    else:
        await view.children[0].callback(step)
    assert step.events[0][0] == "modal", step.events
    return step.events[0][1]


async def submit(
    bot,
    modal,
    *,
    reference="B-101",
    statement="Please review my ban",
    id=700,
    user=None,
    locale=None,
):
    if "reference" in modal.inputs:
        modal.inputs["reference"]._value = reference
    modal.inputs["statement"]._value = statement
    kwargs = {"locale": locale} if locale else {}
    interaction = Interaction(bot, user=user or member(42, ()), id=id, **kwargs)
    await modal.on_submit(interaction)
    return interaction


async def opened(service, bot, **kwargs):
    modal = await form(bot)
    interaction = await submit(bot, modal, **kwargs)
    case = await service.store.by_submission(f"discord:{kwargs.get('id', 700)}")
    assert case is not None, interaction.events
    return case


async def act(service, case, verb, *, values=None, user=None):
    interaction = Interaction(service.bot, user=user or member())
    return await service.dispatch_action(
        case.number, verb, interaction.user, "en", values, interaction=interaction
    )
