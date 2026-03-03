from __future__ import annotations

from discord import Interaction, app_commands
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from ..settings import Settings


def _role_mention(role_id: int | None) -> str:
    if role_id is None:
        return "<@&0>"
    return f"<@&{role_id}>"


def _member_role_ids(user: object) -> set[int]:
    roles = getattr(user, "roles", None)
    if roles is None:
        return set()
    result: set[int] = set()
    for role in roles:
        role_id = getattr(role, "id", None)
        if isinstance(role_id, int):
            result.add(role_id)
    return result


def _settings_from_interaction(interaction: Interaction) -> "Settings | None":
    return getattr(interaction.client, "_settings", None)


def admin_check():
    async def predicate(interaction: Interaction) -> bool:
        settings = _settings_from_interaction(interaction)
        if settings is None:
            return False
        if settings.discord_admin_role_id in _member_role_ids(interaction.user):
            return True
        raise app_commands.CheckFailure(
            "Missing permissions: required role "
            f"{_role_mention(settings.discord_admin_role_id)}"
        )

    return app_commands.check(predicate)


def general_admin_check():
    async def predicate(interaction: Interaction) -> bool:
        settings = _settings_from_interaction(interaction)
        if settings is None:
            return False
        general_role = (
            settings.discord_general_admin_role_id
            if settings.discord_general_admin_role_id is not None
            else settings.discord_admin_role_id
        )
        role_ids = _member_role_ids(interaction.user)
        if settings.discord_admin_role_id in role_ids or general_role in role_ids:
            return True
        raise app_commands.CheckFailure(
            "Missing permissions: required one of roles "
            f"{_role_mention(general_role)} or "
            f"{_role_mention(settings.discord_admin_role_id)}"
        )

    return app_commands.check(predicate)


def map_reviewer_check():
    async def predicate(interaction: Interaction) -> bool:
        settings = _settings_from_interaction(interaction)
        if settings is None:
            return False
        reviewer_role = (
            settings.discord_map_reviewer_role_id
            if settings.discord_map_reviewer_role_id is not None
            else settings.discord_admin_role_id
        )
        if reviewer_role in _member_role_ids(interaction.user):
            return True
        raise app_commands.CheckFailure(
            f"Missing permissions: required role {_role_mention(reviewer_role)}"
        )

    return app_commands.check(predicate)
