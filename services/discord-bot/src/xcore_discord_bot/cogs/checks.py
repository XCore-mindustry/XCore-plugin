from __future__ import annotations

from discord import Interaction, app_commands

from ..permissions import (
    admin_role_ids,
    general_admin_role_ids,
    map_reviewer_role_ids,
    require_any_role,
    role_mention,
    settings_from_interaction,
)


def admin_check():
    async def predicate(interaction: Interaction) -> bool:
        settings = settings_from_interaction(interaction)
        if settings is None:
            return False
        return require_any_role(
            interaction,
            role_ids=admin_role_ids(settings),
            message=(
                "Missing permissions: required role "
                f"{role_mention(settings.discord_admin_role_id)}"
            ),
        )

    return app_commands.check(predicate)


def general_admin_check():
    async def predicate(interaction: Interaction) -> bool:
        settings = settings_from_interaction(interaction)
        if settings is None:
            return False
        general_role = general_admin_role_ids(settings)[1]
        return require_any_role(
            interaction,
            role_ids=general_admin_role_ids(settings),
            message=(
                "Missing permissions: required one of roles "
                f"{role_mention(general_role)} or "
                f"{role_mention(settings.discord_admin_role_id)}"
            ),
        )

    return app_commands.check(predicate)


def map_reviewer_check():
    async def predicate(interaction: Interaction) -> bool:
        settings = settings_from_interaction(interaction)
        if settings is None:
            return False
        reviewer_role = map_reviewer_role_ids(settings)[0]
        return require_any_role(
            interaction,
            role_ids=map_reviewer_role_ids(settings),
            message=f"Missing permissions: required role {role_mention(reviewer_role)}",
        )

    return app_commands.check(predicate)
