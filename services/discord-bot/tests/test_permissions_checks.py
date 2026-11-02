from __future__ import annotations

from dataclasses import dataclass, field
from types import SimpleNamespace
from typing import Any

import discord
import pytest
from discord import app_commands

from xcore_discord_bot.cogs.checks import (
    admin_check,
    general_admin_check,
    map_reviewer_check,
)
from xcore_discord_bot.i18n import LocalePref
from xcore_discord_bot.permissions import (
    ensure_any_role,
    general_admin_role_ids,
    map_reviewer_role_ids,
    role_mention,
    settings_from_interaction,
)


@dataclass
class _Role:
    id: int


@dataclass
class _User:
    roles: list[_Role]
    id: int = 1


@dataclass
class _Response:
    sent: list[tuple[str, bool]] = field(default_factory=list)

    async def send_message(self, message: str, *, ephemeral: bool = False) -> None:
        self.sent.append((message, ephemeral))


class _ClientWithSettings:
    def __init__(self, settings: object, prefs: dict[int, Any] | None = None) -> None:
        self.settings = settings
        # The /language choices the bot keeps warm; nobody has picked one by default.
        store = SimpleNamespace(cached_locale_pref=(prefs or {}).get)
        self.container = SimpleNamespace(cases=SimpleNamespace(store=store))


@dataclass
class _Interaction:
    user: _User
    client: Any
    response: _Response = field(default_factory=_Response)
    locale: Any = None


@dataclass
class _Settings:
    discord_admin_role_id: int
    discord_general_admin_role_id: int | None = None
    discord_map_reviewer_role_id: int | None = None


def _predicate(check: Any):
    return check.__closure__[0].cell_contents


def test_role_mention_uses_fallback_text_for_none() -> None:
    assert role_mention(None) == "configured role"


def test_settings_from_interaction_returns_none_for_unknown_client() -> None:
    interaction = _Interaction(user=_User(roles=[]), client=object())
    assert settings_from_interaction(interaction) is None


def test_general_admin_role_ids_fall_back_to_admin_role() -> None:
    settings = _Settings(discord_admin_role_id=10, discord_general_admin_role_id=None)
    assert general_admin_role_ids(settings) == (10, 10)


def test_map_reviewer_role_ids_fall_back_to_admin_role() -> None:
    settings = _Settings(discord_admin_role_id=10, discord_map_reviewer_role_id=None)
    assert map_reviewer_role_ids(settings) == (10,)


@pytest.mark.asyncio
async def test_ensure_any_role_sends_ephemeral_denied_message() -> None:
    interaction = _Interaction(user=_User(roles=[_Role(1)]), client=object())

    allowed = await ensure_any_role(
        interaction,
        role_ids=(2, 3),
        denied_message="Access denied",
    )

    assert allowed is False
    assert interaction.response.sent == [("Access denied", True)]


@pytest.mark.asyncio
async def test_admin_check_returns_false_without_settings() -> None:
    interaction = _Interaction(user=_User(roles=[_Role(10)]), client=object())

    allowed = await _predicate(admin_check())(interaction)

    assert allowed is False


@pytest.mark.asyncio
async def test_general_admin_check_raises_with_both_role_mentions() -> None:
    settings = _Settings(discord_admin_role_id=20, discord_general_admin_role_id=10)
    interaction = _Interaction(
        user=_User(roles=[_Role(99)]),
        client=_ClientWithSettings(settings),
    )

    with pytest.raises(app_commands.CheckFailure) as error:
        await _predicate(general_admin_check())(interaction)

    assert str(error.value) == (
        "Missing permissions: required one of roles <@&10> or <@&20>"
    )


@pytest.mark.asyncio
async def test_map_reviewer_check_uses_configured_role_fallback_text() -> None:
    settings = _Settings(discord_admin_role_id=20, discord_map_reviewer_role_id=None)
    interaction = _Interaction(
        user=_User(roles=[_Role(99)]),
        client=_ClientWithSettings(settings),
    )

    with pytest.raises(app_commands.CheckFailure) as error:
        await _predicate(map_reviewer_check())(interaction)

    assert str(error.value) == "Missing permissions: required role <@&20>"


@pytest.mark.asyncio
async def test_a_refusal_follows_the_users_discord_language() -> None:
    settings = _Settings(discord_admin_role_id=20, discord_general_admin_role_id=10)
    interaction = _Interaction(
        user=_User(roles=[_Role(99)]),
        client=_ClientWithSettings(settings),
        locale=discord.Locale.russian,
    )

    with pytest.raises(app_commands.CheckFailure) as error:
        await _predicate(general_admin_check())(interaction)

    assert str(error.value) == (
        "Недостаточно прав: нужна одна из ролей <@&10> или <@&20>"
    )


@pytest.mark.asyncio
async def test_a_refusal_follows_the_language_chosen_with_the_language_command() -> (
    None
):
    settings = _Settings(discord_admin_role_id=20)
    interaction = _Interaction(
        user=_User(roles=[_Role(99)], id=7),
        client=_ClientWithSettings(settings, {7: LocalePref("uk", "manual")}),
        locale=discord.Locale.russian,
    )

    with pytest.raises(app_commands.CheckFailure) as error:
        await _predicate(admin_check())(interaction)

    assert str(error.value) == "Бракує прав: потрібна роль <@&20>"
