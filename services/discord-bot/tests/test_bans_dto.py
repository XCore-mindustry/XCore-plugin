from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any

import pytest

from xcore_discord_bot.dto import BanRecord
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


class _Bot:
    async def count_bans(self, *, name_filter: str | None) -> int:
        assert name_filter == "tar"
        return 1

    async def list_bans(
        self,
        *,
        name_filter: str | None,
        limit: int,
        page: int,
    ) -> list[BanRecord]:
        assert name_filter == "tar"
        assert limit == 6
        assert page == 0
        return [
            BanRecord(
                name="Target",
                admin_name="Admin",
                reason="griefing",
                expire_date=datetime(2026, 1, 2, tzinfo=timezone.utc),
            )
        ]

    async def _send_paginated(self, interaction: _Interaction, fetch_page) -> None:
        embed, _has_next = await fetch_page(0)
        await interaction.response.send_message(embed=embed)


@pytest.mark.asyncio
async def test_cmd_bans_renders_ban_record_rows() -> None:
    interaction = _Interaction()

    await cmd_bans(_Bot(), interaction, "tar")

    assert len(interaction.response.sent) == 1
    embed = interaction.response.sent[0]["embed"]
    assert embed is not None
    assert embed.title == "Bans"
    assert len(embed.fields) == 1
    assert embed.fields[0].name == "Target"
    assert "Admin: Admin" in embed.fields[0].value
    assert "Reason: griefing" in embed.fields[0].value
