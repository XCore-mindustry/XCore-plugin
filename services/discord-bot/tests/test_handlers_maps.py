from __future__ import annotations

from dataclasses import dataclass, field
from types import SimpleNamespace
from typing import Any

import discord
import pytest
from backends import GameServer, real_bot
from bot_fakes import make_settings
from xcore_protocol.generated.maps import (
    MapsListRequestV1,
    MapsListResponseV1,
    MapsRemoveRequestV1,
    MapsRemoveResponseV1,
)

from xcore_discord_bot.bot import XCoreDiscordBot
from xcore_discord_bot.handlers_maps import cmd_maps, perform_remove_map
from xcore_discord_bot.server_views import MapsListView


@dataclass
class _DeferredResponse:
    deferred: bool = False

    async def defer(self) -> None:
        self.deferred = True


@dataclass
class _Followup:
    messages: list[dict[str, Any]] = field(default_factory=list)

    async def send(
        self, content: str | None = None, *, embed: Any = None, view: Any = None
    ):
        message = {"content": content, "embed": embed, "view": view}
        self.messages.append(message)
        return message


@dataclass
class _Interaction:
    response: _DeferredResponse = field(default_factory=_DeferredResponse)
    followup: _Followup = field(default_factory=_Followup)
    user: Any = field(default_factory=lambda: SimpleNamespace(id=1))
    locale: discord.Locale | None = None


LIST = MapsListRequestV1.MESSAGE_TYPE
REMOVE = MapsRemoveRequestV1.MESSAGE_TYPE


async def _maps_bot(
    maps: list[dict[str, Any]] | None = None, *, silent: bool = False
) -> tuple[XCoreDiscordBot, GameServer]:
    """A bot with ``mini-pvp`` online: it lists ``maps`` as the plugin sends them, removes
    any map it is asked to, or answers nothing while ``silent``."""
    bot, _ = await real_bot(settings=make_settings(rpc_timeout_ms=50))
    game = GameServer(bot)
    game.answers[LIST] = lambda payload: (
        None
        if silent
        else {
            "messageType": MapsListResponseV1.MESSAGE_TYPE,
            "messageVersion": 1,
            "server": payload["server"],
            "maps": maps or [],
        }
    )
    game.answers[REMOVE] = lambda payload: (
        None
        if silent
        else {
            "messageType": MapsRemoveResponseV1.MESSAGE_TYPE,
            "messageVersion": 1,
            "server": payload["server"],
            "result": f"removed {payload['fileName']}",
        }
    )
    return bot, game


def _upload(uploader: str, creator: str | None = None) -> dict[str, Any]:
    """The ``discordUpload`` of a map uploaded through Discord."""
    upload: dict[str, Any] = {
        "uploadedBy": {"discordId": uploader, "discordUsername": "Reviewer"},
        "uploadedAt": 1791806400000,
    }
    if creator is not None:
        upload["creator"] = {"discordId": creator, "discordUsername": "Creator"}
    return upload


async def test_cmd_maps_reports_timeout() -> None:
    bot, game = await _maps_bot(silent=True)
    interaction = _Interaction()

    await cmd_maps(bot, interaction, "mini-pvp")

    assert interaction.response.deferred is True
    assert interaction.followup.messages == [
        {
            "content": "No response from target server (timeout).",
            "embed": None,
            "view": None,
        }
    ]
    # A list is a read, so it is asked for once more before giving up.
    assert [request["timeout_ms"] for request in game.requests] == ["50", "50"]


async def test_cmd_maps_reports_empty_server_map_list() -> None:
    bot, game = await _maps_bot([])
    interaction = _Interaction()

    await cmd_maps(bot, interaction, "mini-pvp")

    assert interaction.response.deferred is True
    assert interaction.followup.messages == [
        {"content": "No maps found on server `mini-pvp`", "embed": None, "view": None}
    ]
    assert game.sent(LIST) == [
        {"messageType": LIST, "messageVersion": 1, "server": "mini-pvp"}
    ]


async def test_cmd_maps_renders_rating_metadata() -> None:
    bot, _ = await _maps_bot(
        [
            {
                "name": "Arena",
                "fileName": "arena.msav",
                "author": "Alice",
                "width": 120,
                "height": 80,
                "fileSizeBytes": 2048,
                "like": 5,
                "dislike": 2,
                "reputation": 3,
                "popularity": 7.5,
                "gameMode": "pvp",
            }
        ]
    )
    interaction = _Interaction()

    await cmd_maps(bot, interaction, "mini-pvp")

    assert interaction.response.deferred is True
    assert len(interaction.followup.messages) == 1
    embed = interaction.followup.messages[0]["embed"]
    assert embed is not None
    assert embed.description == (
        "• Arena — by `Alice`\n  `arena.msav` • 120x80 • 2.0 KB • 👍 5 • 👎 2 • rep 3"
    )
    assert (
        embed.footer.text
        == "Sort: reputation • Page 1/1 • total maps: 1 • entries on page: 1"
    )
    view = interaction.followup.messages[0]["view"]
    assert isinstance(view, MapsListView)


async def test_cmd_maps_defaults_to_reputation_sort() -> None:
    bot, _ = await _maps_bot(
        [
            {
                "name": "LowRep",
                "fileName": "low.msav",
                "author": "A",
                "reputation": 1,
                "like": 1,
            },
            {
                "name": "HighRep",
                "fileName": "high.msav",
                "author": "B",
                "reputation": 5,
                "like": 2,
            },
        ]
    )
    interaction = _Interaction()

    await cmd_maps(bot, interaction, "mini-pvp")

    embed = interaction.followup.messages[0]["embed"]
    assert embed is not None
    assert embed.description is not None
    assert embed.description.splitlines()[0].startswith("• HighRep")


async def test_perform_remove_map_runs_once_per_request() -> None:
    bot, game = await _maps_bot()

    async def remove(nonce: str) -> str:
        return await perform_remove_map(
            bot, server="mini-pvp", file_name="arena.msav", request_nonce=nonce
        )

    assert await remove("nonce-1") == (
        "🗑️ `mini-pvp` remove-map `arena.msav`: removed arena.msav"
    )
    assert await remove("nonce-1") == "This map removal was already processed."
    assert (
        590
        < await bot.bus.redis.ttl("xcore:idmp:remove-map:mini-pvp:arena.msav:nonce-1")
        <= 600
    )
    # Another confirmation of the same map is another request.
    await remove("nonce-2")
    assert game.sent(REMOVE) == 2 * [
        {
            "messageType": REMOVE,
            "messageVersion": 1,
            "server": "mini-pvp",
            "fileName": "arena.msav",
        }
    ]


async def test_perform_remove_map_returns_timeout_message() -> None:
    bot, game = await _maps_bot(silent=True)

    result = await perform_remove_map(
        bot,
        server="mini-pvp",
        file_name="arena.msav",
        request_nonce="nonce-2",
    )

    assert result == "No response from target server (timeout)."
    # A removal is a write: it may have been applied, so it is not sent again.
    assert [request["timeout_ms"] for request in game.requests] == ["50"]


async def test_maps_show_distinct_file_author_creator_and_submitter() -> None:
    bot, _ = await _maps_bot(
        [
            {
                "name": "Arena",
                "fileName": "arena.msav",
                "author": "[blue]File Mapper[]",
                "discordUpload": _upload("123456789012345678", "234567890123456789"),
            }
        ]
    )
    interaction = _Interaction()
    await cmd_maps(bot, interaction, "mini-pvp")
    description = interaction.followup.messages[0]["embed"].description
    assert "file author `File Mapper`" in description
    assert "🎨 [Creator](https://discord.com/users/234567890123456789)" in description
    assert "⬆ [Reviewer](https://discord.com/users/123456789012345678)" in description
    assert "<t:1791806400:d>" in description


def test_map_list_pages_respect_embed_limit_and_keep_every_map() -> None:
    from xcore_discord_bot.map_list import map_list_pages

    maps = [
        {
            "name": "*" * 200,
            "file_name": "`" * 200,
            "author": "*" * 200,
            "uploaded_by_discord_id": "123456789012345678",
            "uploaded_by_name": "*" * 200,
            "creator_discord_id": "234567890123456789",
            "creator_name": "*" * 200,
            "uploaded_at": "1791806400000",
        }
        for _ in range(40)
    ]
    pages = map_list_pages(maps, "name")
    assert sum(map(len, pages)) == 40
    assert len(pages) > 1
    assert all(len("\n".join(page)) <= 4096 for page in pages)


def test_map_inline_code_preserves_filename_punctuation_without_color_markup() -> None:
    from xcore_discord_bot.map_list import map_list_row

    row = map_list_row(
        {
            "name": "[green]Arena[]",
            "author": "[blue]Map_Maker[]",
            "file_name": "arena_v2.msav",
        }
    )
    assert "`arena_v2.msav`" in row
    assert "`Map_Maker`" in row
    assert "[green]" not in row and "[blue]" not in row


@pytest.mark.parametrize(
    "name", ["x](https://evil.example) [y", "[Name", "a\\](https://evil.example)"]
)
def test_contributor_name_cannot_escape_profile_link(name: str) -> None:
    from xcore_discord_bot.map_list import map_contributor

    text = map_contributor(
        {"creator_discord_id": "123456789012345678", "creator_name": name}, "creator"
    )
    assert text.count("](https://discord.com/users/") == 1
    assert "](https://evil.example)" not in text
    assert "\\]" in text or "\\[" in text


async def test_creator_filter_only_keeps_maps_declared_by_selected_user() -> None:
    from types import SimpleNamespace

    bot, _ = await _maps_bot(
        [
            {
                "name": "Matching",
                "fileName": "one.msav",
                "author": "A",
                "discordUpload": _upload("111", "111"),
            },
            {
                # Uploaded by the selected user, but declared as someone else's.
                "name": "Other",
                "fileName": "two.msav",
                "author": "A",
                "discordUpload": _upload("111", "222"),
            },
            {"name": "Legacy", "fileName": "three.msav", "author": "A"},
        ]
    )
    interaction = _Interaction()
    await cmd_maps(bot, interaction, "mini-pvp", creator=SimpleNamespace(id=111))
    description = interaction.followup.messages[0]["embed"].description
    assert (
        "Matching" in description
        and "Other" not in description
        and "Legacy" not in description
    )


def test_map_pages_budget_astral_unicode_as_utf16_units() -> None:
    from xcore_discord_bot.map_list import map_list_pages

    maps = [
        {
            "name": "🎨" * 80,
            "file_name": "a.msav",
            "author": "🎨" * 80,
            "uploaded_by_discord_id": "123",
            "uploaded_by_name": "🎨" * 80,
            "creator_discord_id": "234",
            "creator_name": "🎨" * 80,
        }
        for _ in range(40)
    ]
    pages = map_list_pages(maps, "name")
    assert sum(map(len, pages)) == len(maps)
    assert all(len("\n".join(page).encode("utf-16-le")) // 2 <= 3800 for page in pages)


async def test_cmd_maps_answers_in_the_users_language() -> None:
    bot, _ = await _maps_bot(
        [
            {
                "name": "Arena",
                "fileName": "arena.msav",
                "author": "Alice",
                "reputation": 1500,
            }
        ]
    )
    interaction = _Interaction(locale=discord.Locale.russian)

    await cmd_maps(bot, interaction, "mini-pvp")

    sent = interaction.followup.messages[0]
    assert sent["embed"].title == "Карты на `mini-pvp`"
    assert sent["embed"].description == (
        "• Arena — автор `Alice`\n  `arena.msav` • реп. 1\xa0500"
    )
    assert sent["embed"].footer.text == (
        "Сортировка: по репутации • Страница 1/1 • всего карт: 1 • на странице: 1"
    )
    assert [item.label for item in sent["view"].children] == [
        "◀ Назад",
        "Вперёд ▶",
        "Сортировка: по репутации",
    ]


async def test_cmd_maps_reports_an_empty_list_in_ukrainian() -> None:
    bot, _ = await _maps_bot([])
    interaction = _Interaction(locale=discord.Locale.ukrainian)

    await cmd_maps(bot, interaction, "mini-pvp")

    assert interaction.followup.messages[0]["content"] == (
        "На сервері `mini-pvp` немає карт"
    )
