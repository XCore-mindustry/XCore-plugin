from __future__ import annotations

import logging
import re
from collections.abc import Awaitable, Callable, Sequence
from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Any

import discord

from ..dto import Actor
from ..i18n import SUPPORTED, t
from ._privacy import log_failure
from .access import check_opening
from .cards import case_secrets, safe_text
from .ids import (
    CASE_PATTERN,
    PANEL_PATTERN,
    case_custom_id,
    decode_custom_id,
    panel_custom_id,
)
from .modals import FormModal, error_text
from .model import (
    Case,
    CaseError,
    CaseKind,
    CaseRace,
    CaseStatus,
    InvalidTransition,
    Resolution,
)
from .registry import FlowRegistry, FormField, PanelContext

if TYPE_CHECKING:
    from .service import CaseService

logger = logging.getLogger(__name__)
FOLLOW_UP_SECONDS = 60


def interaction_service(interaction):
    return interaction.client.container.cases


async def failure_text(service, number: int, locale: str, error: Exception) -> str:
    """The reason a press was refused; a stale press also names who got there first."""
    if isinstance(error, (CaseRace, InvalidTransition)):
        try:
            case = await service.get(number)
        except CaseError:
            return error_text(locale, error)
        name = (
            case.resolution.by_name
            if case.resolution
            else (case.pending_action or {}).get("actor", {}).get("name")
            or (case.assignee or {}).get("name")
        )
        if name:
            if case.kind == CaseKind.COMPLAINT:
                name = safe_text(name, secrets=case_secrets(case))
            return t(locale, "error-already-handled-by", name=name)
    return error_text(locale, error)


async def refresh_card(service, number: int) -> None:
    try:
        await service.project(number)
    except CaseError:
        pass


async def run_case_action(
    service,
    interaction,
    number: int,
    verb: str,
    locale: str,
    values: dict[str, Any] | None = None,
) -> None:
    """Run one press after its interaction is acknowledged, and answer it."""
    try:
        result = await service.dispatch_action(
            number, verb, interaction.user, locale, values, interaction=interaction
        )
    except Exception as error:  # noqa: BLE001 -- shown to the user; unexpected ones are logged
        if not isinstance(error, ValueError):
            log_failure(
                logger,
                (
                    service.store.peek(number).kind
                    if service.store.peek(number)
                    else None
                ),
                "Case #%s action %s failed",
                number,
                verb,
            )
        # A refused or stale press leaves the card as the stored case says it should be.
        await refresh_card(service, number)
        await interaction.followup.send(
            await failure_text(service, number, locale, error), ephemeral=True
        )
        return
    if result is None:
        return
    await interaction.followup.send(
        result
        if isinstance(result, str)
        else t(locale, "cases-updated", number=number),
        ephemeral=True,
    )


class CaseActionModal(FormModal):
    """The form behind a card button. Submitting dispatches the action from the start."""

    def __init__(
        self,
        *,
        service,
        number: int,
        verb: str,
        requester_id: int,
        locale: str,
        fields: Sequence[FormField],
        title_key: str | None = None,
    ) -> None:
        super().__init__(
            title=t(locale, title_key or "cases-button-" + verb),
            locale=locale,
            fields=fields,
            files=False,
        )
        self.service, self.number, self.verb = service, number, verb
        self.requester_id = requester_id

    async def on_submit(self, interaction: discord.Interaction) -> None:
        await interaction.response.defer(ephemeral=True, thinking=True)
        locale = await self.service.store.remember_interaction(interaction)
        if interaction.user.id != self.requester_id:
            await interaction.followup.send(
                t(locale, "error-not-allowed"), ephemeral=True
            )
            return
        await run_case_action(
            self.service, interaction, self.number, self.verb, locale, self.values()
        )


class CaseActionButton(
    discord.ui.DynamicItem[discord.ui.Button], template=CASE_PATTERN
):
    def __init__(
        self,
        number: int,
        verb: str,
        *,
        label: str | None = None,
        style: discord.ButtonStyle = discord.ButtonStyle.secondary,
    ) -> None:
        super().__init__(
            discord.ui.Button(
                label=label or verb, style=style, custom_id=case_custom_id(verb, number)
            )
        )
        self.number, self.verb = number, verb

    @classmethod
    async def from_custom_id(
        cls,
        interaction: discord.Interaction,
        item: discord.ui.Item,
        match: re.Match[str],
    ):
        parsed = decode_custom_id(match[0])
        return cls(parsed.number, parsed.verb)

    async def callback(self, interaction: discord.Interaction) -> None:
        service = interaction_service(interaction)
        locale = service.store.interaction_locale_now(interaction)
        case = service.store.peek(self.number)
        fields = None
        if case is not None:
            try:
                fields = service.precheck(case, interaction.user, self.verb, locale)
            except CaseError:
                # The copy may be behind: let the stored case decide and explain.
                case = None
        if case is None:
            await self._uncached(service, interaction)
            return
        if not fields:
            await interaction.response.defer(ephemeral=True, thinking=True)
            locale = await service.store.remember_interaction(interaction)
            await run_case_action(service, interaction, self.number, self.verb, locale)
            return
        # A form is the first response, so nothing above touched the network. The checks that
        # need MongoDB follow at once and again on submit, as 4.6 asks.
        await interaction.response.send_modal(
            CaseActionModal(
                service=service,
                number=self.number,
                verb=self.verb,
                requester_id=interaction.user.id,
                locale=locale,
                fields=fields,
                title_key=service.registry.label_key(case.kind, self.verb),
            )
        )
        locale = await service.store.remember_interaction(interaction)
        try:
            current = await service.get(self.number)
            await service.authorize(current, interaction.user, self.verb)
            if not current.actionable or current.pending_action:
                raise CaseRace()
        except CaseError as error:
            await refresh_card(service, self.number)
            await interaction.followup.send(
                await failure_text(service, self.number, locale, error), ephemeral=True
            )

    async def _uncached(self, service, interaction) -> None:
        """Without a usable copy of the case (closed, unknown, refused, or the bot has just
        started) the press is answered from MongoDB after a defer."""
        await interaction.response.defer(ephemeral=True, thinking=True)
        locale = await service.store.remember_interaction(interaction)
        try:
            case = await service.get(self.number)
            await service.authorize(case, interaction.user, self.verb)
            fields = service.precheck(case, interaction.user, self.verb, locale)
        except CaseError as error:
            if isinstance(error, (CaseRace, InvalidTransition)):
                await refresh_card(service, self.number)
            await interaction.followup.send(
                await failure_text(service, self.number, locale, error), ephemeral=True
            )
            return
        if fields:
            # The form could not be the first response this time; the case is cached now.
            await interaction.followup.send(
                t(locale, "cases-press-again"), ephemeral=True
            )
            return
        await run_case_action(service, interaction, self.number, self.verb, locale)


class PanelButton(discord.ui.DynamicItem[discord.ui.Button], template=PANEL_PATTERN):
    def __init__(
        self,
        kind: CaseKind | None = None,
        *,
        label: str | None = None,
        emoji: str | None = None,
    ) -> None:
        super().__init__(
            discord.ui.Button(
                label=label, emoji=emoji or "🌐", custom_id=panel_custom_id(kind)
            )
        )
        self.kind = kind

    @classmethod
    async def from_custom_id(
        cls,
        interaction: discord.Interaction,
        item: discord.ui.Item,
        match: re.Match[str],
    ):
        return cls(decode_custom_id(match[0]).kind)

    async def callback(self, interaction: discord.Interaction) -> None:
        service = interaction_service(interaction)
        locale = service.store.interaction_locale_now(interaction)
        if self.kind is None:
            await interaction.response.send_message(
                t(locale, "language-select-prompt"),
                view=LanguageView(service.store, interaction.user.id, locale),
                ephemeral=True,
            )
            await service.store.remember_interaction(interaction)
            return
        try:
            flow = service.registry.require(self.kind)
            check_opening(service.settings, interaction.user)
            if flow.panel is None:
                raise CaseError()
        except CaseError as error:
            await interaction.response.send_message(
                error_text(locale, error), ephemeral=True
            )
            return
        context = PanelContext(service, interaction, self.kind, locale)
        if flow.panel.modal_factory:
            await interaction.response.send_modal(flow.panel.modal_factory(context))
            await service.store.remember_interaction(interaction)
        elif flow.panel.handler:
            await interaction.response.defer(ephemeral=True, thinking=True)
            context.locale = await service.store.remember_interaction(interaction)
            try:
                await flow.panel.handler(context)
            except Exception as error:  # noqa: BLE001 -- shown to the user; unexpected ones are logged
                if not isinstance(error, ValueError):
                    log_failure(logger, self.kind, "Panel entry %s failed", self.kind)
                await interaction.followup.send(
                    error_text(context.locale, error), ephemeral=True
                )


class LanguageSelect(discord.ui.Select):
    def __init__(self, store, requester_id: int, locale: str) -> None:
        super().__init__(
            placeholder=t(locale, "language-select-placeholder"),
            options=[
                discord.SelectOption(
                    label=t(locale, "language-name-" + code), value=code
                )
                for code in SUPPORTED
            ],
        )
        self.store, self.requester_id = store, requester_id

    async def callback(self, interaction: discord.Interaction) -> None:
        locale = self.store.interaction_locale_now(interaction)
        if interaction.user.id != self.requester_id:
            await interaction.response.send_message(
                t(locale, "error-not-allowed"), ephemeral=True
            )
            return
        await interaction.response.defer()
        selected = self.values[0]
        await self.store.set_locale_pref(interaction.user.id, selected, source="manual")
        await interaction.edit_original_response(
            content=t(
                selected,
                "language-changed",
                language=t(selected, "language-name-" + selected),
            ),
            view=None,
        )
        self.view.stop()


class LanguageView(discord.ui.View):
    def __init__(self, store, requester_id: int, locale: str) -> None:
        super().__init__(timeout=60)
        self.add_item(LanguageSelect(store, requester_id, locale))


def case_view(registry: FlowRegistry, case: Case, locale: str) -> discord.ui.View:
    """The buttons of a card, from the stored case alone."""
    view = discord.ui.View(timeout=None)
    if not registry.enabled(case.kind) or not case.actionable or case.pending_action:
        return view
    flow = registry.require(case.kind)
    if flow.takeable:
        verb = "take" if case.status == CaseStatus.OPEN else "release"
        view.add_item(
            CaseActionButton(case.number, verb, label=t(locale, "cases-button-" + verb))
        )
    for action in flow.actions:
        if action.visible is None or action.visible(case):
            view.add_item(
                CaseActionButton(
                    case.number,
                    action.verb,
                    label=t(locale, action.label_key)[:80],
                    style=action.style,
                )
            )
    view.add_item(
        CaseActionButton(
            case.number,
            "reject",
            label=t(locale, "cases-button-reject"),
            style=discord.ButtonStyle.danger,
        )
    )
    return view


PANEL_ORDER = ("report", "appeal", "badge", "complaint")


def panel_flows(registry: FlowRegistry) -> list:
    """Active flows with a panel button, the most used first."""
    flows = [flow for flow in registry.active_flows() if flow.panel]
    return sorted(
        flows,
        key=lambda f: PANEL_ORDER.index(f.kind) if f.kind in PANEL_ORDER else 99,
    )


def panel_view(registry: FlowRegistry) -> discord.ui.View:
    """One message for everyone: emoji and an English word on every button (8.6)."""
    view = discord.ui.View(timeout=None)
    for flow in panel_flows(registry):
        view.add_item(
            PanelButton(
                flow.kind,
                label=t("en", flow.panel.label_key)[:80],
                emoji=flow.panel.emoji or "📝",
            )
        )
    if view.children:
        view.add_item(PanelButton(label=t("en", "cases-panel-language")))
    return view


# Follow-up steps: ordinary short-lived views, private to the person who pressed (8.3).


class FollowUpView(discord.ui.View):
    def __init__(self, requester_id: int, locale: str) -> None:
        super().__init__(timeout=FOLLOW_UP_SECONDS)
        self.requester_id, self.locale = requester_id, locale
        self.used = False

    async def interaction_check(self, interaction: discord.Interaction) -> bool:
        if interaction.user.id == self.requester_id and not self.used:
            return True
        await interaction.response.send_message(
            t(self.locale, "error-not-allowed"), ephemeral=True
        )
        return False

    def finish(self) -> None:
        self.used = True
        self.stop()


Chosen = Callable[[discord.Interaction, str], Awaitable[None]]


class ChoiceView(FollowUpView):
    """One select. ``on_choice(interaction, value)`` receives the unanswered interaction, so
    it may open a modal as well as defer."""

    def __init__(
        self,
        on_choice: Chosen | ActionStep,
        locale: str,
        options: Sequence[discord.SelectOption],
        *,
        requester_id: int | None = None,
    ) -> None:
        super().__init__(_requester(on_choice, requester_id), locale)
        self.on_choice = on_choice
        select = discord.ui.Select(
            placeholder=t(locale, "cases-choice-placeholder"),
            options=list(options)[:25],
        )
        select.callback = self._chosen
        self.select = select
        self.add_item(select)

    async def _chosen(self, interaction: discord.Interaction) -> None:
        self.finish()
        await self.on_choice(interaction, self.select.values[0])


class UserChoiceView(FollowUpView):
    def __init__(
        self,
        on_choice: ActionStep,
        locale: str,
    ) -> None:
        super().__init__(on_choice.requester_id, locale)
        self.on_choice = on_choice
        select = discord.ui.UserSelect(
            placeholder=t(locale, "cases-choice-placeholder")
        )
        select.callback = self._chosen
        self.select = select
        self.add_item(select)

    async def _chosen(self, interaction: discord.Interaction) -> None:
        self.finish()
        user = self.select.values[0]
        await self.on_choice(
            interaction,
            str(user.id),
            **{self.on_choice.name + "_name": user.display_name},
        )


class ConfirmView(FollowUpView):
    """Confirm / Cancel. ``on_confirm(interaction, "yes")`` runs on Confirm only."""

    def __init__(
        self,
        on_confirm: ActionStep,
        locale: str,
    ) -> None:
        super().__init__(on_confirm.requester_id, locale)
        self.on_confirm = on_confirm
        confirm = discord.ui.Button(
            label=t(locale, "button-confirm"),
            style=discord.ButtonStyle.danger,
        )
        cancel = discord.ui.Button(label=t(locale, "button-cancel"))
        confirm.callback = self._confirmed
        cancel.callback = self._cancelled
        self.add_item(confirm)
        self.add_item(cancel)

    async def _confirmed(self, interaction: discord.Interaction) -> None:
        self.finish()
        await self.on_confirm(interaction, "yes")

    async def _cancelled(self, interaction: discord.Interaction) -> None:
        self.finish()
        await interaction.response.edit_message(
            content=t(self.locale, "cases-cancelled"), view=None
        )


@dataclass(slots=True)
class ActionStep:
    """The next step of a card action: the answer joins ``values`` and the action is
    dispatched again from the start, so every step is checked against the stored case."""

    service: Any
    number: int
    verb: str
    requester_id: int
    name: str
    values: dict[str, str] = field(default_factory=dict)

    async def __call__(
        self, interaction: discord.Interaction, value: str, **extra: str
    ) -> None:
        await interaction.response.defer(ephemeral=True, thinking=True)
        locale = await self.service.store.remember_interaction(interaction)
        await run_case_action(
            self.service,
            interaction,
            self.number,
            self.verb,
            locale,
            {**self.values, self.name: value, **extra},
        )


@dataclass(slots=True)
class ActionContext:
    """What an action handler works with. The interaction is already deferred."""

    service: CaseService
    case: Case
    member: Any
    locale: str
    verb: str
    values: dict[str, str] = field(default_factory=dict)
    interaction: Any = None
    replied: bool = False

    @property
    def actor(self) -> Actor:
        return Actor(str(self.member.id), self.member.display_name)

    async def run(self, action: Callable[[Case], Awaitable[Resolution]]) -> Case:
        """Run a staff action through the pipeline of 8.3 and close the case with its result."""
        return await self.service.execute_action(
            self.case.number, self.verb, self.member, action
        )

    async def reply(self, text: str, *, view: discord.ui.View | None = None) -> None:
        """Answer the staff member yourself; the handler then returns ``None``."""
        self.replied = True
        kwargs = {"view": view} if view is not None else {}
        await self.interaction.followup.send(text, ephemeral=True, **kwargs)

    async def confirm(self, text: str, *, name: str = "confirmed") -> None:
        """Ask for a confirmation. The handler runs again with ``values[name] == "yes"``."""
        await self.reply(text, view=ConfirmView(self._step(name), self.locale))

    async def choose(
        self,
        text: str,
        name: str,
        options: Sequence[discord.SelectOption],
    ) -> None:
        """Ask for one of ``options``. The handler runs again with ``values[name]`` set."""
        await self.reply(text, view=ChoiceView(self._step(name), self.locale, options))

    async def choose_user(self, text: str, name: str) -> None:
        """Ask for a member. The handler runs again with ``values[name]`` (the id) and
        ``values[name + "_name"]`` (the display name) set."""
        await self.reply(text, view=UserChoiceView(self._step(name), self.locale))

    def _step(self, name: str):
        return ActionStep(
            service=self.service,
            number=self.case.number,
            verb=self.verb,
            requester_id=int(self.member.id),
            values=dict(self.values),
            name=name,
        )


def _requester(callback, requester_id: int | None) -> int:
    if requester_id is None:
        requester_id = getattr(callback, "requester_id", None)
    if requester_id is None:
        raise ValueError("A follow-up view needs the id of its user")
    return requester_id
