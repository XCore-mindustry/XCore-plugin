from __future__ import annotations

from functools import wraps

import discord
from discord import Interaction, app_commands
from discord.ext import commands

from ..cases.access import staff_roles
from ..cases.ids import parse_case_number
from ..cases.modals import error_text
from ..cases.model import CaseKind, Party
from ..cases.panel import LanguageView
from ..i18n import t
from ..permissions import has_any_role, head_admin_role_ids


def case_command_boundary(callback):
    """Keep unexpected failures inside the bot's cases-aware global error boundary."""

    @wraps(callback)
    async def guarded(self, interaction, number, *args, **kwargs):
        if not hasattr(interaction, "extras"):
            interaction.extras = {}
        interaction.extras["cases_command_number"] = number
        try:
            return await callback(self, interaction, number, *args, **kwargs)
        except Exception as error:
            await self.bot._handle_app_command_error(
                interaction, app_commands.CommandInvokeError(interaction.command, error)
            )

    return guarded


class CasesCog(commands.Cog):
    cases_group = app_commands.Group(name="cases", description="Find and manage cases")
    panel_group = app_commands.Group(
        name="panel", description="Public cases panel", parent=cases_group
    )

    def __init__(self, bot) -> None:
        self.bot = bot

    @property
    def service(self):
        return self.bot.container.cases

    async def _begin(
        self, interaction: Interaction, *, senior: bool = False
    ) -> str | None:
        await interaction.response.defer(ephemeral=True, thinking=True)
        locale = await self.service.store.remember_interaction(interaction)
        roles = (
            head_admin_role_ids(self.service.settings)
            if senior
            else staff_roles(self.service.settings)
        )
        if not has_any_role(interaction.user, roles):
            await interaction.followup.send(
                t(locale, "error-not-allowed"), ephemeral=True
            )
            return None
        return locale

    @panel_group.command(
        name="publish", description="Publish or update the public cases panel"
    )
    async def panel_publish(self, interaction: Interaction) -> None:
        locale = await self._begin(interaction, senior=True)
        if locale is None:
            return
        registry = self.service.registry
        # Start-up diagnostics are for the operator: setting names, as in the log.
        problems = [
            f"{kind}: {', '.join(reasons)}"
            for kind, reasons in registry.disabled_reasons.items()
            if reasons
        ]
        lines = []
        try:
            if not any(flow.panel for flow in registry.active_flows()):
                raise ValueError(t(locale, "cases-panel-empty"))
            message_id = await self.bot.container.case_panel.publish()
            lines.append(
                t(
                    locale,
                    "cases-panel-published",
                    url=f"https://discord.com/channels/{interaction.guild_id}/{self.service.settings.cases_panel_channel_id}/{message_id}",
                )
            )
        except ValueError as error:
            problems.append(str(error))
        if problems:
            lines.append(
                t(locale, "cases-configuration-error", details="\n".join(problems))
            )
        await interaction.followup.send("\n".join(lines)[:2000], ephemeral=True)

    @cases_group.command(name="find", description="Find a case by its number")
    @app_commands.describe(number="Case number, for example #57")
    @case_command_boundary
    async def find(self, interaction: Interaction, number: str) -> None:
        locale = await self._begin(interaction)
        if locale is None:
            return
        try:
            case = await self.service.get(parse_case_number(number))
            await self.service.authorize(case, interaction.user, "find")
            content = t(
                locale,
                "cases-found",
                number=case.number,
                kind=t(locale, "cases-kind-" + case.kind.value),
                status=t(locale, "cases-status-" + case.status.value),
            )
            if case.discord.get("thread_id"):
                content += f"\n<#{case.discord['thread_id']}>"
            await interaction.followup.send(content, ephemeral=True)
        except ValueError as error:
            await interaction.followup.send(error_text(locale, error), ephemeral=True)

    @cases_group.command(
        name="reopen", description="Reopen a resolved or rejected case"
    )
    @app_commands.describe(number="Case number, for example #57")
    @case_command_boundary
    async def reopen(self, interaction: Interaction, number: str) -> None:
        locale = await self._begin(interaction, senior=True)
        if locale is None:
            return
        try:
            case = await self.service.reopen(
                parse_case_number(number), interaction.user
            )
            await interaction.followup.send(
                t(locale, "cases-updated", number=case.number), ephemeral=True
            )
        except ValueError as error:
            await interaction.followup.send(error_text(locale, error), ephemeral=True)

    @cases_group.command(
        name="subject", description="Set the staff member a complaint is about"
    )
    @app_commands.describe(
        number="Case number, for example #57",
        user="The staff member this case is about",
    )
    @case_command_boundary
    async def subject(
        self, interaction: Interaction, number: str, user: discord.Member
    ) -> None:
        locale = await self._begin(interaction, senior=True)
        if locale is None:
            return
        try:
            number_value = parse_case_number(number)
            # A report's subject is the in-game player; a Discord-only party would drop it.
            if (await self.service.get(number_value)).kind != CaseKind.COMPLAINT:
                await interaction.followup.send(
                    t(locale, "cases-subject-complaint-only"), ephemeral=True
                )
                return
            case = await self.service.set_subject(
                number_value,
                interaction.user,
                Party(discord_id=str(user.id), discord_name=user.display_name),
            )
            await interaction.followup.send(
                t(locale, "cases-updated", number=case.number), ephemeral=True
            )
        except ValueError as error:
            await interaction.followup.send(error_text(locale, error), ephemeral=True)

    @app_commands.command(
        name="language", description="Choose the language the bot uses with you"
    )
    async def language(self, interaction: Interaction) -> None:
        locale = self.service.store.interaction_locale_now(interaction)
        await interaction.response.send_message(
            t(locale, "language-select-prompt"),
            view=LanguageView(self.service.store, interaction.user.id, locale),
            ephemeral=True,
        )
        await self.service.store.remember_interaction(interaction)
