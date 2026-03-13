from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any

import pytest

from xcore_discord_bot.handlers_moderation import post_ban_log, post_mute_log


@dataclass
class _Channel:
    sent: list[dict[str, Any]] = field(default_factory=list)

    async def send(self, *, embed: Any) -> None:
        self.sent.append({"embed": embed})


class _Bot:
    def __init__(self, *, bans_channel_id: int = 0, mutes_channel_id: int = 0) -> None:
        self.bans_channel_id = bans_channel_id
        self.mutes_channel_id = mutes_channel_id
        self.channel = _Channel()

    async def _resolve_messageable_channel(
        self, channel_id: int, *, context: str
    ) -> _Channel | None:
        if context == "ban logs":
            assert channel_id == self.bans_channel_id
        if context == "mute logs":
            assert channel_id == self.mutes_channel_id
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
