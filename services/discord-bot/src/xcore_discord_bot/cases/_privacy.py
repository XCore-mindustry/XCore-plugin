"""Complaint errors must not print exception messages or tracebacks."""

from __future__ import annotations

import logging
import sys

from .ids import parse_case_number
from .model import CaseKind


def log_failure(
    logger: logging.Logger, kind: CaseKind | None, message: str, *args
) -> None:
    if kind == CaseKind.COMPLAINT or kind is None:
        error = sys.exc_info()[1]
        logger.error(message + " (%s)", *args, type(error).__name__)
    else:
        logger.exception(message, *args)


async def private_case_command(bot, interaction) -> bool:
    """Only the actual stored target can opt a cases failure into ordinary diagnostics."""
    extras = getattr(interaction, "extras", {})
    command = getattr(interaction, "command", None)
    name = getattr(command, "qualified_name", "")
    if "cases_command_number" not in extras and not (
        name == "cases" or name.startswith("cases ")
    ):
        return False
    try:
        number = extras.get("cases_command_number")
        if number is None:
            number = getattr(getattr(interaction, "namespace", None), "number", None)
        case = await bot.container.cases.get(parse_case_number(number))
    except Exception:
        return True  # No cached kind, fallback case or exception text is safe here.
    return case.kind == CaseKind.COMPLAINT
