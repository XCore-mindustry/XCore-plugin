from __future__ import annotations

from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import Any
from unittest.mock import AsyncMock

import pytest
from backends import real_bot
from bot_fakes import make_settings
from xcore_protocol.generated.shared import VoteKickParticipantV1

from xcore_discord_bot.moderation_logs import (
    post_ban_log,
    post_mute_log,
    post_vote_kick_log,
)


@dataclass
class _Channel:
    sent: list[dict[str, Any]] = field(default_factory=list)

    async def send(self, *, embed: Any) -> None:
        self.sent.append({"embed": embed})


async def _bot(**channels: int):
    bot, _ = await real_bot(
        settings=make_settings(
            **{f"discord_{name}": channel for name, channel in channels.items()}
        )
    )
    channel = _Channel()
    bot.resolve_channel = AsyncMock(return_value=channel)
    return bot, channel


@pytest.mark.asyncio
async def test_post_ban_log_shows_admin_mention_when_discord_id_present() -> None:
    bot, channel = await _bot(bans_channel_id=10)

    await post_ban_log(
        bot,
        pid=42,
        name="Target",
        admin_name="AdminNick",
        admin_discord_id="123456",
        reason="Rule 1",
        expire=datetime(2026, 1, 1, tzinfo=UTC),
    )

    bot.resolve_channel.assert_awaited_once_with(10, context="ban logs")
    embed = channel.sent[0]["embed"]
    fields = {field.name: field.value for field in embed.fields}
    assert fields["Admin"] == "AdminNick (<@123456>)"


@pytest.mark.asyncio
async def test_post_ban_log_for_a_pid_only_event_numbers_the_players_ban() -> None:
    # Old ban events carry only a pid. The log finds the player's ban by its UUID
    # and IP, and a ban written before bans had ids is given one.
    bot, channel = await _bot(bans_channel_id=10)
    db = bot.store.database
    await db["players"].insert_one(
        {"pid": 42, "uuid": "uuid-42", "ip": "1.2.3.4", "nickname": "Target"}
    )
    await db["bans"].insert_one(
        {
            "uuid": "uuid-42",
            "ip": "1.2.3.4",
            "name": "Target",
            "admin_name": "AdminNick",
            "reason": "Rule 1",
            "expire_date": datetime(2026, 1, 1, tzinfo=UTC),
        }
    )

    await post_ban_log(
        bot,
        pid=42,
        name="Target",
        admin_name="AdminNick",
        admin_discord_id=None,
        reason="Rule 1",
        expire=datetime(2026, 1, 1, tzinfo=UTC),
    )

    (ban,) = await db["bans"].find({}).to_list()
    embed = channel.sent[0]["embed"]
    fields = {field.name: field.value for field in embed.fields}
    assert fields["Ban"] == f"B-{ban['ban_id']}"
    assert fields["Violator"] == "Target (pid=42)"


@pytest.mark.asyncio
async def test_post_mute_log_uses_admin_name_without_mention_when_missing() -> None:
    bot, channel = await _bot(mutes_channel_id=11)

    await post_mute_log(
        bot,
        pid=42,
        name="Target",
        admin_name="AdminNick",
        admin_discord_id=None,
        reason="Rule 1",
        expire=datetime(2026, 1, 1, tzinfo=UTC),
    )

    bot.resolve_channel.assert_awaited_once_with(11, context="mute logs")
    embed = channel.sent[0]["embed"]
    fields = {field.name: field.value for field in embed.fields}
    assert fields["Admin"] == "AdminNick"


@pytest.mark.asyncio
async def test_post_vote_kick_log_shows_initiator_reason_and_vote_lists() -> None:
    bot, channel = await _bot(votekicks_channel_id=12)

    await post_vote_kick_log(
        bot,
        target_name="Target",
        target_pid=42,
        starter_name="Starter",
        starter_pid=7,
        starter_discord_id="123456",
        reason="griefing",
        votes_for=[
            VoteKickParticipantV1(
                playerName="[green]Starter[]",
                playerPid=7,
                discordId="123456",
            ),
            VoteKickParticipantV1(
                playerName="[#2CABFEFF]mix",
                playerPid=45410,
                discordId=None,
            ),
        ],
        votes_against=[
            VoteKickParticipantV1(
                playerName="Voter2",
                playerPid=8,
                discordId="654321",
            )
        ],
    )

    bot.resolve_channel.assert_awaited_once_with(12, context="vote-kick logs")
    embed = channel.sent[0]["embed"]
    assert embed.title == "Vote-kick Passed"
    fields = {field.name: field.value for field in embed.fields}
    assert fields["Target"] == "Target (pid=42)"
    assert fields["Initiator"] == "Starter (pid=7) (<@123456>)"
    assert fields["Reason"] == "griefing"
    assert fields["For (2)"] == ("`Starter` (pid=7, <@123456>)\n`mix` (pid=45410)")
    assert fields["Against (1)"] == "`Voter2` (pid=8, <@654321>)"


@pytest.mark.asyncio
async def test_post_vote_kick_log_shows_none_when_against_list_is_empty() -> None:
    bot, channel = await _bot(votekicks_channel_id=12)

    await post_vote_kick_log(
        bot,
        target_name="Target",
        target_pid=42,
        starter_name="Starter",
        starter_pid=7,
        starter_discord_id=None,
        reason="griefing",
        votes_for=[
            VoteKickParticipantV1(
                playerName="Starter",
                playerPid=7,
                discordId=None,
            )
        ],
        votes_against=[],
    )

    bot.resolve_channel.assert_awaited_once_with(12, context="vote-kick logs")
    embed = channel.sent[0]["embed"]
    fields = {field.name: field.value for field in embed.fields}
    assert fields["Against (0)"] == "None"
