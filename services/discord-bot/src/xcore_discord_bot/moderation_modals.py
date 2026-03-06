from __future__ import annotations

from collections.abc import Awaitable, Callable, Mapping

import discord
from discord import Interaction

DISCORD_MODAL_TITLE_MAX = 45


def _build_modal_title(action: str, player: Mapping[str, object]) -> str:
    player_name = str(player.get("nickname", "Unknown"))
    title = f"{action} {player_name}"
    if len(title) <= DISCORD_MODAL_TITLE_MAX:
        return title
    return f"{title[: DISCORD_MODAL_TITLE_MAX - 3]}..."


class StatsBanModal(discord.ui.Modal):
    def __init__(
        self,
        *,
        player_id: int,
        player: Mapping[str, object],
        on_submit_ban: Callable[[Interaction, int, str, str], Awaitable[None]],
    ) -> None:
        super().__init__(title=_build_modal_title("Ban", player))
        self._player_id = player_id
        self._on_submit_ban = on_submit_ban
        self.period = discord.ui.TextInput(
            label="Duration",
            style=discord.TextStyle.short,
            placeholder="1d, 2w, 1y",
            required=True,
            max_length=32,
        )
        self.reason = discord.ui.TextInput(
            label="Reason",
            style=discord.TextStyle.long,
            placeholder="Ban reason",
            required=False,
            default="Not Specified",
            max_length=400,
        )
        self.add_item(self.period)
        self.add_item(self.reason)

    async def on_submit(self, interaction: Interaction) -> None:
        period_value = str(self.period.value).strip()
        reason_value = str(self.reason.value).strip() or "Not Specified"
        await self._on_submit_ban(
            interaction,
            self._player_id,
            period_value,
            reason_value,
        )


class StatsMuteModal(discord.ui.Modal):
    def __init__(
        self,
        *,
        player_id: int,
        player: Mapping[str, object],
        on_submit_mute: Callable[[Interaction, int, str, str], Awaitable[None]],
    ) -> None:
        super().__init__(title=_build_modal_title("Mute", player))
        self._player_id = player_id
        self._on_submit_mute = on_submit_mute
        self.period = discord.ui.TextInput(
            label="Duration",
            style=discord.TextStyle.short,
            placeholder="10m, 1h",
            required=True,
            max_length=32,
        )
        self.reason = discord.ui.TextInput(
            label="Reason",
            style=discord.TextStyle.long,
            placeholder="Mute reason",
            required=False,
            default="Not Specified",
            max_length=400,
        )
        self.add_item(self.period)
        self.add_item(self.reason)

    async def on_submit(self, interaction: Interaction) -> None:
        period_value = str(self.period.value).strip()
        reason_value = str(self.reason.value).strip() or "Not Specified"
        await self._on_submit_mute(
            interaction,
            self._player_id,
            period_value,
            reason_value,
        )
