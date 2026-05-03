from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any

import pytest
from xcore_protocol.generated.shared import VoteKickParticipantV1

from xcore_discord_bot.handlers_moderation import (
    post_ban_log,
    post_mute_log,
    post_vote_kick_log,
)


@dataclass
class _Channel:
    sent: list[dict[str, Any]] = field(default_factory=list)

    async def send(self, *, embed: Any) -> None:
        self.sent.append({"embed": embed})


class _Bot:
    def __init__(
        self,
        *,
        bans_channel_id: int = 0,
        mutes_channel_id: int = 0,
        votekicks_channel_id: int = 0,
    ) -> None:
        self.bans_channel_id = bans_channel_id
        self.mutes_channel_id = mutes_channel_id
        self.votekicks_channel_id = votekicks_channel_id
        self.channel = _Channel()

    async def _resolve_messageable_channel(
        self, channel_id: int, *, context: str
    ) -> _Channel | None:
        if context == "ban logs":
            assert channel_id == self.bans_channel_id
        if context == "mute logs":
            assert channel_id == self.mutes_channel_id
        if context == "vote-kick logs":
            assert channel_id == self.votekicks_channel_id
        return self.channel


@pytest.mark.asyncio
async def test_post_ban_log_shows_admin_mention_when_discord_id_present() -> None:
    bot = _Bot(bans_channel_id=10)

    await post_ban_log(
        bot,
        pid=42,
        name="Target",
        admin_name="AdminNick",
        admin_discord_id="123456",
        reason="Rule 1",
        expire=datetime(2026, 1, 1, tzinfo=timezone.utc),
    )

    embed = bot.channel.sent[0]["embed"]
    fields = {field.name: field.value for field in embed.fields}
    assert fields["Admin"] == "AdminNick (<@123456>)"


@pytest.mark.asyncio
async def test_post_mute_log_uses_admin_name_without_mention_when_missing() -> None:
    bot = _Bot(mutes_channel_id=11)

    await post_mute_log(
        bot,
        pid=42,
        name="Target",
        admin_name="AdminNick",
        admin_discord_id=None,
        reason="Rule 1",
        expire=datetime(2026, 1, 1, tzinfo=timezone.utc),
    )

    embed = bot.channel.sent[0]["embed"]
    fields = {field.name: field.value for field in embed.fields}
    assert fields["Admin"] == "AdminNick"


@pytest.mark.asyncio
async def test_post_vote_kick_log_shows_initiator_reason_and_vote_lists() -> None:
    bot = _Bot(votekicks_channel_id=12)

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

    embed = bot.channel.sent[0]["embed"]
    assert embed.title == "Vote-kick Passed"
    fields = {field.name: field.value for field in embed.fields}
    assert fields["Target"] == "Target (pid=42)"
    assert fields["Initiator"] == "Starter (pid=7) (<@123456> / 123456)"
    assert fields["Reason"] == "griefing"
    assert fields["For (2)"] == (
        "`Starter` (pid=7, <@123456> (123456))\n`mix` (pid=45410)"
    )
    assert fields["Against (1)"] == "`Voter2` (pid=8, <@654321> (654321))"


@pytest.mark.asyncio
async def test_post_vote_kick_log_shows_none_when_against_list_is_empty() -> None:
    bot = _Bot(votekicks_channel_id=12)

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

    embed = bot.channel.sent[0]["embed"]
    fields = {field.name: field.value for field in embed.fields}
    assert fields["Against (0)"] == "None"
