"""The rows of the map list, as /map list shows them."""

from __future__ import annotations

import re

import discord

from .presentation import format_size
from .utils.mindustry_colors import strip_mindustry_colors


def _map_rating_part(item: dict[str, str]) -> str:
    like = str(item.get("like", "")).strip()
    dislike = str(item.get("dislike", "")).strip()
    reputation = str(item.get("reputation", "")).strip()

    parts: list[str] = []
    if like.lstrip("-").isdigit() or dislike.lstrip("-").isdigit():
        like_value = like if like.lstrip("-").isdigit() else "0"
        dislike_value = dislike if dislike.lstrip("-").isdigit() else "0"
        parts.append(f"👍 {like_value}")
        parts.append(f"👎 {dislike_value}")

    if reputation.lstrip("-").isdigit():
        parts.append(f"rep {reputation}")

    return f" • {' • '.join(parts)}" if parts else ""


def _map_int_value(item: dict[str, str], key: str) -> int:
    value = str(item.get(key, "")).strip()
    return int(value) if value.lstrip("-").isdigit() else -(10**9)


def _map_float_value(item: dict[str, str], key: str) -> float:
    value = str(item.get(key, "")).strip()
    try:
        return float(value)
    except ValueError:
        return float("-inf")


def _sort_maps(maps: list[dict[str, str]], mode: str) -> list[dict[str, str]]:
    if mode == "name":
        return sorted(maps, key=lambda item: str(item.get("name", "")).lower())
    if mode == "popularity":
        return sorted(
            maps,
            key=lambda item: (
                -_map_float_value(item, "popularity"),
                -_map_int_value(item, "reputation"),
                str(item.get("name", "")).lower(),
            ),
        )
    return sorted(
        maps,
        key=lambda item: (
            -_map_int_value(item, "reputation"),
            -_map_int_value(item, "like"),
            str(item.get("name", "")).lower(),
        ),
    )


def map_text(value: str, limit: int = 80, *, inline_code: bool = False) -> str:
    text = " ".join(str(value).split()).replace("`", "'")[:limit]
    text = discord.utils.escape_mentions(text)
    return text if inline_code else discord.utils.escape_markdown(text)


def map_contributor(item: dict[str, str], prefix: str) -> str:
    discord_id = item.get(f"{prefix}_discord_id", "")
    name = map_text(
        item.get(f"{prefix}_name", "") or item.get(f"{prefix}_handle", "") or discord_id
    )
    name = re.sub(r"([\[\]()])", r"\\\1", name)
    if discord_id.isascii() and discord_id.isdigit():
        return f"[{name}](https://discord.com/users/{discord_id})"
    return name


def map_list_row(item: dict[str, str]) -> str:
    name = map_text(strip_mindustry_colors(item.get("name", "Unknown")))
    file_name = map_text(item.get("file_name", ""), 100, inline_code=True)
    author = map_text(
        strip_mindustry_colors(item.get("author", "Unknown")), inline_code=True
    )
    width = str(item.get("width", "")).strip()
    height = str(item.get("height", "")).strip()
    file_size = str(item.get("file_size_bytes", "")).strip()
    details = f" • {width}x{height}" if width.isdigit() and height.isdigit() else ""
    if file_size.isdigit():
        details += f" • {format_size(int(file_size))}"
    details += _map_rating_part(item)
    uploaded = bool(item.get("uploaded_by_discord_id"))
    author_label = "file author" if uploaded else "by"
    row = f"• {name} — {author_label} `{author}`"
    row += f"\n  `{file_name}`{details}" if file_name else details
    if uploaded:
        credits = []
        if item.get("creator_discord_id"):
            credits.append(f"🎨 {map_contributor(item, 'creator')}")
        credits.append(f"⬆ {map_contributor(item, 'uploaded_by')}")
        row += "\n  " + " • ".join(credits)
        uploaded_at = item.get("uploaded_at", "")
        if uploaded_at.isascii() and uploaded_at.isdigit():
            row += f" • <t:{int(uploaded_at) // 1000}:d>"
    return row


def map_list_pages(maps: list[dict[str, str]], sort_mode: str) -> list[list[str]]:
    pages: list[list[str]] = [[]]
    length = 0
    for item in _sort_maps(maps, sort_mode):
        row = map_list_row(item)
        row_length = len(row.encode("utf-16-le")) // 2
        if pages[-1] and (len(pages[-1]) >= 15 or length + row_length + 1 > 3800):
            pages.append([])
            length = 0
        pages[-1].append(row)
        length += row_length + 1
    return pages
