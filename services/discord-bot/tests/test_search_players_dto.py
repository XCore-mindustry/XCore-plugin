from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

import pytest

from xcore_discord_bot.dto import PlayerRecord
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


class _Bot:
    async def count_players_by_name(self, query: str) -> int:
        assert query == "vor"
        return 1

    async def search_players(
        self,
        query: str,
        *,
        limit: int,
        page: int,
    ) -> list[PlayerRecord]:
        assert query == "vor"
        assert limit == 6
        assert page == 0
        return [PlayerRecord(pid=123, nickname="Vortex", total_play_time=10)]

    async def _send_paginated(self, interaction: _Interaction, fetch_page) -> None:
        embed, _has_next = await fetch_page(0)
        await interaction.response.send_message(embed=embed)


@pytest.mark.asyncio
async def test_cmd_search_renders_player_record_rows() -> None:
    interaction = _Interaction()

    await cmd_search(_Bot(), interaction, "vor")

    assert len(interaction.response.sent) == 1
    embed = interaction.response.sent[0]["embed"]
    assert embed is not None
    assert embed.title == "Search: 'vor'"
    assert len(embed.fields) == 1
    assert embed.fields[0].name == "Vortex"
    assert embed.fields[0].value == "ID: 123 | playtime: 10m"
