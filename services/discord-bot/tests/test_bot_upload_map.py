from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

import pytest

from xcore_discord_bot.bot import XCoreDiscordBot
from xcore_discord_bot.handlers_misc import cmd_upload_map


@dataclass
class _Role:
    id: int


@dataclass
class _Member:
    roles: list[_Role]
    display_name: str = "tester"
    id: int = 123456789012345678


@dataclass
class _Attachment:
    filename: str
    url: str


@dataclass
class _InteractionResponse:
    replies: list[str] = field(default_factory=list)
    ephemeral_replies: list[str] = field(default_factory=list)

    async def send_message(
        self, text: str, *, ephemeral: bool = False, **kwargs
    ) -> None:
        if ephemeral:
            self.ephemeral_replies.append(text)
        else:
            self.replies.append(text)


@dataclass
class _Interaction:
    id: int
    user: Any
    response: _InteractionResponse = field(default_factory=_InteractionResponse)

    @property
    def replies(self) -> list[str]:
        return self.response.replies

    @property
    def ephemeral_replies(self) -> list[str]:
        return self.response.ephemeral_replies


class _Bus:
    def __init__(self) -> None:
        self.claimed = True
        self.maps_calls: list[tuple[str, list[dict[str, str]]]] = []

    async def claim_idempotency(self, key: str, ttl_seconds: int = 600) -> bool:
        return self.claimed

    async def publish_maps_load(self, server: str, files: list[dict[str, str]]) -> None:
        self.maps_calls.append((server, files))


class _Settings:
    discord_map_reviewer_role_id = 200


@pytest.mark.asyncio
async def test_upload_map_requires_valid_msav_files() -> None:
    bot = object.__new__(XCoreDiscordBot)
    bot._settings = _Settings()
    bot._bus = _Bus()

    interaction = _Interaction(
        id=10,
        user=_Member(roles=[_Role(100)]),
    )

    attachments = [
        _Attachment(filename="one.txt", url="https://example/one.txt"),
        None,
        None,
    ]
    await cmd_upload_map(bot, interaction, "mini-pvp", attachments)

    assert interaction.ephemeral_replies
    assert "No valid .msav files attached." in interaction.ephemeral_replies[0]
    assert bot._bus.maps_calls == []


@pytest.mark.asyncio
async def test_upload_map_publishes_msav_files() -> None:
    bot = object.__new__(XCoreDiscordBot)
    bot._settings = _Settings()
    bot._bus = _Bus()

    interaction = _Interaction(
        id=20,
        user=_Member(roles=[_Role(200)]),
    )

    attachments = [
        _Attachment(filename="one.msav", url="https://example/one.msav"),
        _Attachment(filename="ignored.txt", url="https://example/ignored.txt"),
        _Attachment(filename="two.MSAV", url="https://example/two.MSAV"),
    ]
    await cmd_upload_map(bot, interaction, "mini-pvp", attachments)

    assert len(bot._bus.maps_calls) == 1
    server, files = bot._bus.maps_calls[0]
    assert server == "mini-pvp"
    assert [item["filename"] for item in files] == ["one.msav", "two.MSAV"]
    assert any("Queued 2 map" in reply for reply in interaction.replies)

    for item in files:
        assert item["discordUpload"]["uploadedBy"] == {
            "discordId": "123456789012345678",
            "discordUsername": "tester",
        }
        assert item["discordUpload"]["uploadedAt"] > 0
        assert "creator" not in item["discordUpload"]


@pytest.mark.asyncio
async def test_upload_map_records_selected_creator_for_all_files() -> None:
    bot = object.__new__(XCoreDiscordBot)
    bot._settings = _Settings()
    bot._bus = _Bus()
    interaction = _Interaction(id=30, user=_Member(roles=[_Role(200)]))
    creator = _Member(roles=[], display_name="Mapper", id=234567890123456789)
    await cmd_upload_map(
        bot,
        interaction,
        "mini-pvp",
        [
            _Attachment(filename="one.msav", url="https://example/one.msav"),
            _Attachment(filename="two.msav", url="https://example/two.msav"),
        ],
        creator=creator,
    )
    _, files = bot._bus.maps_calls[0]
    assert all(
        item["discordUpload"]["creator"]
        == {"discordId": str(creator.id), "discordUsername": "Mapper"}
        for item in files
    )
    assert "Declared creator" in interaction.replies[0]
