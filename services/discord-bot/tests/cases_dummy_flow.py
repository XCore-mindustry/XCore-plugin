"""A complete foundation-only flow: it uses the report kind without implementing reports.

A panel form, one card action that goes through the action pipeline, and an outcome: the
smallest flow the core's tests can open, take, resolve and reject.
"""

from __future__ import annotations

from xcore_discord_bot.cases.cards import base_card
from xcore_discord_bot.cases.modals import NOTE_FIELDS, OpenCaseModal
from xcore_discord_bot.cases.model import CaseKind, Party, Resolution
from xcore_discord_bot.cases.notify import Outcome
from xcore_discord_bot.cases.registry import Action, Flow, FormField, PanelEntry


async def submit(context, values):
    return await context.open(
        opener=Party(
            discord_id=str(context.interaction.user.id),
            discord_name=context.interaction.user.display_name,
        ),
        subject=Party(
            player_uuid="subject-secret", player_pid=42, player_name="Example"
        ),
        payload={"statement": values["note"]},
    )


def modal(context):
    return OpenCaseModal(
        context,
        title_key="cases-kind-report",
        fields=(FormField("note", "cases-note", paragraph=True),),
        submit=submit,
    )


async def resolve(context):
    async def staff_action(case):
        # Real flows call perform_ban/mute/etc. here with context.actor and case.number.
        return Resolution("muted", reason_text=context.values["note"])

    return await context.run(staff_action)


def outcomes(case):
    return (
        Outcome(
            case.opener,
            "report_rejected"
            if case.resolution.code == "rejected"
            else "report_resolved",
            "cases-outcome",
            user_thread=True,
        ),
    )


DUMMY_FLOW = Flow(
    kind=CaseKind.REPORT,
    card_builder=base_card,
    panel=PanelEntry("cases-kind-report", emoji="📣", modal_factory=modal),
    actions=(Action("resolve", "cases-button-resolve", resolve, fields=NOTE_FIELDS),),
    outcomes=outcomes,
)
