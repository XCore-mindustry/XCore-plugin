from __future__ import annotations

from collections.abc import Awaitable, Callable, Mapping
from datetime import timedelta

import discord
from discord import Interaction

from .dto import AccountMergeResult, PlayerRecord
from .moderation_modals import StatsBanModal, StatsMuteModal
from .permissions import admin_role_ids, ensure_any_role
from .presentation import format_minutes
from .settings import Settings
from .ui_helpers import (
    disable_view_buttons,
    ensure_requester_action_allowed,
    safe_edit_view_message,
)

MSG_PLAYER_NOT_FOUND = "Player not found"

PerformBanFn = Callable[..., Awaitable[str]]
PerformMergeFn = Callable[..., Awaitable[AccountMergeResult]]
PerformRemoveMapFn = Callable[..., Awaitable[str]]
DeleteMuteFn = Callable[..., Awaitable[int]]
CreateModalFn = Callable[..., discord.ui.Modal]
FindPlayerByPidFn = Callable[[int], Awaitable[PlayerRecord | None]]
OpenAuditFn = Callable[[Interaction, int, Mapping[str, object]], Awaitable[None]]


class AccountMergeConfirmView(discord.ui.View):
    def __init__(
        self,
        *,
        requester_id: int,
        source_pid: int,
        target_pid: int,
        source_player: PlayerRecord,
        target_player: PlayerRecord,
        reason: str,
        perform_merge: PerformMergeFn,
    ) -> None:
        super().__init__(timeout=120)
        self._requester_id = requester_id
        self._source_pid = source_pid
        self._target_pid = target_pid
        self._source_player = source_player
        self._target_player = target_player
        self._reason = reason
        self._perform_merge = perform_merge
        self.message: discord.Message | None = None

    @discord.ui.button(label="Подтвердить слияние", style=discord.ButtonStyle.danger)
    async def _confirm(
        self, interaction: Interaction, button: discord.ui.Button
    ) -> None:
        if not await ensure_requester_action_allowed(
            interaction,
            requester_id=self._requester_id,
            denied_message="Только администратор, вызвавший команду, может подтвердить слияние.",
        ):
            return

        result = await self._perform_merge(
            source_pid=self._source_pid,
            target_pid=self._target_pid,
            actor_name=interaction.user.display_name,
            actor_discord_id=str(interaction.user.id),
            reason=self._reason,
        )
        self._disable_all()
        if not result.success:
            embed = discord.Embed(
                title="❌ Ошибка слияния аккаунтов",
                description=result.error or "Unknown error",
                color=discord.Color.red(),
            )
            await interaction.response.edit_message(embed=embed, view=self)
            return

        s = result.source_before
        t_before = result.target_before
        t_after = result.target_after

        if s is None or t_before is None or t_after is None:
            embed = discord.Embed(
                title="✅ Аккаунты успешно объединены!",
                color=discord.Color.green(),
            )
            await interaction.response.edit_message(embed=embed, view=self)
            return

        embed = discord.Embed(
            title="✅ Аккаунты успешно объединены!",
            color=discord.Color.green(),
        )
        embed.add_field(
            name="Исходный аккаунт (Закрыт)",
            value=f"PID: `#{s.pid}`\nНикнейм: `{s.nickname}`\nUUID: `{s.uuid}`",
            inline=True,
        )
        embed.add_field(
            name="Целевой аккаунт (Активен)",
            value=f"PID: `#{t_after.pid}`\nНикнейм: `{t_after.nickname}`\nUUID: `{t_after.uuid}`",
            inline=True,
        )
        embed.add_field(
            name="Перенесенные данные",
            value=(
                f"Время игры: `{format_minutes(s.total_play_time)}` (Итог: `{format_minutes(t_after.total_play_time)}`)\n"
                f"PvP Рейтинг: `{s.pvp_rating}` vs `{t_before.pvp_rating}` -> `{t_after.pvp_rating}`\n"
                f"Hexed очки: `+{s.hexed_points}` (Итог: `{t_after.hexed_points}`)\n"
                f"Бейджи: `{len(t_after.unlocked_badges)}` открыто\n"
                f"Матчей переназначено: `{result.games_transferred}`"
            ),
            inline=False,
        )
        if result.ban_transferred:
            embed.add_field(name="⚠️ Бан", value="Активный бан перенесен на целевой аккаунт", inline=False)
        if result.mute_transferred:
            embed.add_field(name="⚠️ Мут", value="Активный мут перенесен на целевой аккаунт", inline=False)

        embed.set_footer(text=f"Audit ID: {result.audit_id or 'n/a'}")
        await interaction.response.edit_message(embed=embed, view=self)

    @discord.ui.button(label="Отмена", style=discord.ButtonStyle.secondary)
    async def _cancel(
        self, interaction: Interaction, button: discord.ui.Button
    ) -> None:
        if not await ensure_requester_action_allowed(
            interaction,
            requester_id=self._requester_id,
            denied_message="Только администратор, вызвавший команду, может отменить слияние.",
        ):
            return

        self._disable_all()
        embed = discord.Embed(
            title="Слияние отменено",
            description="Операция слияния аккаунтов была отменена.",
            color=discord.Color.light_grey(),
        )
        await interaction.response.edit_message(embed=embed, view=self)

    def _disable_all(self) -> None:
        disable_view_buttons(self)

    async def on_timeout(self) -> None:
        self._disable_all()
        await safe_edit_view_message(self.message, view=self)


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
    ) -> None:
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
    ) -> None:
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
    ) -> None:
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
    ) -> None:
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
    ) -> None:
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
    ) -> None:
        modal = self._create_ban_modal(
            player_id=self._player_id,
            player=self._player,
        )
        await interaction.response.send_modal(modal)

    @discord.ui.button(label="Mute", style=discord.ButtonStyle.secondary)
    async def _mute_btn(
        self, interaction: Interaction, button: discord.ui.Button
    ) -> None:
        modal = self._create_mute_modal(
            player_id=self._player_id,
            player=self._player,
        )
        await interaction.response.send_modal(modal)

    @discord.ui.button(label="History", style=discord.ButtonStyle.primary)
    async def _history_btn(
        self, interaction: Interaction, button: discord.ui.Button
    ) -> None:
        await self._open_target_audit(interaction, self._player_id, self._player)

    @discord.ui.button(label="Actions", style=discord.ButtonStyle.primary)
    async def _actions_btn(
        self, interaction: Interaction, button: discord.ui.Button
    ) -> None:
        await self._open_actor_audit(interaction, self._player_id, self._player)

    async def on_timeout(self) -> None:
        disable_view_buttons(self)
        await safe_edit_view_message(self.message, view=self)


StatsBanModal.__name__ = "_StatsBanModal"
StatsMuteModal.__name__ = "_StatsMuteModal"

_StatsBanModal = StatsBanModal
_StatsMuteModal = StatsMuteModal
