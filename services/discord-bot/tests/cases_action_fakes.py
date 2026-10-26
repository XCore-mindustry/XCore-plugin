"""The case core on a bot that runs the real staff helpers."""

from bot_fakes import FAMILY_BUSES
from cases_fakes import core
from test_staff_actions import _action_bot


async def staff_core(**kwargs):
    """The case service and its bot, sharing one MongoStore and one RedisTransport with the
    staff helpers as the container does. Returns the service, the database, the bot, the
    game server on the bus and the moderation log channel."""
    action_bot, db, game, log = await _action_bot()
    service, _, bot = await core(players=action_bot.store, bus=action_bot.bus, **kwargs)
    # Audits and sanctions are dated by the case clock, which recovery compares them with.
    action_bot.store.now_utc = lambda: service.now()
    service.punishments = action_bot.container.punishments
    bot.settings = service.settings.model_copy(
        update={"discord_bans_channel_id": 10, "discord_mutes_channel_id": 20}
    )
    bot.store = action_bot.store
    bot.bus = action_bot.bus
    for family in FAMILY_BUSES:
        setattr(bot.container, family, getattr(action_bot.container, family))
    bot.container.punishments = action_bot.container.punishments
    bot.container.audit = action_bot.container.audit
    bot.resolve_channel = action_bot.resolve_channel
    return service, db, bot, game, log
