"""B5 fakes: the report flow over the core fakes, with the real staff helpers and MongoStore."""

from __future__ import annotations

from datetime import timedelta
from types import SimpleNamespace
from unittest.mock import AsyncMock

from cases_action_fakes import Claims, mirror_audits
from cases_action_fakes import Players as SharedPlayers
from cases_fakes import NOW, Interaction, member
from cases_fakes import core as cases_core
from test_staff_actions import _action_bot
from xcore_protocol.generated.cases import CasesReportSubmittedV1
from xcore_protocol.generated.moderation import (
    ModerationBanCreatedV1,
    ModerationMuteCreatedV1,
)
from xcore_protocol.generated.shared import (
    ActorRefV1,
    CaseBlockSummaryV1,
    CaseChatLineV1,
    CaseChatLineV1Role,
    ExpirationInfoV1,
    PlayerRefV1,
)

from xcore_discord_bot.cases.flows import reports
from xcore_discord_bot.cases.model import CaseKind
from xcore_discord_bot.cases.registry import PanelContext
from xcore_discord_bot.utils.mindustry_colors import strip_mindustry_colors

# Opaque values that must never reach a card, a thread or a reporter.
REPORTER_UUID = "reporter-uuid-secret="
TARGET_UUID = "target-uuid-secret="
SECOND_UUID = "second-uuid-secret="
REPORTER_IP = "198.51.100.7"
TARGET_IP = "198.51.100.8"
STAFF = 101
BLACKLIST_ROLE = 77


def player_rows():
    return [
        {
            "_id": 1,
            "uuid": REPORTER_UUID,
            "pid": 0,
            "nickname": "[red]Reporter",
            "discord_id": "201",
            "local_language": "uk",
            "online_server": "hub",
            "ip": REPORTER_IP,
        },
        {
            "_id": 2,
            "uuid": TARGET_UUID,
            "pid": -5,
            "nickname": "Target",
            "discord_id": "202",
            "online_server": "survival",
            "ip": TARGET_IP,
        },
        {
            "_id": 3,
            "uuid": SECOND_UUID,
            "pid": 7,
            "nickname": "Second",
            "ip": "198.51.100.9",
        },
    ]


class Players(SharedPlayers):
    async def find_players_by_exact_name(self, name):
        # The real query (a case-insensitive match that ignores colour tags) has its own test.
        found = [
            row
            for row in self.database["players"].docs
            if name.strip().casefold()
            in {
                strip_mindustry_colors(str(row.get(key) or "")).casefold()
                for key in ("nickname", "custom_nickname", "username")
            }
        ][:2]
        return [await self.bot.store.find_player_by_uuid(row["uuid"]) for row in found]


class Guild:
    def __init__(self, roles_by_member=None):
        self.roles_by_member = (
            {STAFF: (10,), 102: (10,)} if roles_by_member is None else roles_by_member
        )
        self.fetched = []

    def get_member(self, discord_id):
        roles = self.roles_by_member.get(discord_id)
        return None if roles is None else member(discord_id, roles)

    async def fetch_member(self, discord_id):
        import discord

        self.fetched.append(discord_id)
        raise discord.NotFound(
            SimpleNamespace(status=404, reason="x"), "Unknown Member"
        )


async def reports_core(*, enabled=True, guild=None, **settings):
    settings.setdefault("cases_open_cooldown_seconds", 0)
    service, db, bot = await cases_core(
        flow=reports.REPORT_FLOW, cases_reports_enabled=enabled, **settings
    )
    if not enabled:
        service.registry.disable(CaseKind.REPORT)
    action_bot, mutations, bus, log = _action_bot()
    mutations["players"].docs = player_rows()
    action_bot.store.now_utc = lambda: service.now()
    database_bans = mutations["bans"]
    db.collections["bans"] = database_bans
    db.collections["mutes"] = mutations["mutes"]
    service.player_store = Players(action_bot, mutations)
    service.notifier.find_player = service.player_store.find_player_by_uuid
    service.claims = Claims()
    bot.settings = service.settings.model_copy(
        update={"discord_bans_channel_id": 10, "discord_mutes_channel_id": 20}
    )
    bot.store = action_bot.store
    bot.bus = action_bot.bus
    bot.resolve_channel = action_bot.resolve_channel

    mirror_audits(bot, action_bot, db, mutations)
    bot.guild = guild or Guild()
    bot.get_guild = lambda guild_id: bot.guild if guild_id == 123 else None
    transport = SimpleNamespace(
        publish_case_notice=AsyncMock(),
        rpc_case_evidence=AsyncMock(return_value=None),
        rpc_inspect_map=AsyncMock(return_value=None),
    )
    service.set_transport(transport)
    bot.mutations, bot.bus, bot.log = mutations, bus, log
    return service, db, bot


def report(sid="submission-1", **changes):
    values = {
        "submissionId": sid,
        "server": "origin",
        "reporter": PlayerRefV1(REPORTER_UUID, "Reporter", 0),
        "target": PlayerRefV1(TARGET_UUID, "Target", -5),
        "targetOnline": True,
        "reason": "griefing",
        "chat": (
            CaseChatLineV1(
                int(NOW.timestamp() * 1000),
                "Target",
                CaseChatLineV1Role.TARGET,
                f"my ip is {TARGET_IP}",
                playerPid=-5,
            ),
        ),
        "occurredAt": NOW.isoformat(),
        "comment": "Destroyed our core",
        "reporterLocale": "uk_UA",
        "mapName": "Arena",
        "mapFileName": "arena.msav",
        "gameMode": "survival",
        "sameNetwork": True,
        "blockSummary": CaseBlockSummaryV1(
            windowSeconds=600,
            built=12,
            destroyed=40,
            configured=0,
            truncated=False,
            topBlocks=(),
        ),
    }
    values.update(changes)
    return CasesReportSubmittedV1(**values)


def from_second(sid="submission-2", **changes):
    return report(sid, reporter=PlayerRefV1(SECOND_UUID, "Second", 7), **changes)


def moderation(verb="ban", *, at=None, uuid=TARGET_UUID, actor_name="Ingame Admin"):
    cls = ModerationBanCreatedV1 if verb == "ban" else ModerationMuteCreatedV1
    extra = {"banId": 31} if verb == "ban" else {}
    return cls(
        target=PlayerRefV1(uuid, "Target", -5),
        actor=ActorRefV1(actorName=actor_name),
        reason="griefing in game",
        occurredAt=(at or NOW + timedelta(minutes=1)).isoformat(),
        expiration=ExpirationInfoV1(expiresAt=(NOW + timedelta(days=1)).isoformat()),
        **extra,
    )


async def open_report(service, sid="submission-1", **changes):
    await reports.submitted(service, report(sid, **changes))
    return await service.store.by_submission(sid)


async def panel(service, bot, values, *, user=None, id=700, locale="en", files=()):
    """Submit the panel form the way Discord does: the real modal and its wrapper."""
    user = user or member(301, ())
    opening = Interaction(bot, user=user, id=id - 1)
    modal = reports.open_form(PanelContext(service, opening, CaseKind.REPORT, locale))
    for name, item in modal.inputs.items():
        item._value = values.get(name, "")
    if files:
        modal.files._values = list(files)
    interaction = Interaction(bot, user=user, id=id)
    await modal.on_submit(interaction)
    return interaction, await service.store.by_submission(f"discord:{id}")


async def act(service, case, verb, *, values=None, user=None):
    interaction = Interaction(service.bot, user=user or member(STAFF))
    result = await service.dispatch_action(
        case.number, verb, interaction.user, "en", values, interaction=interaction
    )
    return result, interaction


async def sanction(service, case, verb="ban", *, pid="-5", duration="1d", user=None):
    """Press Ban or Mute, fill the form and confirm."""
    values = {
        "pid": pid,
        "duration": duration,
        "reason": f"griefing · case #{case.number}",
    }
    first, asked = await act(service, case, verb, values=values, user=user)
    assert first is None and asked.followup.texts(), asked.events
    result, _ = await act(
        service, case, verb, values={**values, "confirmed": "yes"}, user=user
    )
    return result


def player(service, uuid):
    return next(
        r for r in service.player_store.database["players"].docs if r["uuid"] == uuid
    )


def move(service, uuid, **changes):
    player(service, uuid).update(changes)
