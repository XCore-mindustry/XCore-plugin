from __future__ import annotations

import json
import uuid
from dataclasses import dataclass, field
from typing import Any, cast
from unittest.mock import AsyncMock, MagicMock

import discord
import pytest
from backends import GameServer, RpcAnswer, fail, published, real_bot, refuse

from xcore_discord_bot.bot import XCoreDiscordBot
from xcore_discord_bot.bus import accounts
from xcore_discord_bot.handlers_linking import cmd_link, cmd_link_status, cmd_unlink
from xcore_discord_bot.i18n import LocalePref

OPERATION = "discord.account.operation.request"
LEGACY_STREAMS = ("xcore:cmd:discord-link-confirm", "xcore:cmd:discord-unlink")


@dataclass
class _User:
    id: int = 555
    display_name: str = "discord-user"


@dataclass
class _Response:
    sent: list[tuple[str, bool]] = field(default_factory=list)
    embeds: list[tuple[Any, bool]] = field(default_factory=list)
    deferred: bool = False

    def is_done(self) -> bool:
        return self.deferred or bool(self.sent or self.embeds)

    async def defer(self, *, ephemeral: bool = False) -> None:
        assert not self.is_done()
        self.deferred = ephemeral

    async def send_message(
        self, text: str | None = None, *, ephemeral: bool = False, embed: Any = None
    ) -> None:
        assert not self.is_done()
        if text is not None:
            self.sent.append((text, ephemeral))
        if embed is not None:
            self.embeds.append((embed, ephemeral))


@dataclass
class _Followup:
    sent: list[tuple[str, bool]] = field(default_factory=list)

    async def send(self, text: str, *, ephemeral: bool = False) -> None:
        self.sent.append((text, ephemeral))


@dataclass
class _Interaction:
    id: int = 77
    user: _User = field(default_factory=_User)
    response: _Response = field(default_factory=_Response)
    followup: _Followup = field(default_factory=_Followup)
    locale: object = None

    @property
    def replies(self) -> list[tuple[str, bool]]:
        return self.response.sent + self.followup.sent


TARGET = {
    "_id": 1,
    "pid": 7,
    "uuid": "uuid-7",
    "nickname": "Target",
    "discord_id": "555",
    "discord_linked_at": 1_700_000_000_000,
}
UNLINKED = dict(TARGET, discord_id="", discord_linked_at=0)
ALT = {"_id": 2, "pid": 9, "uuid": "uuid-9", "nickname": "Alt", "discord_id": "555"}
# The shape the plugin stores (RedisDiscordLinkCodeStore.LinkCodePayload).
CODE = {
    "code": "ABC123",
    "playerUuid": "uuid-7",
    "playerPid": 7,
    "playerNickname": "Target",
    "server": "mini-pvp",
    "createdAt": 1,
    "expiresAt": 9999999999999,
}


def answer(status: str, **extra: Any) -> RpcAnswer:
    """A typed account operation response for the request it answers."""

    def reply(payload: dict[str, Any]) -> dict[str, Any]:
        return {
            "messageType": "discord.account.operation.response",
            "messageVersion": 1,
            "server": payload["server"],
            "operationId": payload["operationId"],
            "status": status,
            **extra,
        }

    return reply


async def _bot(
    *players: dict[str, Any], code: dict[str, Any] = CODE, servers=("mini-pvp", "other")
) -> tuple[XCoreDiscordBot, dict[str, GameServer]]:
    bot, db = await real_bot()
    games = {name: GameServer(bot, name) for name in servers}
    for player in players or (UNLINKED, ALT):
        await db["players"].insert_one(dict(player))
    await bot.bus.redis.set("xcore:discord-link:code:ABC123", json.dumps(code))
    return bot, games


async def _no_fanout(bot: XCoreDiscordBot, games: dict[str, GameServer]) -> bool:
    for name in games:
        for stream in LEGACY_STREAMS:
            if await published(bot.bus, f"{stream}:{name}"):
                return False
    return True


async def _link(bot: XCoreDiscordBot, **kwargs: Any) -> _Interaction:
    interaction = _Interaction(**kwargs)
    await cmd_link(cast(Any, bot), cast(Any, interaction), "abc123")
    return interaction


@pytest.mark.asyncio
async def test_link_asks_only_the_server_that_issued_the_code() -> None:
    bot, games = await _bot()
    games["mini-pvp"].answers[OPERATION] = answer("success", linkedAt=5)

    interaction = await _link(bot)

    (request,) = games["mini-pvp"].sent(OPERATION)
    assert games["other"].requests == []
    assert await _no_fanout(bot, games)
    assert request["action"] == "link"
    assert request["code"] == "ABC123"
    assert "expectedLinkedAt" not in request
    assert request["player"] == {
        "playerUuid": "uuid-7",
        "playerPid": 7,
        "playerName": "Target",
    }
    assert request["discord"] == {"discordId": "555", "discordUsername": "discord-user"}
    assert request["expiresAt"] - request["issuedAt"] == 10_000
    assert uuid.UUID(request["operationId"])
    (fields,) = games["mini-pvp"].requests
    assert fields["reply_to"] == "xcore:rpc:resp:discord"
    assert fields["correlation_id"] != request["operationId"]
    assert interaction.response.deferred is True
    assert interaction.replies == [
        ("Account linked to `Target` (`pid=7`). Return in-game in a moment.", True)
    ]


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("server_answer", "reply"),
    [
        (
            answer("rejected", reason="expired"),
            "`mini-pvp` refused to link `Target` (`pid=7`): `expired`.",
        ),
        (
            answer("failed", reason="link-failed"),
            (
                "Linking `Target` (`pid=7`) failed on `mini-pvp`: `link-failed`. If the "
                "code no longer works, get a new one in-game."
            ),
        ),
        (
            answer("unknown", reason="storage-outcome-unknown"),
            (
                "`mini-pvp` did not confirm the link of `Target` (`pid=7`), so it is not "
                "known whether it happened. Check /link-status before trying again."
            ),
        ),
        (
            lambda payload: refuse("invalid-request"),
            "`mini-pvp` refused to link `Target` (`pid=7`): `invalid-request`.",
        ),
        (
            # An error reply that is not a refusal does not prove nothing was written.
            lambda payload: fail("boom"),
            (
                "`mini-pvp` did not confirm the link of `Target` (`pid=7`), so it is not "
                "known whether it happened. Check /link-status before trying again."
            ),
        ),
        (
            lambda payload: answer("success")(
                dict(payload, operationId=str(uuid.uuid4()))
            ),
            (
                "`mini-pvp` did not confirm the link of `Target` (`pid=7`), so it is not "
                "known whether it happened. Check /link-status before trying again."
            ),
        ),
    ],
    ids=[
        "rejected",
        "failed",
        "unknown",
        "envelope-refusal",
        "envelope-error",
        "foreign-id",
    ],
)
async def test_link_reports_what_the_server_answered(
    server_answer: RpcAnswer, reply: str
) -> None:
    bot, games = await _bot()
    games["mini-pvp"].answers[OPERATION] = server_answer

    interaction = await _link(bot)

    assert interaction.replies == [(reply, True)]
    assert len(games["mini-pvp"].requests) == 1


@pytest.mark.asyncio
async def test_a_link_without_an_answer_is_unknown_and_not_sent_again(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(accounts, "ACCOUNT_OPERATION_TIMEOUT_MS", 50)
    bot, games = await _bot()
    games["mini-pvp"].answers[OPERATION] = lambda payload: None

    interaction = await _link(bot)

    assert interaction.replies == [
        (
            (
                "`mini-pvp` did not confirm the link of `Target` (`pid=7`), so it is not "
                "known whether it happened. Check /link-status before trying again."
            ),
            True,
        )
    ]
    assert len(games["mini-pvp"].requests) == 1
    assert await _no_fanout(bot, games)


@pytest.mark.asyncio
async def test_a_code_from_an_offline_server_is_not_redeemed_elsewhere() -> None:
    bot, games = await _bot(code=dict(CODE, server="gone"))

    interaction = await _link(bot)

    assert interaction.replies == [
        (
            (
                "Server `gone` that issued this code is offline. Try again when it is "
                "back, or get a new code in-game."
            ),
            True,
        )
    ]
    assert all(game.requests == [] for game in games.values())
    assert await _no_fanout(bot, games)


@pytest.mark.asyncio
async def test_an_expired_code_is_refused_without_a_request() -> None:
    bot, games = await _bot(code=dict(CODE, expiresAt=1))

    interaction = await _link(bot)

    assert interaction.replies == [("This link code has expired.", True)]
    assert all(game.requests == [] for game in games.values())


@pytest.mark.asyncio
async def test_a_code_for_someone_elses_account_is_refused_without_a_request() -> None:
    bot, games = await _bot(dict(TARGET, discord_id="999"))

    interaction = await _link(bot)

    assert interaction.replies == [
        ("This Mindustry account is already linked to another Discord account.", True)
    ]
    assert all(game.requests == [] for game in games.values())


@pytest.mark.asyncio
async def test_link_answers_in_the_users_discord_language() -> None:
    bot, games = await _bot()
    games["mini-pvp"].answers[OPERATION] = answer("success")

    interaction = await _link(bot, locale=discord.Locale.russian)

    assert interaction.replies == [
        ("Аккаунт `Target` (`pid=7`) привязан. Можно возвращаться в игру.", True)
    ]


async def _unlink(bot: XCoreDiscordBot, **kwargs: Any) -> _Interaction:
    interaction = _Interaction(**kwargs)
    await cmd_unlink(cast(Any, bot), cast(Any, interaction), 7)
    return interaction


@pytest.mark.asyncio
async def test_unlink_goes_to_the_server_the_player_is_on() -> None:
    bot, games = await _bot(dict(TARGET, online=True, online_server="other"))
    games["other"].answers[OPERATION] = answer("success")

    interaction = await _unlink(bot)

    (request,) = games["other"].sent(OPERATION)
    assert games["mini-pvp"].requests == []
    assert await _no_fanout(bot, games)
    assert request["action"] == "unlink"
    assert request["expectedLinkedAt"] == 1_700_000_000_000
    assert "code" not in request
    assert request["discord"]["discordId"] == "555"
    assert interaction.replies == [
        ("`Target` is unlinked from your Discord account.", True)
    ]


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "player",
    [
        dict(TARGET, online=False),
        # Its last server stopped sending heartbeats.
        dict(TARGET, online=True, online_server="crashed"),
    ],
    ids=["offline", "stale-server"],
)
async def test_unlink_of_an_offline_player_goes_to_the_writable_server(
    player: dict[str, Any],
) -> None:
    bot, games = await _bot(player)
    games["mini-pvp"].answers[OPERATION] = answer("success")

    await _unlink(bot)

    assert len(games["mini-pvp"].sent(OPERATION)) == 1
    assert games["other"].requests == []


@pytest.mark.asyncio
async def test_a_read_only_server_hands_the_unlink_to_the_writable_one() -> None:
    bot, games = await _bot(dict(TARGET, online=True, online_server="other"))
    games["other"].answers[OPERATION] = answer("rejected", reason="read-only")
    games["mini-pvp"].answers[OPERATION] = answer("success")

    interaction = await _unlink(bot)

    (refused,) = games["other"].sent(OPERATION)
    (accepted,) = games["mini-pvp"].sent(OPERATION)
    assert accepted["server"] == "mini-pvp"
    # A new operation, not a replay of the refused one.
    assert accepted["operationId"] != refused["operationId"]
    assert accepted["expectedLinkedAt"] == refused["expectedLinkedAt"]
    assert interaction.replies == [
        ("`Target` is unlinked from your Discord account.", True)
    ]


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("online_answer", "servers"),
    [
        (answer("unknown", reason="storage-outcome-unknown"), ("mini-pvp", "other")),
        (lambda payload: None, ("mini-pvp", "other")),
        (answer("rejected", reason="binding-mismatch"), ("mini-pvp", "other")),
        # The writable server is offline.
        (answer("rejected", reason="read-only"), ("other",)),
    ],
    ids=["unknown", "timeout", "other-refusal", "no-fallback-server"],
)
async def test_unlink_falls_back_only_after_a_read_only_refusal(
    monkeypatch: pytest.MonkeyPatch, online_answer: RpcAnswer, servers: tuple[str, ...]
) -> None:
    monkeypatch.setattr(accounts, "ACCOUNT_OPERATION_TIMEOUT_MS", 50)
    bot, games = await _bot(
        dict(TARGET, online=True, online_server="other"), servers=servers
    )
    games["other"].answers[OPERATION] = online_answer

    await _unlink(bot)

    assert len(games["other"].requests) == 1
    assert "mini-pvp" not in games or games["mini-pvp"].requests == []


@pytest.mark.asyncio
async def test_a_read_only_writable_server_is_not_asked_twice() -> None:
    bot, games = await _bot(TARGET)
    games["mini-pvp"].answers[OPERATION] = answer("rejected", reason="read-only")

    interaction = await _unlink(bot)

    assert len(games["mini-pvp"].requests) == 1
    assert games["other"].requests == []
    assert interaction.replies == [
        ("`mini-pvp` refused to unlink `Target`: `read-only`.", True)
    ]


@pytest.mark.asyncio
async def test_a_read_only_refusal_of_a_link_is_not_sent_elsewhere() -> None:
    bot, games = await _bot()
    games["mini-pvp"].answers[OPERATION] = answer("rejected", reason="read-only")

    interaction = await _link(bot)

    assert len(games["mini-pvp"].requests) == 1
    assert games["other"].requests == []
    assert interaction.replies == [
        ("`mini-pvp` refused to link `Target` (`pid=7`): `read-only`.", True)
    ]


@pytest.mark.asyncio
async def test_unlink_with_no_server_online_sends_nothing() -> None:
    bot, _ = await _bot(TARGET, servers=())

    interaction = await _unlink(bot)

    assert interaction.replies == [
        ("No server is online to unlink `Target`. Try again later.", True)
    ]
    assert await bot.bus.redis.keys("xcore:rpc:req:*") == []


@pytest.mark.asyncio
async def test_unlink_of_a_link_without_a_timestamp_expects_zero() -> None:
    legacy = {key: value for key, value in TARGET.items() if key != "discord_linked_at"}
    bot, games = await _bot(legacy)
    games["mini-pvp"].answers[OPERATION] = answer("rejected", reason="binding-mismatch")

    interaction = await _unlink(bot)

    assert games["mini-pvp"].sent(OPERATION)[0]["expectedLinkedAt"] == 0
    assert interaction.replies == [
        ("`mini-pvp` refused to unlink `Target`: `binding-mismatch`.", True)
    ]


@pytest.mark.asyncio
async def test_an_unanswered_unlink_is_unknown(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(accounts, "ACCOUNT_OPERATION_TIMEOUT_MS", 50)
    bot, games = await _bot(TARGET)
    games["mini-pvp"].answers[OPERATION] = lambda payload: None

    interaction = await _unlink(bot)

    assert interaction.replies == [
        (
            (
                "`mini-pvp` did not confirm the unlink of `Target`, so it is not known "
                "whether it happened. Check /link-status before trying again."
            ),
            True,
        )
    ]
    assert len(games["mini-pvp"].requests) == 1


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "server_answer",
    [
        lambda payload: cast(Any, []),
        answer("success", linkedAt="not-a-timestamp"),
        lambda payload: {},
    ],
    ids=["json-array", "wrong-field-type", "missing-fields"],
)
async def test_malformed_unlink_response_is_unknown_and_not_retried(
    server_answer: RpcAnswer,
) -> None:
    bot, games = await _bot(dict(TARGET, online=True, online_server="other"))
    games["other"].answers[OPERATION] = server_answer

    interaction = await _unlink(bot)

    assert interaction.response.deferred is True
    assert interaction.replies == [
        (
            (
                "`other` did not confirm the unlink of `Target`, so it is not known "
                "whether it happened. Check /link-status before trying again."
            ),
            True,
        )
    ]
    assert len(games["other"].requests) == 1
    assert games["mini-pvp"].requests == []
    assert await _no_fanout(bot, games)


@pytest.mark.asyncio
async def test_unlink_refuses_someone_elses_player_in_the_users_language() -> None:
    bot, games = await _bot(TARGET)

    interaction = await _unlink(
        bot, user=_User(id=556), locale=discord.Locale.ukrainian
    )

    assert interaction.replies == [
        ("Цей гравець не прив'язаний до вашого акаунта Discord.", True)
    ]
    assert all(game.requests == [] for game in games.values())


@pytest.mark.asyncio
async def test_cmd_link_status_renders_same_linked_account_output() -> None:
    bot, _ = await _bot(TARGET, ALT)
    interaction = _Interaction()

    await cmd_link_status(cast(Any, bot), cast(Any, interaction))

    assert interaction.response.sent == []
    assert len(interaction.response.embeds) == 1
    embed, ephemeral = interaction.response.embeds[0]
    assert ephemeral is True
    assert embed.title == "Linked Mindustry accounts"
    # Newest account first.
    assert embed.description == "`9` — Alt\n`7` — Target"


@pytest.mark.asyncio
async def test_a_language_chosen_with_the_language_command_wins() -> None:
    bot, _ = await _bot()
    bot.container.cases.store.cached_locale_pref = lambda discord_id: LocalePref(
        "uk", "manual"
    )
    interaction = _Interaction(locale=discord.Locale.russian)

    await cmd_link_status(cast(Any, bot), cast(Any, interaction))

    assert interaction.response.embeds[0][0].title == "Прив'язані акаунти Mindustry"


def _dm(content: str, author_id: int = 555) -> Any:
    channel = MagicMock(spec=discord.DMChannel)
    channel.send = AsyncMock()
    author = MagicMock(id=author_id, display_name="discord-user", bot=False)
    return MagicMock(id=4242, content=content, channel=channel, author=author)


async def _send_dm(
    player: dict[str, Any], *, pref: LocalePref | None = None
) -> tuple[XCoreDiscordBot, dict[str, GameServer], Any]:
    bot, games = await _bot(player)
    games["mini-pvp"].answers[OPERATION] = answer("success")
    bot.container.cases.store.get_locale_pref = AsyncMock(return_value=pref)
    message = _dm("abc123")
    await XCoreDiscordBot.on_message(bot, message)
    return bot, games, message


@pytest.mark.asyncio
async def test_a_code_sent_in_a_dm_links_and_answers_in_the_players_language() -> None:
    bot, games, message = await _send_dm(dict(UNLINKED, local_language="ru"))

    (request,) = games["mini-pvp"].sent(OPERATION)
    assert request["code"] == "ABC123"
    assert await _no_fanout(bot, games)
    message.channel.send.assert_awaited_once_with(
        "Аккаунт `Target` (`pid=7`) привязан. Можно возвращаться в игру."
    )


@pytest.mark.asyncio
async def test_a_dm_follows_a_stored_language_choice_over_the_game_language() -> None:
    _, _, message = await _send_dm(
        dict(UNLINKED, local_language="ru"), pref=LocalePref("uk", "manual")
    )

    message.channel.send.assert_awaited_once_with(
        "Акаунт `Target` (`pid=7`) прив'язано. Можна повертатися до гри."
    )


@pytest.mark.asyncio
async def test_a_dm_code_for_an_account_linked_elsewhere_is_refused() -> None:
    _, games, message = await _send_dm(dict(TARGET, discord_id="999"))

    assert games["mini-pvp"].requests == []
    message.channel.send.assert_awaited_once_with(
        "This Mindustry account is already linked to another Discord account."
    )
