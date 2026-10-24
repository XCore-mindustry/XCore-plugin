from __future__ import annotations

from collections.abc import Awaitable, Callable
from typing import Literal

import discord
from discord import Interaction

from .presentation import build_servers_embed, sort_live_servers
from .registry import server_registry
from .ui_helpers import disable_view_buttons, safe_edit_view_message

MapSortMode = Literal["reputation", "popularity", "name"]


class PaginatorView(discord.ui.View):
    def __init__(
        self,
        *,
        page: int,
        has_prev: bool,
        has_next: bool,
        fetch_page: Callable[[int], Awaitable[tuple[discord.Embed, bool]]],
    ) -> None:
        super().__init__(timeout=120)
        self._page = page
        self._fetch_page = fetch_page
        self.bot_message: discord.Message | None = None
        self._prev_btn.disabled = not has_prev
        self._next_btn.disabled = not has_next

    @discord.ui.button(label="◀ Prev", style=discord.ButtonStyle.secondary)
    async def _prev_btn(
        self, interaction: Interaction, button: discord.ui.Button
    ) -> None:
        await self._turn(interaction, self._page - 1)

    @discord.ui.button(label="Next ▶", style=discord.ButtonStyle.secondary)
    async def _next_btn(
        self, interaction: Interaction, button: discord.ui.Button
    ) -> None:
        await self._turn(interaction, self._page + 1)

    async def _turn(self, interaction: Interaction, new_page: int) -> None:
        embed, has_next = await self._fetch_page(new_page)
        self._page = new_page
        self._prev_btn.disabled = new_page == 0
        self._next_btn.disabled = not has_next
        await interaction.response.edit_message(embed=embed, view=self)

    async def on_timeout(self) -> None:
        disable_view_buttons(self)
        await safe_edit_view_message(self.bot_message, view=self)


class ServersView(discord.ui.View):
    def __init__(
        self,
        *,
        sort_mode: Literal["players", "name"] = "players",
    ) -> None:
        super().__init__(timeout=180)
        self._sort_mode: Literal["players", "name"] = sort_mode
        self.bot_message: discord.Message | None = None
        self._sync_sort_button_label()

    @property
    def sort_mode(self) -> Literal["players", "name"]:
        return self._sort_mode

    def build_embed(self) -> discord.Embed:
        servers = sort_live_servers(server_registry.get_all_servers(), self._sort_mode)
        return build_servers_embed(servers, sort_mode=self._sort_mode)

    @discord.ui.button(label="Refresh", style=discord.ButtonStyle.primary, emoji="🔄")
    async def _refresh_btn(
        self, interaction: Interaction, button: discord.ui.Button
    ) -> None:
        embed = self.build_embed()
        await interaction.response.edit_message(embed=embed, view=self)

    @discord.ui.button(label="Sort: players", style=discord.ButtonStyle.secondary)
    async def _sort_btn(
        self, interaction: Interaction, button: discord.ui.Button
    ) -> None:
        self._sort_mode = "name" if self._sort_mode == "players" else "players"
        self._sync_sort_button_label()
        embed = self.build_embed()
        await interaction.response.edit_message(embed=embed, view=self)

    async def on_timeout(self) -> None:
        disable_view_buttons(self)
        await safe_edit_view_message(self.bot_message, view=self)

    def _sync_sort_button_label(self) -> None:
        self._sort_btn.label = f"Sort: {self._sort_mode}"


class MapsListView(discord.ui.View):
    def __init__(
        self,
        *,
        page: int,
        has_prev: bool,
        has_next: bool,
        sort_mode: MapSortMode,
        fetch_page: Callable[[int, MapSortMode], Awaitable[tuple[discord.Embed, bool]]],
    ) -> None:
        super().__init__(timeout=120)
        self._page = page
        self._sort_mode: MapSortMode = sort_mode
        self._fetch_page = fetch_page
        self.bot_message: discord.Message | None = None
        self._prev_btn.disabled = not has_prev
        self._next_btn.disabled = not has_next
        self._sync_sort_button_label()

    @discord.ui.button(label="◀ Prev", style=discord.ButtonStyle.secondary)
    async def _prev_btn(
        self, interaction: Interaction, button: discord.ui.Button
    ) -> None:
        await self._turn(interaction, self._page - 1)

    @discord.ui.button(label="Next ▶", style=discord.ButtonStyle.secondary)
    async def _next_btn(
        self, interaction: Interaction, button: discord.ui.Button
    ) -> None:
        await self._turn(interaction, self._page + 1)

    @discord.ui.button(label="Sort: reputation", style=discord.ButtonStyle.secondary)
    async def _sort_btn(
        self, interaction: Interaction, button: discord.ui.Button
    ) -> None:
        self._sort_mode = self._next_sort_mode()
        self._sync_sort_button_label()
        embed, has_next = await self._fetch_page(0, self._sort_mode)
        self._page = 0
        self._prev_btn.disabled = True
        self._next_btn.disabled = not has_next
        await interaction.response.edit_message(embed=embed, view=self)

    async def _turn(self, interaction: Interaction, new_page: int) -> None:
        embed, has_next = await self._fetch_page(new_page, self._sort_mode)
        self._page = new_page
        self._prev_btn.disabled = new_page == 0
        self._next_btn.disabled = not has_next
        await interaction.response.edit_message(embed=embed, view=self)

    async def on_timeout(self) -> None:
        disable_view_buttons(self)
        await safe_edit_view_message(self.bot_message, view=self)

    def _sync_sort_button_label(self) -> None:
        self._sort_btn.label = f"Sort: {self._sort_mode}"

    def _next_sort_mode(self) -> MapSortMode:
        if self._sort_mode == "reputation":
            return "popularity"
        if self._sort_mode == "popularity":
            return "name"
        return "reputation"
