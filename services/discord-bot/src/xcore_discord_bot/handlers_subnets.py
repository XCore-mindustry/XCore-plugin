from __future__ import annotations

import ipaddress
import re
import time
from collections.abc import Iterable
from typing import TYPE_CHECKING, Any

from discord import Interaction

from .registry import server_registry

if TYPE_CHECKING:
    from .bot import XCoreDiscordBot

_MAX_IMPORT_TEXT = 4_000
_MAX_IMPORT_RULES = 256
_MESSAGE_LIMIT = 1_900


def _timeout(bot: XCoreDiscordBot) -> int:
    return int(getattr(getattr(bot, "settings", None), "rpc_timeout_ms", 5000))


def _value(response: Any, name: str, default: Any = None) -> Any:
    return getattr(response, name, default)


def _target(server: str) -> str:
    value = server.strip()
    if not value or len(value) > 128:
        raise ValueError("Server name is required and must be at most 128 characters.")
    return value


def _cidr(value: str) -> str:
    try:
        return str(ipaddress.ip_network(value.strip(), strict=False))
    except ValueError as exc:
        raise ValueError(f"Invalid CIDR: `{value.strip()}`") from exc


def _ip(value: str) -> str:
    try:
        return str(ipaddress.ip_address(value.strip()))
    except ValueError as exc:
        raise ValueError(f"Invalid IP address: `{value.strip()}`") from exc


def parse_import(text: str) -> list[str]:
    if len(text) > _MAX_IMPORT_TEXT:
        raise ValueError(f"Import text is too large (maximum {_MAX_IMPORT_TEXT} characters).")
    result: list[str] = []
    seen: set[str] = set()
    for item in text.replace(",", "\n").splitlines():
        item = item.strip()
        if not item:
            continue
        normalized = _cidr(item)
        if normalized not in seen:
            seen.add(normalized)
            result.append(normalized)
    if not result:
        raise ValueError("Import text contains no CIDR networks.")
    if len(result) > _MAX_IMPORT_RULES:
        raise ValueError(f"Too many networks (maximum {_MAX_IMPORT_RULES}).")
    return result


async def _defer(interaction: Interaction) -> None:
    if not interaction.response.is_done():
        await interaction.response.defer(ephemeral=True)


async def _send(interaction: Interaction, message: str) -> None:
    if interaction.response.is_done():
        await interaction.followup.send(message, ephemeral=True)
    else:
        await interaction.response.send_message(message, ephemeral=True)


def _actor(interaction: Interaction) -> tuple[str, str]:
    user = interaction.user
    return str(user.id), str(getattr(user, "display_name", user))


def _control_server(bot: XCoreDiscordBot) -> str:
    del bot
    servers = sorted(s.name for s in server_registry.get_all_servers())
    if not servers:
        raise ValueError("No online server is available as subnet control server.")
    return servers[0]


def parse_expiration(value: str | None) -> int | None:
    if value is None or not value.strip() or value.strip().lower() in {"never", "0"}:
        return None
    match = re.fullmatch(r"(\d+)([smhdwy])", value.strip().lower())
    if match is None or int(match.group(1)) <= 0:
        raise ValueError("Invalid expiration. Use 30m, 2h, 7d, 1w, 1y or never.")
    seconds = int(match.group(1)) * {"s": 1, "m": 60, "h": 3600, "d": 86400, "w": 604800, "y": 31536000}[match.group(2)]
    return int(time.time() * 1000) + seconds * 1000


async def _command(
    bot: XCoreDiscordBot,
    interaction: Interaction,
    operation: str,
    server: str | None,
    rules: Iterable[str] = (),
    reason: str | None = None,
    expires_at: int | None = None,
) -> None:
    target = _target(server) if server is not None else _control_server(bot)
    normalized_rules = [_cidr(rule) for rule in rules]
    discord_id, username = _actor(interaction)
    await _defer(interaction)
    response = await bot.rpc_subnet_rules_command(
        operation=operation,
        rules=normalized_rules,
        discord_id=discord_id,
        discord_username=username,
        target_server=target,
        source="discord",
        reason=reason.strip() if reason and reason.strip() else None,
        timeout_ms=_timeout(bot),
        expires_at=expires_at,
    )
    if not _value(response, "success", False):
        await _send(interaction, f"Subnet `{operation.lower()}` failed: {_value(response, 'error', 'unknown error')}")
        return
    await _send(interaction, f"Subnet `{operation.lower()}` completed for `{target}`.")


async def cmd_subnet_list(bot: XCoreDiscordBot, interaction: Interaction) -> None:
    target = _control_server(bot)
    await _defer(interaction)
    response = await bot.rpc_subnet_rules_list(target, _timeout(bot))
    rules = tuple(_value(response, "rules", ()) or ())
    lines = [f"`{rule}`" for rule in rules]
    if not lines:
        await _send(interaction, f"No subnet rules for `{target}`.")
        return
    chunks: list[str] = []
    current = f"Subnet rules for `{target}` ({len(lines)}):\n"
    for line in lines:
        if len(current) + len(line) + 1 > _MESSAGE_LIMIT:
            chunks.append(current.rstrip())
            current = ""
        current += line + "\n"
    if current.strip():
        chunks.append(current.rstrip())
    for chunk in chunks:
        await _send(interaction, chunk)


async def cmd_subnet_check(bot: XCoreDiscordBot, interaction: Interaction, server: str, ip: str) -> None:
    target, address = _target(server), _ip(ip)
    await _defer(interaction)
    response = await bot.rpc_subnet_rules_check(target, address, _timeout(bot))
    allowed = bool(_value(response, "allowed", False))
    matched = tuple(_value(response, "matchedRules", _value(response, "matched_rules", ())) or ())
    suffix = f"; matched: {', '.join(matched)}" if matched else ""
    await _send(interaction, f"`{address}` is **{'allowed' if allowed else 'denied'}** on `{target}`{suffix}.")


async def cmd_subnet_allow(bot: XCoreDiscordBot, interaction: Interaction, cidr: str, reason: str | None = None, expires: str | None = None) -> None:
    await _command(bot, interaction, "ALLOW", None, [cidr], reason, parse_expiration(expires))


async def cmd_subnet_deny(bot: XCoreDiscordBot, interaction: Interaction, cidr: str, reason: str | None = None, expires: str | None = None) -> None:
    await _command(bot, interaction, "DENY", None, [cidr], reason, parse_expiration(expires))


async def cmd_subnet_remove(bot: XCoreDiscordBot, interaction: Interaction, cidr: str) -> None:
    await _command(bot, interaction, "REMOVE", None, [cidr])


async def cmd_subnet_reload(bot: XCoreDiscordBot, interaction: Interaction, server: str) -> None:
    await _command(bot, interaction, "RELOAD", server)


async def cmd_subnet_import(bot: XCoreDiscordBot, interaction: Interaction, text: str, expires: str | None = None) -> None:
    await _command(bot, interaction, "IMPORT", None, parse_import(text), expires_at=parse_expiration(expires))


async def cmd_subnet_sweep(bot: XCoreDiscordBot, interaction: Interaction, server: str, cluster: bool = False) -> None:
    del cluster
    await _send(interaction, "Subnet sweep is not supported by protocol 0.6.0 (use reload).")


async def safe_handler(handler: Any, *args: Any) -> None:
    interaction = args[1]
    try:
        await handler(*args)
    except (TimeoutError, RuntimeError) as exc:
        await _send(interaction, f"Subnet operation failed: {exc or 'request timed out'}")
    except ValueError as exc:
        await _send(interaction, str(exc))
