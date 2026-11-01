"""Helpers for safe generated identifier naming."""

from __future__ import annotations

import re


_IDENTIFIER_PART_PATTERN = re.compile(r"[A-Za-z0-9]+")


def message_type_constant_name(message_type: str, message_version: int) -> str:
    """Convert a canonical message identifier into a safe constant name."""

    parts = _identifier_parts(message_type)
    normalized = "_".join(part.upper() for part in parts)
    return f"{normalized}_V{message_version}"


def _identifier_parts(identifier: str) -> tuple[str, ...]:
    parts = tuple(_IDENTIFIER_PART_PATTERN.findall(identifier))
    if not parts:
        raise ValueError(f"Identifier must contain at least one alphanumeric part: {identifier!r}")
    return parts
