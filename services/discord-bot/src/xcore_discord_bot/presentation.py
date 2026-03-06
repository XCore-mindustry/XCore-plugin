from __future__ import annotations

from datetime import datetime, timezone
from typing import Literal

from bson.datetime_ms import DatetimeMS
import discord

DISCORD_EMBED_TITLE_MAX = 256

HEXED_RANKS: list[dict[str, str | int]] = [
    {"name": "Newbie", "tag": "", "required": 0},
    {"name": "Regular", "tag": "\uf7e7", "required": 3},
    {"name": "Advanced", "tag": "\uf7ed", "required": 10},
    {"name": "Veteran", "tag": "\uf7ec", "required": 20},
    {"name": "Devastator", "tag": "\uf7c4", "required": 25},
    {"name": "The Legend", "tag": "\uf7c6", "required": 30},
]


def format_size(size_bytes: int) -> str:
    if size_bytes < 1024:
        return f"{size_bytes} B"
    if size_bytes < 1024 * 1024:
        return f"{size_bytes / 1024:.1f} KB"
    return f"{size_bytes / (1024 * 1024):.1f} MB"


def format_minutes(total_minutes: int) -> str:
    if total_minutes < 60:
        return f"{total_minutes}m"
    hours = total_minutes // 60
    minutes = total_minutes % 60
    if hours < 24:
        return f"{hours}h {minutes}m"
    days = hours // 24
    rem_hours = hours % 24
    return f"{days}d {rem_hours}h {minutes}m"


def format_epoch_millis(value: object) -> str:
    if isinstance(value, (int, float)) and value > 0:
        dt = datetime.fromtimestamp(float(value) / 1000.0, tz=timezone.utc)
        return dt.strftime("%Y-%m-%d %H:%M UTC")
    return "n/a"


def as_int(value: object, default: int = 0) -> int:
    if isinstance(value, bool):
        return default
    if isinstance(value, int):
        return value
    if isinstance(value, float):
        return int(value)
    if isinstance(value, str):
        normalized = value.strip()
        if normalized and normalized.lstrip("-").isdigit():
            return int(normalized)
    return default


def format_hexed_rank_block(rank_value: int, points: int) -> tuple[str, str]:
    safe_rank = max(0, min(rank_value, len(HEXED_RANKS) - 1))
    current = HEXED_RANKS[safe_rank]

    rank_name = str(current["name"])
    rank_tag = str(current["tag"])
    rank_label = f"{rank_tag} {rank_name}" if rank_tag else rank_name

    if safe_rank + 1 < len(HEXED_RANKS):
        next_required = int(HEXED_RANKS[safe_rank + 1]["required"])
        rank_progress = f"{points}/{next_required} wins"
    else:
        rank_progress = f"{points} wins (max rank)"

    return rank_label, rank_progress


def build_stats_title(nickname: str, custom_nickname: str) -> str:
    base = f"Player Stats • {nickname}"
    if custom_nickname:
        base = f"{base} ({custom_nickname})"
    if len(base) <= DISCORD_EMBED_TITLE_MAX:
        return base
    return f"{base[: DISCORD_EMBED_TITLE_MAX - 3]}..."


def format_ban_expire_date(expire_value: object) -> str:
    if isinstance(expire_value, datetime):
        expire_dt = (
            expire_value.replace(tzinfo=timezone.utc)
            if expire_value.tzinfo is None
            else expire_value
        )
        return (
            f"{discord.utils.format_dt(expire_dt, style='f')} "
            f"({discord.utils.format_dt(expire_dt, style='R')})"
        )
    if isinstance(expire_value, DatetimeMS):
        return format_ban_expire_date_from_millis(int(expire_value))
    if isinstance(expire_value, int):
        return format_ban_expire_date_from_millis(expire_value)
    return "Unknown"


def format_ban_expire_date_from_millis(millis: int) -> str:
    min_millis = int(datetime(1, 1, 1, tzinfo=timezone.utc).timestamp() * 1000)
    max_millis = int(
        datetime(9999, 12, 31, 23, 59, 59, tzinfo=timezone.utc).timestamp() * 1000
    )
    if millis < min_millis:
        return "Before year 1"
    if millis > max_millis:
        return "After year 9999"
    expire_dt = datetime.fromtimestamp(millis / 1000.0, tz=timezone.utc)
    return (
        f"{discord.utils.format_dt(expire_dt, style='f')} "
        f"({discord.utils.format_dt(expire_dt, style='R')})"
    )


def build_servers_embed(
    servers, *, sort_mode: Literal["players", "name"]
) -> discord.Embed:
    embed = discord.Embed(title="Live Servers", color=0x00FF00)
    if not servers:
        embed.description = "No live servers connected right now."
    for srv in servers:
        value = f"👥 `{srv.players}/{srv.max_players}`\n📦 `{srv.version}`\n💬 <#{srv.channel_id}>"
        if srv.host and isinstance(srv.port, int) and srv.port > 0:
            value += f"\n🔌 Address: `{srv.host}:{srv.port}`"
        embed.add_field(name=srv.name, value=value, inline=True)

    total_players = sum(srv.players for srv in servers)
    embed.set_footer(
        text=(
            f"Sort: {sort_mode} • Servers: {len(servers)} "
            f"• Players online: {total_players}"
        )
    )
    return embed
