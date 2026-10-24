from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from bot_fakes import make_bot, make_settings

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

    async def edit_original_response(self, *, content: str, **kwargs) -> None:
        self.response.replies.append(content)

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

    async def rpc_upload_maps(
        self, *, server: str, files: list[dict[str, object]], timeout_ms: int
    ):
        from xcore_protocol.generated.maps import MapsUploadResponseV1

        self.maps_calls.append((server, files))
        return MapsUploadResponseV1.from_payload(
            {
                "messageType": "maps.upload.response",
                "messageVersion": 1,
                "server": server,
                "results": [
                    {"fileName": item["filename"], "status": "installed"}
                    for item in files
                ],
            }
        )


async def test_upload_map_requires_valid_msav_files() -> None:
    bot = make_bot(settings=make_settings(discord_map_reviewer_role_id=200), bus=_Bus())
    bot.map_cache = {}

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
    assert bot.bus.maps_calls == []


async def test_upload_map_publishes_msav_files() -> None:
    bot = make_bot(settings=make_settings(discord_map_reviewer_role_id=200), bus=_Bus())
    bot.map_cache = {}

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

    assert len(bot.bus.maps_calls) == 1
    server, files = bot.bus.maps_calls[0]
    assert server == "mini-pvp"
    assert [item["filename"] for item in files] == ["one.msav", "two.MSAV"]
    assert any("Uploading 2 map" in reply for reply in interaction.replies)
    assert "✅ Installed; attribution saved" in interaction.replies[-1]

    for item in files:
        assert item["discordUpload"]["uploadedBy"] == {
            "discordId": "123456789012345678",
            "discordUsername": "tester",
        }
        assert item["discordUpload"]["uploadedAt"] > 0
        assert "creator" not in item["discordUpload"]


async def test_upload_map_records_selected_creator_for_all_files() -> None:
    bot = make_bot(settings=make_settings(discord_map_reviewer_role_id=200), bus=_Bus())
    bot.map_cache = {}
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
    _, files = bot.bus.maps_calls[0]
    assert all(
        item["discordUpload"]["creator"]
        == {"discordId": str(creator.id), "discordUsername": "Mapper"}
        for item in files
    )
    assert "✅ Installed; attribution saved" in interaction.replies[-1]


async def test_upload_creator_overrides_follow_original_attachment_positions() -> None:
    bot = make_bot(settings=make_settings(discord_map_reviewer_role_id=200), bus=_Bus())
    bot.map_cache = {}
    interaction = _Interaction(id=40, user=_Member(roles=[_Role(200)]))
    default = _Member([], "Default", 111)
    override = _Member([], "Override", 222)
    await cmd_upload_map(
        bot,
        interaction,
        "mini-pvp",
        [
            _Attachment("one.msav", "https://example/one"),
            _Attachment("skip.txt", "https://example/skip"),
            _Attachment("three.msav", "https://example/three"),
        ],
        creator=default,
        creators=[None, None, override],
    )
    files = bot.bus.maps_calls[0][1]
    assert [item["discordUpload"]["creator"]["discordId"] for item in files] == [
        "111",
        "222",
    ]


async def test_upload_timeout_reports_uncertainty_without_claiming_failure_or_retrying() -> (
    None
):
    from unittest.mock import AsyncMock

    bot = make_bot(settings=make_settings(discord_map_reviewer_role_id=200), bus=_Bus())
    bot.map_cache = {}
    bot.bus.rpc_upload_maps = AsyncMock(side_effect=TimeoutError)
    interaction = _Interaction(id=50, user=_Member(roles=[_Role(200)]))
    await cmd_upload_map(
        bot,
        interaction,
        "mini-pvp",
        [_Attachment("arena.msav", "https://example/arena")],
    )
    assert "Some files may already be installed" in interaction.replies[-1]
    assert bot.bus.rpc_upload_maps.await_count == 1


async def test_upload_formats_partial_result_and_disables_mentions() -> None:
    from unittest.mock import AsyncMock

    from xcore_protocol.generated.maps import MapsUploadResponseV1

    bot = make_bot(settings=make_settings(discord_map_reviewer_role_id=200), bus=_Bus())
    bot.map_cache = {"mini-pvp": (0, [])}
    bot.bus.rpc_upload_maps = AsyncMock(
        return_value=MapsUploadResponseV1.from_payload(
            {
                "messageType": "maps.upload.response",
                "messageVersion": 1,
                "server": "mini-pvp",
                "results": [
                    {"fileName": "ok.msav", "status": "installed"},
                    {"fileName": "bad.msav", "status": "invalid_map"},
                ],
            }
        )
    )
    interaction = _Interaction(id=60, user=_Member(roles=[_Role(200)]))
    interaction.edit_original_response = AsyncMock()
    await cmd_upload_map(
        bot,
        interaction,
        "mini-pvp",
        [
            _Attachment("ok.msav", "https://example/ok"),
            _Attachment("bad.msav", "https://example/bad"),
        ],
    )
    call = interaction.edit_original_response.call_args.kwargs
    assert "✅ Installed" in call["content"] and "❌ Invalid map" in call["content"]
    assert not call["allowed_mentions"].everyone and not call["allowed_mentions"].users
    assert "mini-pvp" not in bot.map_cache


async def test_malformed_response_invalidates_cached_map_list_and_finishes_message() -> (
    None
):
    from unittest.mock import AsyncMock

    bot = make_bot(settings=make_settings(discord_map_reviewer_role_id=200), bus=_Bus())
    bot.map_cache = {"mini-pvp": (0, [])}
    bot.bus.rpc_upload_maps = AsyncMock(side_effect=TypeError("results must be a list"))
    interaction = _Interaction(id=70, user=_Member(roles=[_Role(200)]))
    await cmd_upload_map(
        bot,
        interaction,
        "mini-pvp",
        [_Attachment("arena.msav", "https://example/arena")],
    )
    assert "Could not confirm" in interaction.replies[-1]
    assert "mini-pvp" not in bot.map_cache


async def test_invalid_upload_is_rejected_before_rpc_or_uploading_message() -> None:
    bot = make_bot(settings=make_settings(discord_map_reviewer_role_id=200), bus=_Bus())
    bot.map_cache = {}
    interaction = _Interaction(id=80, user=_Member(roles=[_Role(200)]))
    await cmd_upload_map(
        bot,
        interaction,
        "mini-pvp",
        [_Attachment("../arena.msav", "https://example/arena")],
    )
    assert "nothing was sent" in interaction.ephemeral_replies[-1]
    assert bot.bus.maps_calls == []
    assert not interaction.replies


async def test_attachment_only_discord_message_is_not_published_as_empty_chat(
    monkeypatch,
) -> None:
    from types import SimpleNamespace
    from unittest.mock import AsyncMock

    from xcore_discord_bot.registry import server_registry

    bot = make_bot(bus=SimpleNamespace(publish_discord_message=AsyncMock()))
    monkeypatch.setattr(
        server_registry, "get_server_for_channel", lambda channel: "mini-pvp"
    )
    message = SimpleNamespace(
        author=SimpleNamespace(bot=False), channel=SimpleNamespace(id=1), content=""
    )
    await bot.on_message(message)
    bot.bus.publish_discord_message.assert_not_awaited()
