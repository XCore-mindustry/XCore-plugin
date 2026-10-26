from types import SimpleNamespace
from unittest.mock import AsyncMock

import discord
from bot_fakes import make_rpc
from cases_badges_fakes import badge_core
from cases_fakes import NOW, Message, Thread, member
from test_cases_msav import msav

from xcore_discord_bot.cases.flows.maps import MAP_FLOW
from xcore_discord_bot.cases.model import CaseKind
from xcore_discord_bot.cases.msav import validate_msav


class Attachment:
    def __init__(self, id=2000, data=None, filename="arena.msav"):
        self.id = id
        self.data = msav() if data is None else data
        self.filename = filename
        self.size = len(self.data)
        self.url = f"https://cdn.discordapp.com/attachments/400/{id}/{filename}?signature=fresh"
        self.read = AsyncMock(side_effect=lambda: self.data)


async def map_core(**kwargs):
    service, db, bot, mutations, bus, guild, author = await badge_core(
        cases_maps_enabled=True, **kwargs
    )
    service.registry.disable(CaseKind.BADGE)
    service.registry.register(MAP_FLOW)
    service.registry.enable(CaseKind.MAP)
    bot.map_cache = {}
    bot.bus.rpc_upload_maps = bus.rpc_upload_maps
    # Tests swap the upload RPC on bot.bus while a case runs.
    bot.container.rpc = make_rpc(bot.bus, maps=bot.bus)
    author.bot = False
    author.name = "mapper"
    forum = bot.channels[400]
    forum.available_tags = [
        discord.ForumTag(name=name)
        for name in (
            "Waiting",
            "Accepted",
            "Rejected",
            "PvP",
            "Tower Defence",
            "Report a map",
            "Balance a map",
        )
    ]
    service.transport = SimpleNamespace(
        rpc_inspect_map=AsyncMock(side_effect=lambda **kw: inspect_payload(attachment))
    )
    attachment = Attachment()
    thread = Thread(bot, bot.next_id(), forum, "Author’s title")
    thread.owner_id = author.id
    thread.created_at = NOW
    thread.applied_tags = [next(t for t in forum.available_tags if t.name == "PvP")]
    forum.all_threads.append(thread)
    bot.channels[thread.id] = thread
    starter = authored_message(bot, thread, author, attachment, id=thread.id)
    return service, db, bot, mutations, bus, guild, author, thread, starter, attachment


def authored_message(bot, thread, author, attachment=None, *, id=None):
    message = Message(bot, thread, id or bot.next_id())
    message.author = author
    message.attachments = [attachment] if attachment else []
    thread.messages.append(message)
    return message


def inspect_payload(attachment, *, status="ok", findings=None):
    local = validate_msav(attachment.data, attachment.filename)
    return {
        "messageType": "maps.inspect.response",
        "messageVersion": 1,
        "server": "mini-pvp",
        "status": status,
        "findings": findings or [],
        "info": {
            "name": local.meta["name"],
            "width": 128,
            "height": 128,
            "sha256": local.sha256,
            "sizeBytes": local.size,
            "profile": "pvp",
            "gameMode": "pvp",
        },
    }


def reviewer(id=101):
    user = member(id, (30,))
    user.name = f"reviewer-{id}"
    return user
