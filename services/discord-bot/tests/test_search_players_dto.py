from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

import pytest
from backends import real_bot

from xcore_discord_bot.handlers_misc import cmd_search


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

    async def original_response(self) -> None:
        return None


@pytest.mark.asyncio
async def test_cmd_search_renders_player_record_rows() -> None:
    bot, db = await real_bot()
    await db["players"].insert_one(
        {"_id": 1, "pid": 123, "nickname": "Vortex", "total_play_time": 10}
    )
    await db["players"].insert_one({"_id": 2, "pid": 124, "nickname": "Someone"})
    interaction = _Interaction()

    await cmd_search(bot, interaction, "vor")

    assert len(interaction.response.sent) == 1
    embed = interaction.response.sent[0]["embed"]
    assert embed is not None
    assert embed.title == "Search: 'vor'"
    assert len(embed.fields) == 1
    assert embed.fields[0].name == "Vortex"
    assert embed.fields[0].value == "`#123` · played `10m`"
