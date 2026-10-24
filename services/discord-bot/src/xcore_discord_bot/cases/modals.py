from __future__ import annotations

import logging
from collections.abc import Awaitable, Callable, Sequence

import discord

from ..i18n import t
from ._privacy import log_failure
from .access import check_opening
from .model import AccessDenied, ActionFailed, Case, CaseError, Cooldown
from .registry import FormField, PanelContext

NOTE_FIELDS = (FormField("note", "cases-note", paragraph=True),)
MODAL_SECONDS = 900
logger = logging.getLogger(__name__)
ACTION_ERROR_KEYS = {
    "MissingPlayerIdentifiers": "cases-error-player",
    "InvalidDuration": "cases-error-duration",
    "BanNotFound": "cases-error-ban",
    "InvalidBanExpiry": "cases-error-expiry",
    "BadgeNotFound": "cases-error-badge",
    "BadgeNotGrantable": "cases-error-badge",
    "ActionAlreadyProcessed": "error-already-handled",
    "InvalidMapUpload": "cases-error-map",
    "MapUploadTimeout": "cases-error-map-unconfirmed",
    "MapUploadFailed": "cases-error-map-unconfirmed",
    "NoLiveServerError": "cases-error-no-server",
    "TimeoutError": "cases-error-timeout",
    "RpcRejected": "cases-error-server",
    "RpcFailed": "cases-error-server",
}


def error_text(locale: str, error: Exception) -> str:
    """The localised explanation of a refusal or a failed action."""
    if isinstance(error, ActionFailed):
        return error.text
    if isinstance(error, Cooldown):
        return t(locale, error.key, seconds=error.seconds)
    return t(
        locale,
        error.key
        if isinstance(error, CaseError)
        else ACTION_ERROR_KEYS.get(type(error).__name__, "cases-action-failed"),
    )


class FormModal(discord.ui.Modal):
    """A modal built from ``FormField``s: text inputs and selects, each under its label."""

    def __init__(
        self, *, title: str, locale: str, fields: Sequence[FormField], files: bool
    ) -> None:
        super().__init__(title=title[:45], timeout=MODAL_SECONDS)
        if len(fields) + int(files) > 5:
            raise ValueError("A modal can contain at most five components")
        self.inputs: dict[str, discord.ui.TextInput | discord.ui.Select] = {}
        for spec in fields:
            if spec.choices:
                item = discord.ui.Select(
                    required=spec.required,
                    options=[
                        discord.SelectOption(
                            label=t(locale, choice.label_key)[:100],
                            value=choice.value,
                            default=choice.value == spec.default,
                        )
                        for choice in spec.choices
                    ],
                )
            else:
                item = discord.ui.TextInput(
                    required=spec.required,
                    style=discord.TextStyle.paragraph
                    if spec.paragraph
                    else discord.TextStyle.short,
                    max_length=spec.max_length,
                    default=(spec.default or "")[: spec.max_length] or None,
                )
            self.inputs[spec.name] = item
            self.add_item(
                discord.ui.Label(text=t(locale, spec.label_key)[:45], component=item)
            )
        # ``min_values`` is explicit: an optional upload must not default to "at least one".
        self.files = (
            discord.ui.FileUpload(required=False, min_values=0, max_values=5)
            if files
            else None
        )
        if self.files:
            self.add_item(
                discord.ui.Label(
                    text=t(locale, "cases-files")[:45], component=self.files
                )
            )

    def values(self) -> dict[str, str]:
        return {
            name: (item.values[0] if item.values else "")
            if isinstance(item, discord.ui.Select)
            else str(item.value or "").strip()
            for name, item in self.inputs.items()
        }


class OpenCaseModal(FormModal):
    """The form behind a panel button.

    The flow supplies ``submit(context, values) -> Case`` (validation, lookups,
    ``context.open(...)``); this wrapper owns the defer, the access checks, copying the
    uploaded files into the case post and the reply.
    """

    def __init__(
        self,
        context: PanelContext,
        *,
        title_key: str,
        fields: Sequence[FormField],
        submit: Callable[[PanelContext, dict[str, str]], Awaitable[Case]],
        files: bool = False,
    ) -> None:
        super().__init__(
            title=t(context.locale, title_key),
            locale=context.locale,
            fields=fields,
            files=files,
        )
        self.context, self.submit = context, submit
        self.requester_id = context.interaction.user.id

    async def on_submit(self, interaction: discord.Interaction) -> None:
        await interaction.response.defer(ephemeral=True, thinking=True)
        service = self.context.service
        locale = await service.store.remember_interaction(interaction)
        try:
            if interaction.user.id != self.requester_id:
                raise AccessDenied()
            service.registry.require(self.context.kind)
            check_opening(service.settings, interaction.user)
            context = PanelContext(service, interaction, self.context.kind, locale)
            case = await self.submit(context, self.values())
        except Exception as error:
            if not isinstance(error, ValueError):
                log_failure(
                    logger,
                    self.context.kind,
                    "Opening a %s case failed",
                    self.context.kind,
                )
            await interaction.followup.send(error_text(locale, error), ephemeral=True)
            return
        # From here the case exists: whatever fails below, the opener gets the number (8.8).
        content = t(locale, "cases-registered", number=case.number)
        uploads = self.files.values if self.files else []
        if uploads:
            try:
                if not case.discord.get("thread_id"):
                    raise LookupError("The case post does not exist yet")
                await service.threads.upload_attachments(service, case.number, uploads)
            except Exception:
                log_failure(
                    logger, case.kind, "Could not copy files for case #%s", case.number
                )
                content += "\n" + t(locale, "cases-files-retry")
        # The submitter's own thread: on a merged report that is not the opener's.
        thread_id = case.user_threads.get(str(interaction.user.id))
        if thread_id:
            content += f"\n<#{thread_id}>"
        await interaction.followup.send(content, ephemeral=True)
