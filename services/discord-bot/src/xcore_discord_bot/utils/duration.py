from __future__ import annotations

import re
from datetime import timedelta


def parse_duration(token: str, default_unit: str = "d") -> timedelta:
    normalized = token.strip().lower()
    if not normalized:
        raise ValueError("Invalid period format. Use 10m, 1h, 1d, 1w, 1y")

    factors = {
        "s": 1,
        "m": 60,
        "h": 3600,
        "d": 86400,
        "w": 604800,
        "y": 31536000,
    }

    if default_unit not in factors:
        raise ValueError("Invalid default unit")

    if normalized.isdigit():
        return timedelta(seconds=int(normalized) * factors[default_unit])

    total_seconds = 0
    consumed = 0
    for match in re.finditer(r"(\d+)([smhdwy])", normalized):
        start, end = match.span()
        if start != consumed:
            raise ValueError("Invalid period format. Use 10m, 1h, 1d, 1w, 1y")
        value = int(match.group(1))
        unit = match.group(2)
        total_seconds += value * factors[unit]
        consumed = end

    if consumed != len(normalized) or total_seconds <= 0:
        raise ValueError("Invalid period format. Use 10m, 1h, 1d, 1w, 1y")

    return timedelta(seconds=total_seconds)
