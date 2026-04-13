from __future__ import annotations

from collections.abc import Mapping
from datetime import timedelta
from typing import Awaitable, Callable

import discord
from discord import Interaction

from .dto import PlayerRecord
from .permissions import admin_role_ids, ensure_any_role
from .settings import Settings
from .moderation_modals import StatsBanModal, StatsMuteModal
from .ui_helpers import (
    disable_view_buttons,
    ensure_requester_action_allowed,
    safe_edit_view_message,
)

MSG_PLAYER_NOT_FOUND = "Player not found"

PerformBanFn = Callable[..., Awaitable[str]]
PerformRemoveMapFn = Callable[..., Awaitable[str]]
DeleteMuteFn = Callable[..., Awaitable[int]]
CreateModalFn = Callable[..., discord.ui.Modal]
FindPlayerByPidFn = Callable[[int], Awaitable[PlayerRecord | None]]
OpenAuditFn = Callable[[Interaction, int, Mapping[str, object]], Awaitable[None]]


class BanConfirmView(discord.ui.View):
    def __init__(
        self,
        *,
        requester_id: int,
        player_id: int,
        player: PlayerRecord,
        period: str,
        reason: str,
        duration: timedelta,
        perform_ban: PerformBanFn,
    ) -> None:
        super().__init__(timeout=120)
        self._requester_id = requester_id
        self._player_id = player_id
        self._player = player
        self._period = period
        self._reason = reason
        self._duration = duration
        self._perform_ban = perform_ban
        self.message: discord.Message | None = None

    @discord.ui.button(label="Yes", style=discord.ButtonStyle.success)
    async def _confirm(
        self, interaction: Interaction, button: discord.ui.Button
    ) -> None:  # noqa: ARG002
        if not await ensure_requester_action_allowed(
            interaction,
            requester_id=self._requester_id,
            denied_message="Only the moderator who started this action can confirm it.",
        ):
            return

        result = await self._perform_ban(
            actor_name=interaction.user.display_name,
            actor_discord_id=str(interaction.user.id),
            player_id=self._player_id,
            period=self._period,
            reason=self._reason,
            duration=self._duration,
            player=self._player,
        )
        self._disable_all()
        await interaction.response.edit_message(content=result, view=self)

    @discord.ui.button(label="No", style=discord.ButtonStyle.danger)
    async def _cancel(
        self, interaction: Interaction, button: discord.ui.Button
    ) -> None:  # noqa: ARG002
        if not await ensure_requester_action_allowed(
            interaction,
            requester_id=self._requester_id,
            denied_message="Only the moderator who started this action can cancel it.",
        ):
            return

        self._disable_all()
        await interaction.response.edit_message(content="Ban cancelled.", view=self)

    async def on_timeout(self) -> None:
        self._disable_all()
        await safe_edit_view_message(self.message, view=self)

    def _disable_all(self) -> None:
        disable_view_buttons(self)


class MapRemoveConfirmView(discord.ui.View):
    def __init__(
        self,
        *,
        requester_id: int,
        server: str,
        file_name: str,
        request_nonce: str,
        perform_remove_map: PerformRemoveMapFn,
    ) -> None:
        super().__init__(timeout=120)
        self._requester_id = requester_id
        self._server = server
        self._file_name = file_name
        self._request_nonce = request_nonce
        self._perform_remove_map = perform_remove_map
        self.message: discord.Message | None = None

    @discord.ui.button(label="Yes", style=discord.ButtonStyle.danger)
    async def _confirm(
        self, interaction: Interaction, button: discord.ui.Button
    ) -> None:  # noqa: ARG002
        if not await ensure_requester_action_allowed(
            interaction,
            requester_id=self._requester_id,
            denied_message="Only the moderator who started this action can confirm it.",
        ):
            return

        await interaction.response.defer()
        result = await self._perform_remove_map(
            server=self._server,
            file_name=self._file_name,
            request_nonce=self._request_nonce,
        )
        self._disable_all()
        if interaction.message is not None:
            await interaction.message.edit(content=result, view=self)

    @discord.ui.button(label="No", style=discord.ButtonStyle.secondary)
    async def _cancel(
        self, interaction: Interaction, button: discord.ui.Button
    ) -> None:  # noqa: ARG002
        if not await ensure_requester_action_allowed(
            interaction,
            requester_id=self._requester_id,
            denied_message="Only the moderator who started this action can cancel it.",
        ):
            return

        self._disable_all()
        await interaction.response.edit_message(
            content="Map removal cancelled.", view=self
        )

    async def on_timeout(self) -> None:
        self._disable_all()
        await safe_edit_view_message(self.message, view=self)

    def _disable_all(self) -> None:
        disable_view_buttons(self)


class MuteUndoView(discord.ui.View):
    def __init__(
        self,
        *,
        requester_id: int,
        uuid: str,
        player_name: str,
        delete_mute: DeleteMuteFn,
    ) -> None:
        super().__init__(timeout=30)
        self._requester_id = requester_id
        self._uuid = uuid
        self._player_name = player_name
        self._delete_mute = delete_mute
        self.message: discord.Message | None = None

    async def interaction_check(self, interaction: Interaction) -> bool:
        return await ensure_requester_action_allowed(
            interaction,
            requester_id=self._requester_id,
            denied_message="Only the moderator who started this action can undo it.",
        )

    @discord.ui.button(label="Undo", style=discord.ButtonStyle.secondary)
    async def _undo(
        self,
        interaction: Interaction,
        button: discord.ui.Button,
    ) -> None:  # noqa: ARG002
        deleted = await self._delete_mute(uuid=self._uuid)
        if deleted > 0:
            content = f"Mute undone for {self._player_name}."
        else:
            content = f"Mute was already inactive for {self._player_name}."
        await interaction.response.edit_message(content=content, view=None)

    async def on_timeout(self) -> None:
        await safe_edit_view_message(self.message, view=None)


class StatsActionsView(discord.ui.View):
    def __init__(
        self,
        *,
        settings: Settings,
        player_id: int,
        player: Mapping[str, object],
        create_ban_modal: CreateModalFn,
        create_mute_modal: CreateModalFn,
        open_target_audit: OpenAuditFn,
        open_actor_audit: OpenAuditFn,
    ) -> None:
        super().__init__(timeout=180)
        self._settings = settings
        self._player_id = player_id
        self._player = dict(player)
        self._create_ban_modal = create_ban_modal
        self._create_mute_modal = create_mute_modal
        self._open_target_audit = open_target_audit
        self._open_actor_audit = open_actor_audit
        self.message: discord.Message | None = None

    async def interaction_check(self, interaction: Interaction) -> bool:
        return await ensure_any_role(
            interaction,
            role_ids=admin_role_ids(self._settings),
            denied_message="Access denied. Required admin role.",
        )

    @discord.ui.button(label="Ban", style=discord.ButtonStyle.danger)
    async def _ban_btn(
        self, interaction: Interaction, button: discord.ui.Button
    ) -> None:  # noqa: ARG002
        modal = self._create_ban_modal(
            player_id=self._player_id,
            player=self._player,
        )
        await interaction.response.send_modal(modal)

    @discord.ui.button(label="Mute", style=discord.ButtonStyle.secondary)
    async def _mute_btn(
        self, interaction: Interaction, button: discord.ui.Button
    ) -> None:  # noqa: ARG002
        modal = self._create_mute_modal(
            player_id=self._player_id,
            player=self._player,
        )
        await interaction.response.send_modal(modal)

    @discord.ui.button(label="History", style=discord.ButtonStyle.primary)
    async def _history_btn(
        self, interaction: Interaction, button: discord.ui.Button
    ) -> None:  # noqa: ARG002
        await self._open_target_audit(interaction, self._player_id, self._player)

    @discord.ui.button(label="Actions", style=discord.ButtonStyle.primary)
    async def _actions_btn(
        self, interaction: Interaction, button: discord.ui.Button
    ) -> None:  # noqa: ARG002
        await self._open_actor_audit(interaction, self._player_id, self._player)

    async def on_timeout(self) -> None:
        disable_view_buttons(self)
        await safe_edit_view_message(self.message, view=self)


StatsBanModal.__name__ = "_StatsBanModal"
StatsMuteModal.__name__ = "_StatsMuteModal"

_StatsBanModal = StatsBanModal
_StatsMuteModal = StatsMuteModal
