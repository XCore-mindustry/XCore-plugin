"""B9 uses the real case core and Discord UI components with in-memory services."""

from types import SimpleNamespace
from unittest.mock import AsyncMock

import discord
from cases_fakes import NOW, Interaction, core, member

from xcore_discord_bot.cases.flows.complaints import COMPLAINT_FLOW
from xcore_discord_bot.cases.model import CaseKind
from xcore_discord_bot.cases.views import PanelButton


def player(index=1):
    return SimpleNamespace(
        uuid=f"private-{index}=",
        pid=index,
        nickname=f"Player {index}",
        language="uk",
        online_server=None,
    )


def audit(*, index=1, action="BAN", target="private-1=", actor="99", **extra):
    return {
        "action": action,
        "target": {"uuid": target, "ip_snapshot": "203.0.113.8"},
        "actor": actor
        if isinstance(actor, dict)
        else {"discord_id": actor, "name_snapshot": "Staff member"},
        "reason": "Sanction reason",
        "occurred_at": NOW,
        "created_at_epoch_ms": int(NOW.timestamp() * 1000) + index,
        **extra,
    }


async def complaint_core(*, players=None, audits=(), enabled=True, **settings):
    service, db, bot = await core(
        flow=COMPLAINT_FLOW,
        cases_complaints_enabled=enabled,
        cases_open_cooldown_seconds=0,
        **settings,
    )
    if not enabled:
        service.registry.disable(CaseKind.COMPLAINT)
    linked = [player()] if players is None else players
    service.player_store.by_discord["42"] = linked
    service.player_store.by_uuid = {p.uuid: p for p in linked}
    db["moderation_audit"].docs = list(audits)
    # Ordinary members see an otherwise empty parent. Staff deny view; seniors allow it.
    for role_id in (10, 30):
        bot.channels[500].role_permissions[role_id] = discord.Permissions.none()
    original = bot.channels[500].create_thread

    async def create_thread(**kwargs):
        thread = await original(**kwargs)
        thread.creation_kwargs = kwargs

        async def remove(user):
            bot.failures.hit("user_remove")
            thread.members.discard(user.id)
            bot.calls.append(("user_remove", thread.id, user.id))
            bot.failures.hit("user_remove", after=True)

        thread.remove_user = remove
        return thread

    bot.channels[500].create_thread = create_thread
    # Signals forbidden operational side effects without connecting to anything.
    bot.store = SimpleNamespace()
    bot.container.audit = SimpleNamespace(append_moderation_audit=AsyncMock())
    return service, db, bot


async def form(bot, *, user=None, locale=discord.Locale.american_english):
    interaction = Interaction(bot, user=user or member(42, ()), locale=locale)
    await PanelButton(CaseKind.COMPLAINT).callback(interaction)
    assert interaction.events[0][0] == "modal", interaction.events
    return interaction.events[0][1]


async def submit(
    bot, modal, *, id=700, user=None, locale=discord.Locale.american_english, **values
):
    for name, value in {
        "raw_subject": "Staff member",
        "statement": "Private complaint text",
        "when": "Yesterday",
        **values,
    }.items():
        modal.inputs[name]._value = value
    interaction = Interaction(bot, user=user or member(42, ()), id=id, locale=locale)
    await modal.on_submit(interaction)
    return interaction


async def opened(service, bot, **values):
    interaction = await submit(bot, await form(bot), **values)
    case = await service.store.by_submission(f"discord:{values.get('id', 700)}")
    assert case is not None, interaction.events
    return case


async def act(service, case, verb, *, values=None, user=None):
    interaction = Interaction(service.bot, user=user or member(101, (20,)))
    # Membership projection rechecks the member's current guild roles in the B5 core.
    service.bot.get_guild(service.settings.discord_guild_id).members[
        interaction.user.id
    ] = interaction.user
    return await service.dispatch_action(
        case.number, verb, interaction.user, "en", values, interaction=interaction
    )
