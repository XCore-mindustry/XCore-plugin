from __future__ import annotations

from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import Any

import pytest
from backends import real_bot

from xcore_discord_bot.handlers_misc import cmd_bans


@dataclass
class _Response:
    sent: list[dict[str, Any]] = field(default_factory=list)

    async def send_message(
        self,
        content: str | None = None,
        *,
        embed: Any = None,
        view: Any = None,
        ephemeral: bool = False,
    ) -> None:
        self.sent.append(
            {
                "content": content,
                "embed": embed,
                "view": view,
                "ephemeral": ephemeral,
            }
        )


@dataclass
class _Interaction:
    response: _Response = field(default_factory=_Response)

    async def original_response(self):
        return None


@pytest.mark.asyncio
async def test_cmd_bans_renders_ban_record_rows() -> None:
    bot, db = await real_bot()
    for index, name in enumerate(("Target", "Someone")):
        await db["bans"].insert_one(
            {
                "_id": index,
                "ban_id": index + 1,
                "uuid": f"uuid-{index}",
                "pid": 42 + index,
                "name": name,
                "admin_name": "Admin",
                "admin_discord_id": "123456",
                "reason": "griefing",
                "expire_date": datetime(2026, 1, 2, tzinfo=UTC),
            }
        )
    interaction = _Interaction()

    await cmd_bans(bot, interaction, "tar")

    assert len(interaction.response.sent) == 1
    embed = interaction.response.sent[0]["embed"]
    assert embed is not None
    assert embed.title == "Bans"
    assert len(embed.fields) == 1
    assert embed.fields[0].name == "Target"
    assert "PID: `42`" in embed.fields[0].value
    assert "Admin: Admin (<@123456>)" in embed.fields[0].value
    assert "Reason: griefing" in embed.fields[0].value
