from __future__ import annotations

import re
from dataclasses import dataclass

from .model import CaseKind

CASE_PATTERN = r"xc:1:case:(?P<verb>[a-z][a-z0-9_]{0,31}):(?P<number>[1-9][0-9]{0,18})"
PANEL_PATTERN = (
    r"xc:1:panel:(?:open:(?P<kind>report|appeal|badge|complaint)|(?P<lang>lang))"
)
_VERB = re.compile(r"[a-z][a-z0-9_]{0,31}")
_CASE = re.compile(r"#?([1-9][0-9]*)")
MAX_NUMBER = 2**63 - 1


def _number(number: int) -> int:
    if (
        isinstance(number, bool)
        or not isinstance(number, int)
        or not 1 <= number <= MAX_NUMBER
    ):
        raise ValueError("Invalid positive identifier")
    return number


def parse_case_number(value: str) -> int:
    match = _CASE.fullmatch(value.strip())
    if match is None:
        raise ValueError("Invalid case number")
    return _number(int(match[1]))


def format_case_number(number: int) -> str:
    return f"#{_number(number)}"


@dataclass(frozen=True, slots=True)
class CustomId:
    target: str
    verb: str
    number: int | None = None
    kind: CaseKind | None = None


def case_custom_id(verb: str, number: int) -> str:
    if _VERB.fullmatch(verb) is None:
        raise ValueError("Invalid case verb")
    return f"xc:1:case:{verb}:{_number(number)}"


def panel_custom_id(kind: CaseKind | None = None) -> str:
    if kind is None:
        return "xc:1:panel:lang"
    kind = CaseKind(kind)
    if kind == CaseKind.MAP:
        raise ValueError("Maps are submitted in the forum")
    return f"xc:1:panel:open:{kind.value}"


def decode_custom_id(value: str) -> CustomId:
    if len(value) > 100:
        raise ValueError("Custom id is too long")
    if match := re.fullmatch(CASE_PATTERN, value):
        return CustomId("case", match["verb"], _number(int(match["number"])))
    if match := re.fullmatch(PANEL_PATTERN, value):
        return CustomId(
            "panel",
            "open" if match["kind"] else "lang",
            kind=CaseKind(match["kind"]) if match["kind"] else None,
        )
    raise ValueError("Malformed case custom id")
