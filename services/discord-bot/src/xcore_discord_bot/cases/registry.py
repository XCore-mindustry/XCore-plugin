from __future__ import annotations

import logging
from collections.abc import Awaitable, Callable, Sequence
from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Any

import discord

from ..staff_actions import Actor
from ._privacy import log_failure
from .ids import case_custom_id
from .model import AccessDenied, Case, CaseKind, FlowDisabled, InvalidForm, Resolution

if TYPE_CHECKING:
    from .notify import Outcome
    from .service import CaseService

logger = logging.getLogger(__name__)
FLAG_FIELDS = {
    CaseKind.REPORT: "cases_reports_enabled",
    CaseKind.APPEAL: "cases_appeals_enabled",
    CaseKind.BADGE: "cases_badges_enabled",
    CaseKind.COMPLAINT: "cases_complaints_enabled",
    CaseKind.MAP: "cases_maps_enabled",
}
# Verbs the core implements itself; a flow cannot register an action under these names.
CORE_VERBS = frozenset({"take", "release", "reject", "reopen"})
REJECT_OTHER = "other"


@dataclass(frozen=True, slots=True)
class Choice:
    value: str
    label_key: str


@dataclass(frozen=True, slots=True)
class FormField:
    """One modal component: a text input, or a select when ``choices`` is given."""

    name: str
    label_key: str
    required: bool = True
    paragraph: bool = False
    max_length: int = 1000
    default: str | None = None
    choices: tuple[Choice, ...] = ()

    def __post_init__(self) -> None:
        if not self.name or not 1 <= self.max_length <= 4000:
            raise ValueError("Invalid modal field")
        if len(self.choices) > 25:
            raise ValueError("A select can contain at most 25 options")


FormBuilder = Callable[[Case, str], Sequence[FormField]]
REJECT_TEXT = FormField(
    "reason_text", "cases-reject-details", required=False, paragraph=True
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
        from .views import ConfirmView

        await self.reply(text, view=ConfirmView(self._step(name), self.locale))

    async def choose(
        self,
        text: str,
        name: str,
        options: Sequence[discord.SelectOption],
    ) -> None:
        """Ask for one of ``options``. The handler runs again with ``values[name]`` set."""
        from .views import ChoiceView

        await self.reply(text, view=ChoiceView(self._step(name), self.locale, options))

    async def choose_user(self, text: str, name: str) -> None:
        """Ask for a member. The handler runs again with ``values[name]`` (the id) and
        ``values[name + "_name"]`` (the display name) set."""
        from .views import UserChoiceView

        await self.reply(text, view=UserChoiceView(self._step(name), self.locale))

    def _step(self, name: str):
        from .views import ActionStep

        return ActionStep(
            service=self.service,
            number=self.case.number,
            verb=self.verb,
            requester_id=int(self.member.id),
            values=dict(self.values),
            name=name,
        )


@dataclass(slots=True)
class PanelContext:
    service: CaseService
    interaction: Any
    kind: CaseKind
    locale: str

    async def open(
        self,
        *,
        opener,
        subject=None,
        payload=None,
        evidence=None,
        dedup_key=None,
        flags=(),
    ) -> Case:
        from .model import Origin

        return await self.service.open(
            kind=self.kind,
            origin=Origin("discord", f"discord:{self.interaction.id}", self.locale),
            opener=opener,
            subject=subject,
            payload=payload,
            evidence=evidence,
            dedup_key=dedup_key,
            flags=flags,
            member=self.interaction.user,
        )


@dataclass(frozen=True, slots=True)
class Action:
    """A button on the card.

    ``handler`` returns the updated ``Case`` (generic confirmation), a ready localised ``str``
    (shown as is), or ``None`` after answering through the context itself.
    ``fields`` is the form the button opens first: a tuple, or ``(case, locale) -> fields``
    when it depends on the case (pre-filled values, an extra field for an unresolved subject).
    """

    verb: str
    label_key: str
    handler: Callable[[ActionContext], Awaitable[Case | str | None]]
    style: discord.ButtonStyle = discord.ButtonStyle.secondary
    fields: tuple[FormField, ...] | FormBuilder = ()
    visible: Callable[[Case], bool] | None = None

    def form(self, case: Case, locale: str) -> tuple[FormField, ...]:
        fields = (
            tuple(self.fields(case, locale)) if callable(self.fields) else self.fields
        )
        if len(fields) > 5:
            raise ValueError("A modal can contain at most five fields")
        return fields


@dataclass(frozen=True, slots=True)
class PanelEntry:
    """A button on the public panel.

    ``modal_factory`` builds the form sent as the first response (no I/O before it).
    ``handler`` is for entries that must look something up first: it runs after a defer and
    continues through an ephemeral follow-up (``ChoiceView``), which can then open a modal.
    """

    label_key: str
    emoji: str | None = None
    modal_factory: Callable[[PanelContext], discord.ui.Modal] | None = None
    handler: Callable[[PanelContext], Awaitable[None]] | None = None


@dataclass(frozen=True, slots=True)
class Flow:
    kind: CaseKind
    card_builder: Callable[[Case, str], discord.Embed]
    panel: PanelEntry | None = None
    actions: tuple[Action, ...] = ()
    # The core adds Take / Release and Reject around the flow's own buttons.
    takeable: bool = True
    reject_reasons: tuple[Choice, ...] = ()
    on_thread_create: Callable[[CaseService, Any], Awaitable[None]] | None = None
    on_message: Callable[[CaseService, Any], Awaitable[None]] | None = None
    on_thread_delete: Callable[[CaseService, Any], Awaitable[None]] | None = None
    on_created: Callable[[CaseService, Case], Awaitable[None]] | None = None
    on_merged: Callable[[CaseService, Case, dict[str, Any]], Awaitable[None]] | None = (
        None
    )
    on_subject_set: Callable[[CaseService, Case], Awaitable[None]] | None = None
    on_closed: Callable[[CaseService, Case], Awaitable[None]] | None = None
    outcomes: Callable[[Case], Sequence[Outcome]] | None = None
    reconcile: Callable[[CaseService, Case], Awaitable[None]] | None = None
    # Also call ``reconcile`` for every open case that has no action in progress.
    reconcile_open: bool = False
    event_hooks: dict[str, Callable[[CaseService, Any], Awaitable[None]]] = field(
        default_factory=dict
    )

    def reject_form(self) -> tuple[FormField, ...]:
        if not self.reject_reasons:
            return (FormField("reason_text", "cases-reason", paragraph=True),)
        return (
            FormField("reason_code", "cases-reason", choices=self.reject_reasons),
            REJECT_TEXT,
        )


class FlowRegistry:
    def __init__(self) -> None:
        self._flows: dict[CaseKind, Flow] = {}
        self._enabled: set[CaseKind] = set()
        self.disabled_reasons: dict[CaseKind, tuple[str, ...]] = {}

    def register(self, flow: Flow) -> Flow:
        if flow.kind in self._flows:
            raise ValueError(f"Flow already registered: {flow.kind}")
        if flow.kind == CaseKind.MAP and flow.panel:
            raise ValueError("Maps have no panel entry")
        if len(flow.actions) > 23:
            raise ValueError("A case view can contain at most 25 buttons")
        if not 0 <= len(flow.reject_reasons) <= 25:
            raise ValueError("A select can contain at most 25 options")
        verbs: set[str] = set()
        for action in flow.actions:
            case_custom_id(action.verb, 1)
            if action.verb in verbs or action.verb in CORE_VERBS:
                raise ValueError(f"Reserved or duplicate verb: {action.verb}")
            verbs.add(action.verb)
            if not callable(action.fields) and len(action.fields) > 5:
                raise ValueError("A modal can contain at most five fields")
        self._flows[flow.kind] = flow
        return flow

    def copy(self) -> FlowRegistry:
        registry = FlowRegistry()
        registry._flows = dict(self._flows)
        return registry

    def configured(self, settings) -> tuple[Flow, ...]:
        return tuple(
            flow
            for kind, flow in self._flows.items()
            if getattr(settings, FLAG_FIELDS[kind])
        )

    def enable(self, kind: CaseKind) -> None:
        if kind not in self._flows:
            raise ValueError("No registered flow")
        self._enabled.add(kind)
        self.disabled_reasons.pop(kind, None)

    def disable(self, kind: CaseKind, reasons: Sequence[str] = ()) -> None:
        self._enabled.discard(kind)
        self.disabled_reasons[kind] = tuple(reasons)

    def enabled(self, kind: CaseKind) -> bool:
        return kind in self._enabled

    def require(self, kind: CaseKind) -> Flow:
        if not self.enabled(kind):
            raise FlowDisabled()
        return self._flows[kind]

    def active_flows(self) -> tuple[Flow, ...]:
        return tuple(flow for kind, flow in self._flows.items() if self.enabled(kind))

    def action(self, kind: CaseKind, verb: str) -> Action | None:
        return next((a for a in self.require(kind).actions if a.verb == verb), None)

    def label_key(self, kind: CaseKind, verb: str) -> str:
        action = self.action(kind, verb)
        return action.label_key if action else "cases-button-" + verb

    def fields_for(self, case: Case, verb: str, locale: str) -> tuple[FormField, ...]:
        """The form a card button opens, or ``()``. Refuses a verb the card does not offer."""
        flow = self.require(case.kind)
        if verb in {"take", "release"}:
            if not flow.takeable:
                raise AccessDenied()
            return ()
        if verb == "reject":
            return flow.reject_form()
        action = self.action(case.kind, verb)
        if action is None or action.visible is not None and not action.visible(case):
            raise AccessDenied()
        return action.form(case, locale)

    async def dispatch(self, hook: str, service: CaseService, value: Any) -> None:
        if hook not in {"on_thread_create", "on_message", "on_thread_delete"}:
            raise ValueError("Unknown flow event")
        if not self._enabled:
            return
        try:
            if hook == "on_message":
                await service.threads.announce_user_message(service, value)
            elif hook == "on_thread_delete":
                await service.thread_deleted(value.thread_id, value.parent_id)
        except Exception:
            log_failure(logger, None, "Case core %s hook failed", hook)
        for flow in self.active_flows():
            callback = getattr(flow, hook)
            if callback:
                try:
                    await callback(service, value)
                except Exception:
                    log_failure(
                        logger, flow.kind, "Case %s hook failed for %s", hook, flow.kind
                    )

    async def dispatch_event(
        self, name: str, service: CaseService, payload: Any
    ) -> None:
        """The transport package calls this from its consumers. Errors propagate so the
        caller can retry or dead-letter the event."""
        for flow in self.active_flows():
            if callback := flow.event_hooks.get(name):
                await callback(service, payload)


def clean_values(
    fields: Sequence[FormField], values: dict[str, Any] | None
) -> dict[str, str]:
    """Submitted form values, checked against the form the case offers right now."""
    cleaned = {key: str(value) for key, value in (values or {}).items()}
    for spec in fields:
        value = cleaned.get(spec.name, "").strip()
        if spec.required and not value:
            raise InvalidForm()
        if value and spec.choices and value not in {c.value for c in spec.choices}:
            raise InvalidForm()
        cleaned[spec.name] = value[: spec.max_length]
    return cleaned


flow_registry = FlowRegistry()
