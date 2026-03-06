from __future__ import annotations

from collections.abc import Iterable
from discord import Interaction, app_commands

from .client_protocols import SupportsSettings
from .settings import Settings


def role_mention(role_id: int | None) -> str:
    if role_id is None:
        return "configured role"
    return f"<@&{role_id}>"


def member_role_ids(user: object) -> set[int]:
    roles = getattr(user, "roles", None)
    if roles is None:
        return set()

    result: set[int] = set()
    for role in roles:
        role_id = getattr(role, "id", None)
        if isinstance(role_id, int):
            result.add(role_id)
    return result


def settings_from_interaction(interaction: Interaction) -> Settings | None:
    client = interaction.client
    if not isinstance(client, SupportsSettings):
        return None
    return client.settings


def has_any_role(user: object, role_ids: Iterable[int | None]) -> bool:
    member_roles = member_role_ids(user)
    return any(role_id in member_roles for role_id in role_ids if role_id is not None)


def admin_role_ids(settings: Settings) -> tuple[int, ...]:
    return (settings.discord_admin_role_id,)


def general_admin_role_ids(settings: Settings) -> tuple[int, ...]:
    general_role = (
        settings.discord_general_admin_role_id
        if settings.discord_general_admin_role_id is not None
        else settings.discord_admin_role_id
    )
    return (settings.discord_admin_role_id, general_role)


def map_reviewer_role_ids(settings: Settings) -> tuple[int, ...]:
    reviewer_role = (
        settings.discord_map_reviewer_role_id
        if settings.discord_map_reviewer_role_id is not None
        else settings.discord_admin_role_id
    )
    return (reviewer_role,)


def require_any_role(
    interaction: Interaction,
    *,
    role_ids: Iterable[int | None],
    message: str,
) -> bool:
    if has_any_role(interaction.user, role_ids):
        return True
    raise app_commands.CheckFailure(message)


async def ensure_any_role(
    interaction: Interaction,
    *,
    role_ids: Iterable[int | None],
    denied_message: str,
) -> bool:
    if has_any_role(interaction.user, role_ids):
        return True
    await interaction.response.send_message(denied_message, ephemeral=True)
    return False
