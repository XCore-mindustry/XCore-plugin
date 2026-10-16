"""Requester-bound panel selections shared by independently registered flows."""

from collections.abc import Awaitable, Callable, Sequence

import discord

from ...i18n import t
from ..access import check_opening
from ..modals import error_text
from ..registry import PanelContext
from ..views import FollowUpView

PAGE_SIZE = 25


class PanelChoiceView(FollowUpView):
    """Finite pages, each with at most 25 options, private to one user for 60 seconds."""

    def __init__(
        self,
        context: PanelContext,
        options: Sequence[discord.SelectOption],
        chosen: Callable[[discord.Interaction, str], Awaitable[None]],
        *,
        previous_label: str,
        next_label: str,
    ) -> None:
        super().__init__(context.interaction.user.id, context.locale)
        self.context, self.options, self.chosen = context, tuple(options), chosen
        self.page = 0
        if not options:
            return
        self.select = discord.ui.Select(
            placeholder=t(self.locale, "cases-choice-placeholder")
        )
        self.select.callback = self._chosen
        self.add_item(self.select)
        self.previous = discord.ui.Button(label=previous_label)
        self.next = discord.ui.Button(label=next_label)
        self.previous.callback = self._previous
        self.next.callback = self._next
        if len(options) > PAGE_SIZE:
            self.add_item(self.previous)
            self.add_item(self.next)
        self._page()

    def _page(self) -> None:
        start = self.page * PAGE_SIZE
        self.select.options = list(self.options[start : start + PAGE_SIZE])
        self.previous.disabled = self.page == 0
        self.next.disabled = start + PAGE_SIZE >= len(self.options)

    async def interaction_check(self, interaction: discord.Interaction) -> bool:
        if not await super().interaction_check(interaction):
            return False
        try:
            self.context.service.registry.require(self.context.kind)
            check_opening(self.context.service.settings, interaction.user)
        except ValueError as error:
            await interaction.response.send_message(
                error_text(self.locale, error), ephemeral=True
            )
            return False
        return True

    async def on_timeout(self) -> None:
        self.finish()

    async def _previous(self, interaction: discord.Interaction) -> None:
        await self._move(interaction, -1)

    async def _next(self, interaction: discord.Interaction) -> None:
        await self._move(interaction, 1)

    async def _move(self, interaction: discord.Interaction, delta: int) -> None:
        if not await self.interaction_check(interaction):
            return
        self.page = max(0, min((len(self.options) - 1) // PAGE_SIZE, self.page + delta))
        self._page()
        await interaction.response.edit_message(view=self)

    async def _chosen(self, interaction: discord.Interaction) -> None:
        if not await self.interaction_check(interaction):
            return
        values = self.select.values
        if len(values) != 1 or values[0] not in {o.value for o in self.select.options}:
            await interaction.response.send_message(
                t(self.locale, "cases-form-invalid"), ephemeral=True
            )
            return
        self.finish()
        await self.chosen(interaction, values[0])
