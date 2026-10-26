from __future__ import annotations

from datetime import UTC, datetime
from types import SimpleNamespace

import discord
from backends import connected_bus, connected_store

from xcore_discord_bot.cases.notify import CaseNotifier
from xcore_discord_bot.cases.registry import FlowRegistry
from xcore_discord_bot.cases.service import CaseService
from xcore_discord_bot.cases.store import CaseStore
from xcore_discord_bot.cases.threads import FORUM_TAGS, CaseThreads
from xcore_discord_bot.settings import Settings

NOW = datetime(2026, 10, 13, 12, tzinfo=UTC)


class Failures:
    def __init__(self):
        self.pending = {}

    def arm(self, name, *, after=False):
        self.pending[(name, after)] = True

    def hit(self, name, *, after=False):
        if self.pending.pop((name, after), False):
            raise RuntimeError(f"Injected {name} failure")


def member(discord_id=101, roles=(10,)):
    return SimpleNamespace(
        id=discord_id,
        display_name=f"Staff {discord_id}",
        bot=False,
        roles=[SimpleNamespace(id=r) for r in roles],
    )


class Role:
    def __init__(self, id, bot=False, admin=False):
        self.id, self.bot = id, bot
        self.permissions = discord.Permissions(administrator=admin)

    def is_bot_managed(self):
        return self.bot


class Guild:
    def __init__(self):
        self.id = 123
        self.roles = [Role(n) for n in (1, 10, 20, 30, 40)]
        self.default_role = self.roles[0]
        self.me = member(999, ())
        self.members = {101: member(101), 102: member(102)}

    def get_role(self, id):
        return next((role for role in self.roles if role.id == id), None)

    def get_member(self, id):
        return self.members.get(id)

    async def fetch_member(self, id):
        raise discord.NotFound(
            SimpleNamespace(status=404, reason="Not Found"), "Unknown Member"
        )


class Message:
    def __init__(self, bot, channel, id, content="", embed=None, view=None):
        self.bot, self.channel, self.id = bot, channel, id
        self.author = bot.user
        self.created_at = discord.utils.utcnow()
        self.content = content
        self.embeds = [embed] if embed else []
        self.view = view
        self.components = []
        self.edits = []

    async def edit(self, **changes):
        label = (
            "panel_edit"
            if self.channel.type == discord.ChannelType.text
            else "card_edit"
        )
        self.bot.failures.hit(label)
        self.bot.calls.append((label, self.id))
        self.edits.append(changes)
        self.content = changes.get("content", self.content)
        if "embed" in changes:
            self.embeds = [changes["embed"]]
        self.embeds = changes.get("embeds", self.embeds)
        self.view = changes.get("view", self.view)
        self.bot.failures.hit(label, after=True)
        return self


class TextChannel:
    type = discord.ChannelType.text

    def __init__(self, bot, id, guild):
        self.bot, self.id, self.guild = bot, id, guild
        self.messages, self.all_threads = [], []
        self.role_permissions = {}

    @property
    def threads(self):
        return [thread for thread in self.all_threads if not thread.archived]

    def permissions_for(self, who):
        if who.id == 999:
            return discord.Permissions.all()
        return self.role_permissions.get(
            who.id,
            discord.Permissions(view_channel=True, send_messages_in_threads=True),
        )

    def history(self, *, limit=None, oldest_first=False, after=None):
        messages = self.messages if oldest_first else list(reversed(self.messages))
        if after is not None:
            messages = [message for message in messages if message.id > after.id]
        return CursorObjects(messages[:limit] if limit is not None else messages)

    async def fetch_message(self, id):
        self.bot.failures.hit("post_fetch")
        result = next((m for m in self.messages if m.id == id), None)
        if result is None:
            raise discord.NotFound(
                SimpleNamespace(status=404, reason="Not Found"), "Unknown Message"
            )
        self.bot.failures.hit("post_fetch", after=True)
        return result

    async def send(self, content="", **kwargs):
        self.bot.failures.hit("panel_send")
        message = Message(
            self.bot,
            self,
            self.bot.next_id(),
            content,
            kwargs.get("embed"),
            kwargs.get("view"),
        )
        message.embeds = kwargs.get("embeds", message.embeds)
        self.messages.append(message)
        self.bot.failures.hit("panel_send", after=True)
        return message

    def archived_threads(self, **kwargs):
        self.bot.calls.append(("archived_threads", self.id))
        return CursorObjects([t for t in self.all_threads if t.archived])

    async def create_thread(self, **kwargs):
        self.bot.failures.hit("user_create")
        thread = Thread(
            self.bot, self.bot.next_id(), self, kwargs["name"], private=True
        )
        self.all_threads.append(thread)
        self.bot.channels[thread.id] = thread
        self.bot.failures.hit("user_create", after=True)
        return thread


class Forum(TextChannel):
    type = discord.ChannelType.forum

    def __init__(self, bot, id, guild, *, senior=False):
        super().__init__(bot, id, guild)
        self.available_tags = []
        self.senior = senior

    def permissions_for(self, who):
        if who.id in self.role_permissions:
            return self.role_permissions[who.id]
        if who.id == 999:
            return discord.Permissions.all()
        return discord.Permissions(
            view_channel=who.id == 20 if self.senior else who.id in {10, 20}
        )

    async def edit(self, **kwargs):
        self.available_tags = kwargs.get("available_tags", self.available_tags)

    async def create_thread(self, **kwargs):
        self.bot.failures.hit("forum_create")
        thread = Thread(self.bot, self.bot.next_id(), self, kwargs["name"])
        thread.applied_tags = kwargs.get("applied_tags", [])
        message = Message(
            self.bot,
            thread,
            thread.id,
            embed=kwargs["embed"],
            view=kwargs.get("view"),
        )
        thread.messages.append(message)
        self.all_threads.append(thread)
        self.bot.channels[thread.id] = thread
        self.bot.failures.hit("forum_create", after=True)
        return SimpleNamespace(thread=thread, message=message)


class Thread(TextChannel):
    type = discord.ChannelType.public_thread

    def __init__(self, bot, id, parent, name, private=False):
        super().__init__(bot, id, parent.guild)
        self.parent, self.name = parent, name
        self.parent_id = parent.id
        self.owner_id = 999
        self.archived = self.locked = False
        self.private = private
        self.members, self.applied_tags, self.edits = set(), [], []

    async def edit(self, **changes):
        self.bot.failures.hit("thread_edit")
        self.bot.calls.append(("thread_edit", self.id))
        for key, value in changes.items():
            setattr(self, key, value)
        self.edits.append(changes)
        self.bot.failures.hit("thread_edit", after=True)

    async def add_user(self, user):
        self.bot.failures.hit("user_add")
        self.members.add(user.id)
        self.bot.failures.hit("user_add", after=True)

    async def remove_user(self, user):
        self.bot.failures.hit("user_remove")
        self.members.discard(user.id)
        self.bot.failures.hit("user_remove", after=True)

    async def send(self, content="", **kwargs):
        label = "user_send" if self.private else "post_send"
        self.bot.failures.hit(label)
        message = Message(
            self.bot,
            self,
            self.bot.next_id(),
            content,
            kwargs.get("embed"),
            kwargs.get("view"),
        )
        message.file = kwargs.get("file")
        self.messages.append(message)
        self.bot.failures.hit(label, after=True)
        return message


class CursorObjects:
    def __init__(self, items):
        self.items = items

    def __aiter__(self):
        async def generate():
            for item in self.items:
                yield item

        return generate()


class User:
    def __init__(self, bot, id):
        self.bot, self.id = bot, id
        self.messages = []

    async def send(self, text, **kwargs):
        self.bot.failures.hit("dm")
        self.messages.append(text)
        self.bot.failures.hit("dm", after=True)


class Bot:
    def __init__(self):
        self.user = SimpleNamespace(id=999, bot=True)
        self.failures = Failures()
        # Discord calls that cost a request, for tests about what a healthy case costs.
        self.calls = []
        self._seq = 1000
        guild = Guild()
        self.channels = {
            100: TextChannel(self, 100, guild),
            200: Forum(self, 200, guild),
            300: Forum(self, 300, guild, senior=True),
            400: Forum(self, 400, guild),
            500: TextChannel(self, 500, guild),
        }
        self.users = {}

    def next_id(self):
        self._seq += 1
        return self._seq

    def get_channel(self, id):
        return self.channels.get(id)

    def get_guild(self, id):
        return self.channels[100].guild if id == 123 else None

    async def fetch_channel(self, id):
        if id not in self.channels:
            raise discord.NotFound(
                SimpleNamespace(status=404, reason="Not Found"), "Unknown Channel"
            )
        return self.channels[id]

    def get_user(self, id):
        return self.users.get(id)

    async def fetch_user(self, id):
        return self.users.setdefault(id, User(self, id))


class Response:
    def __init__(self, events):
        self.events = events

    async def defer(self, **kwargs):
        self.events.append(("defer", kwargs))

    async def send_message(self, content, **kwargs):
        self.events.append(("message", content, kwargs))

    async def send_modal(self, modal):
        self.events.append(("modal", modal))

    async def edit_message(self, **changes):
        self.events.append(("edit_message", changes))


class Followup:
    def __init__(self, events):
        self.events = events

    async def send(self, content, **kwargs):
        self.events.append(("followup", content, kwargs))

    def texts(self):
        return [event[1] for event in self.events if event[0] == "followup"]


class Interaction:
    def __init__(
        self, bot, *, user=None, locale=discord.Locale.american_english, id=555
    ):
        self.client, self.user, self.locale, self.id = bot, user or member(), locale, id
        self.events = []
        self.response, self.followup = Response(self.events), Followup(self.events)
        self.guild_id = 123
        self.type = discord.InteractionType.component

    async def edit_original_response(self, **changes):
        self.events.append(("edit_original", changes))


def settings(**kwargs):
    defaults = {
        "discord_bot_token": "fake-token",
        "discord_admin_role_id": 10,
        "discord_general_admin_role_id": 20,
        "discord_map_reviewer_role_id": 30,
        "discord_private_channel_id": 50,
        "discord_guild_id": 123,
        "cases_panel_channel_id": 100,
        "cases_staff_forum_id": 200,
        "cases_senior_forum_id": 300,
        "discord_maps_forum_id": 400,
        "cases_complaint_thread_channel_id": 500,
    }
    return Settings(**{**defaults, **kwargs})


async def core(*, flow=None, tags=True, players=None, bus=None, **kwargs):
    """The case service on the real MongoStore and RedisTransport, as the container wires
    it; pass a bot's ``players`` store and ``bus`` for the service to share them."""
    bot, registry = Bot(), FlowRegistry()
    cfg = settings(**kwargs)
    if players is None:
        players, _ = await connected_store(settings=cfg)
    db = players.database
    if tags:
        # The two staff forums as the start-up checks leave them; the maps forum is the
        # owner's and starts without the bot's tags.
        for forum_id in (200, 300):
            bot.channels[forum_id].available_tags = [
                discord.ForumTag(name=name) for name in FORUM_TAGS
            ]
    store = CaseStore(players)
    await store.ensure_indexes()
    if flow:
        registry.register(flow)
        registry.enable(flow.kind)
    service = CaseService(
        store=store,
        settings=cfg,
        registry=registry,
        threads=CaseThreads(bot, cfg),
        notifier=CaseNotifier(bot, cfg, find_player=players.find_player_by_uuid),
        claims=bus or await connected_bus(cfg),
        player_store=players,
        now=lambda: NOW,
    )
    bot.settings = cfg
    bot.container = SimpleNamespace(cases=service)
    return service, db, bot
