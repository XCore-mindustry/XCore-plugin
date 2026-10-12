from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

import pytest

from xcore_discord_bot.handlers_misc import cmd_maps, perform_remove_map
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


class _MapsBot:
    def __init__(
        self, maps: list[dict[str, str]] | None = None, *, fail: bool = False
    ) -> None:
        self.maps = maps or []
        self.fail = fail
        self.rpc_timeout_ms = 4321

    async def rpc_maps_list(
        self, *, server: str, timeout_ms: int
    ) -> list[dict[str, str]]:
        assert server == "mini-pvp"
        assert timeout_ms == 4321
        if self.fail:
            raise TimeoutError
        return self.maps


class _RemoveMapBot:
    def __init__(self, *, claim: bool = True, fail: bool = False) -> None:
        self.claim = claim
        self.fail = fail
        self.rpc_timeout_ms = 9876
        self.claim_keys: list[tuple[str, int]] = []
        self.remove_calls: list[tuple[str, str, int]] = []

    async def claim_idempotency(self, key: str, *, ttl_seconds: int = 600) -> bool:
        self.claim_keys.append((key, ttl_seconds))
        return self.claim

    async def rpc_remove_map(
        self, *, server: str, file_name: str, timeout_ms: int
    ) -> str:
        self.remove_calls.append((server, file_name, timeout_ms))
        if self.fail:
            raise TimeoutError
        return "removed"


@pytest.mark.asyncio
async def test_cmd_maps_reports_timeout() -> None:
    bot = _MapsBot(fail=True)
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


@pytest.mark.asyncio
async def test_cmd_maps_reports_empty_server_map_list() -> None:
    bot = _MapsBot(maps=[])
    interaction = _Interaction()

    await cmd_maps(bot, interaction, "mini-pvp")

    assert interaction.response.deferred is True
    assert interaction.followup.messages == [
        {"content": "No maps found on server `mini-pvp`", "embed": None, "view": None}
    ]


@pytest.mark.asyncio
async def test_cmd_maps_renders_rating_metadata() -> None:
    bot = _MapsBot(
        maps=[
            {
                "name": "Arena",
                "file_name": "arena.msav",
                "author": "Alice",
                "width": "120",
                "height": "80",
                "file_size_bytes": "2048",
                "like": "5",
                "dislike": "2",
                "reputation": "3",
                "popularity": "7.5",
                "game_mode": "pvp",
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


@pytest.mark.asyncio
async def test_cmd_maps_defaults_to_reputation_sort() -> None:
    bot = _MapsBot(
        maps=[
            {
                "name": "LowRep",
                "file_name": "low.msav",
                "author": "A",
                "reputation": "1",
                "like": "1",
            },
            {
                "name": "HighRep",
                "file_name": "high.msav",
                "author": "B",
                "reputation": "5",
                "like": "2",
            },
        ]
    )
    interaction = _Interaction()

    await cmd_maps(bot, interaction, "mini-pvp")

    embed = interaction.followup.messages[0]["embed"]
    assert embed is not None
    assert embed.description is not None
    assert embed.description.splitlines()[0].startswith("• HighRep")


@pytest.mark.asyncio
async def test_perform_remove_map_returns_duplicate_message() -> None:
    bot = _RemoveMapBot(claim=False)

    result = await perform_remove_map(
        bot,
        server="mini-pvp",
        file_name="arena.msav",
        request_nonce="nonce-1",
    )

    assert result == "This map removal was already processed."
    assert bot.claim_keys == [("remove-map:mini-pvp:arena.msav:nonce-1", 600)]
    assert bot.remove_calls == []


@pytest.mark.asyncio
async def test_perform_remove_map_returns_timeout_message() -> None:
    bot = _RemoveMapBot(claim=True, fail=True)

    result = await perform_remove_map(
        bot,
        server="mini-pvp",
        file_name="arena.msav",
        request_nonce="nonce-2",
    )

    assert result == "No response from target server (timeout)."
    assert bot.remove_calls == [("mini-pvp", "arena.msav", 9876)]


@pytest.mark.asyncio
async def test_maps_show_distinct_file_author_creator_and_submitter() -> None:
    bot = _MapsBot(
        maps=[
            {
                "name": "Arena",
                "file_name": "arena.msav",
                "author": "[blue]File Mapper[]",
                "uploaded_by_discord_id": "123456789012345678",
                "uploaded_by_name": "Reviewer",
                "creator_discord_id": "234567890123456789",
                "creator_name": "Creator",
                "uploaded_at": "1791806400000",
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
    from xcore_discord_bot.handlers_misc import _map_list_pages

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
    pages = _map_list_pages(maps, "name")
    assert sum(map(len, pages)) == 40
    assert len(pages) > 1
    assert all(len("\n".join(page)) <= 4096 for page in pages)


def test_map_inline_code_preserves_filename_punctuation_without_color_markup() -> None:
    from xcore_discord_bot.handlers_misc import _map_list_row

    row = _map_list_row(
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
    from xcore_discord_bot.handlers_misc import _map_contributor

    text = _map_contributor(
        {"creator_discord_id": "123456789012345678", "creator_name": name}, "creator"
    )
    assert text.count("](https://discord.com/users/") == 1
    assert "](https://evil.example)" not in text
    assert "\\]" in text or "\\[" in text


@pytest.mark.asyncio
async def test_creator_filter_only_keeps_maps_declared_by_selected_user() -> None:
    from types import SimpleNamespace

    bot = _MapsBot(
        maps=[
            {
                "name": "Matching",
                "file_name": "one.msav",
                "author": "A",
                "creator_discord_id": "111",
            },
            {
                "name": "Other",
                "file_name": "two.msav",
                "author": "A",
                "creator_discord_id": "222",
            },
            {"name": "Legacy", "file_name": "three.msav", "author": "A"},
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
    from xcore_discord_bot.handlers_misc import _map_list_pages

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
    pages = _map_list_pages(maps, "name")
    assert sum(map(len, pages)) == len(maps)
    assert all(len("\n".join(page).encode("utf-16-le")) // 2 <= 3800 for page in pages)
