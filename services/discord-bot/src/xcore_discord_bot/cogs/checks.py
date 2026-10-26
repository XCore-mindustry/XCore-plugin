from __future__ import annotations

from collections.abc import Iterable
from typing import TYPE_CHECKING, cast

from discord import Interaction, app_commands

from ..command_helpers import reply_locale
from ..i18n import t
from ..permissions import (
    admin_role_ids,
    general_admin_role_ids,
    has_any_role,
    head_admin_role_ids,
    map_reviewer_role_ids,
    role_mention,
    settings_from_interaction,
)

if TYPE_CHECKING:
    from ..bot import XCoreDiscordBot


def _require(
    interaction: Interaction,
    role_ids: Iterable[int | None],
    key: str,
    **roles: int | None,
) -> bool:
    """Lets a holder of one of the roles through; tells anyone else, in their language, which."""
    if has_any_role(interaction.user, role_ids):
        return True
    mentions = {name: role_mention(role_id) for name, role_id in roles.items()}
    locale = reply_locale(cast("XCoreDiscordBot", interaction.client), interaction)
    raise app_commands.CheckFailure(t(locale, key, **mentions))


def admin_check():
    async def predicate(interaction: Interaction) -> bool:
        settings = settings_from_interaction(interaction)
        if settings is None:
            return False
        return _require(
            interaction,
            admin_role_ids(settings),
            "command-missing-role",
            role=settings.discord_admin_role_id,
        )

    return app_commands.check(predicate)


def general_admin_check():
    async def predicate(interaction: Interaction) -> bool:
        settings = settings_from_interaction(interaction)
        if settings is None:
            return False
        general_role = general_admin_role_ids(settings)[1]
        return _require(
            interaction,
            general_admin_role_ids(settings),
            "command-missing-roles",
            first=general_role,
            second=settings.discord_admin_role_id,
        )

    return app_commands.check(predicate)


def head_admin_check():
    async def predicate(interaction: Interaction) -> bool:
        settings = settings_from_interaction(interaction)
        if settings is None:
            return False
        head_role = head_admin_role_ids(settings)[0]
        return _require(
            interaction,
            head_admin_role_ids(settings),
            "command-missing-role",
            role=head_role,
        )

    return app_commands.check(predicate)


def map_reviewer_check():
    async def predicate(interaction: Interaction) -> bool:
        settings = settings_from_interaction(interaction)
        if settings is None:
            return False
        reviewer_role = map_reviewer_role_ids(settings)[0]
        return _require(
            interaction,
            map_reviewer_role_ids(settings),
            "command-missing-role",
            role=reviewer_role,
        )

    return app_commands.check(predicate)
