from __future__ import annotations

import json
import pytest
from unittest.mock import AsyncMock
from xcore_protocol.generated.discord import (
    DiscordLinkStatusChangedV1,
    DiscordLinkStatusChangedV1Action,
)
from xcore_protocol.generated.shared import (
    ActorRefV1,
    DiscordIdentityRefV1,
    ExpirationInfoV1,
    PlayerRefV1,
    VoteKickParticipantV1,
)

from xcore_discord_bot.redis_bus import RedisBus
from xcore_discord_bot.settings import Settings
from xcore_discord_bot.contracts import (
    ChatGlobalV1,
    ChatMessageV1,
    ModerationBanCreatedV1,
    ModerationMuteCreatedV1,
    ModerationVoteKickCreatedV1,
    PlayerJoinLeaveV1,
    ServerActionV1,
    ServerHeartbeatV1,
)
from xcore_discord_bot.registry import server_registry


@pytest.fixture
def settings() -> Settings:
    return Settings(
        discord_token="fake",
        discord_admin_role_id=1,
        discord_general_admin_role_id=1,
        discord_map_reviewer_role_id=1,
        discord_private_channel_id=2,
        redis_url="redis://127.0.0.1",
        redis_group_prefix="xcore:cg",
        redis_consumer_name="bot",
        mongo_uri="mongodb://localhost",
        mongo_db_name="test",
        rpc_timeout_ms=5000,
    )


@pytest.fixture
def mock_redis() -> AsyncMock:
    return AsyncMock()


@pytest.fixture(autouse=True)
def clear_registry() -> None:
    with server_registry._lock:
        server_registry._servers.clear()


def test_stream_maxlen_policy() -> None:
    assert RedisBus._stream_maxlen("xcore:evt:chat:message") == 50_000
    assert RedisBus._stream_maxlen("xcore:cmd:remove-admin:mini-pvp") == 10_000
    assert RedisBus._stream_maxlen("xcore:rpc:req:mini-pvp") == 5_000
    assert RedisBus._stream_maxlen("xcore:rpc:resp:discord") == 20_000
    assert RedisBus._stream_maxlen("xcore:dlq:evt") == 100_000


# --- consume_player_join_leave ---


@pytest.mark.asyncio
async def test_consume_player_join_leave_dispatches_join_event(
    settings: Settings, mock_redis: AsyncMock
) -> None:
    payload = {
        "messageType": "player.join-leave",
        "messageVersion": 1,
        "playerName": "Alice",
        "server": "test-server",
        "joined": True,
    }
    mock_redis.xreadgroup.side_effect = [
        [
            [
                b"xcore:evt:player:joinleave",
                [
                    (
                        b"3-0",
                        {"payload_json": json.dumps(payload)},
                    )
                ],
            ]
        ],
        KeyboardInterrupt("stop"),
    ]

    bus = RedisBus(settings)
    bus._redis = mock_redis

    callback = AsyncMock()
    try:
        await bus.consume_player_join_leave(callback)
    except KeyboardInterrupt:
        pass

    callback.assert_called_once_with(
        PlayerJoinLeaveV1(playerName="Alice", server="test-server", joined=True)
    )
    mock_redis.xack.assert_called_once_with(
        b"xcore:evt:player:joinleave",
        f"{settings.redis_group_prefix}:discord-join-leave",
        b"3-0",
    )


@pytest.mark.asyncio
async def test_consume_player_join_leave_dispatches_leave_event(
    settings: Settings, mock_redis: AsyncMock
) -> None:
    payload = {
        "messageType": "player.join-leave",
        "messageVersion": 1,
        "playerName": "Bob",
        "server": "test-server",
        "joined": False,
    }
    mock_redis.xreadgroup.side_effect = [
        [
            [
                b"xcore:evt:player:joinleave",
                [
                    (
                        b"4-0",
                        {"payload_json": json.dumps(payload)},
                    )
                ],
            ]
        ],
        KeyboardInterrupt("stop"),
    ]

    bus = RedisBus(settings)
    bus._redis = mock_redis

    callback = AsyncMock()
    try:
        await bus.consume_player_join_leave(callback)
    except KeyboardInterrupt:
        pass

    callback.assert_called_once_with(
        PlayerJoinLeaveV1(playerName="Bob", server="test-server", joined=False)
    )


@pytest.mark.asyncio
async def test_consume_server_actions_dispatches_event(
    settings: Settings, mock_redis: AsyncMock
) -> None:
    payload = {
        "messageType": "server.action",
        "messageVersion": 1,
        "message": "Server started",
        "server": "test-server",
    }
    mock_redis.xreadgroup.side_effect = [
        [
            [
                b"xcore:evt:server:action",
                [
                    (
                        b"5-0",
                        {"payload_json": json.dumps(payload)},
                    )
                ],
            ]
        ],
        KeyboardInterrupt("stop"),
    ]

    bus = RedisBus(settings)
    bus._redis = mock_redis

    callback = AsyncMock()
    try:
        await bus.consume_server_actions(callback)
    except KeyboardInterrupt:
        pass

    callback.assert_called_once_with(
        ServerActionV1(message="Server started", server="test-server")
    )
    mock_redis.xack.assert_called_once_with(
        b"xcore:evt:server:action",
        f"{settings.redis_group_prefix}:discord-server-action",
        b"5-0",
    )


@pytest.mark.asyncio
async def test_consume_server_actions_skips_malformed_payload(
    settings: Settings, mock_redis: AsyncMock
) -> None:
    mock_redis.xreadgroup.side_effect = [
        [
            [
                b"xcore:evt:server:action",
                [
                    (
                        b"6-0",
                        {"payload_json": "not-json"},
                    )
                ],
            ]
        ],
        KeyboardInterrupt("stop"),
    ]

    bus = RedisBus(settings)
    bus._redis = mock_redis

    callback = AsyncMock()
    try:
        await bus.consume_server_actions(callback)
    except KeyboardInterrupt:
        pass

    callback.assert_not_called()
    mock_redis.xack.assert_called_once()


@pytest.mark.asyncio
async def test_consume_bans_dispatches_event(
    settings: Settings, mock_redis: AsyncMock
) -> None:
    payload = {
        "messageType": "moderation.ban.created",
        "messageVersion": 1,
        "target": {"playerUuid": "u-1", "playerName": "pizduk"},
        "actor": {"actorName": "admin", "actorDiscordId": "123"},
        "reason": "rule",
        "expiration": {"expiresAt": "2026-03-01T10:00:00+00:00"},
    }
    mock_redis.xreadgroup.side_effect = [
        [
            [
                b"xcore:evt:moderation:ban",
                [
                    (
                        b"7-0",
                        {"payload_json": json.dumps(payload)},
                    )
                ],
            ]
        ],
        KeyboardInterrupt("stop"),
    ]

    bus = RedisBus(settings)
    bus._redis = mock_redis

    callback = AsyncMock()
    try:
        await bus.consume_bans(callback)
    except KeyboardInterrupt:
        pass

    callback.assert_called_once_with(
        ModerationBanCreatedV1(
            target=PlayerRefV1(playerUuid="u-1", playerName="pizduk"),
            actor=ActorRefV1(
                actorName="admin",
                actorDiscordId="123",
            ),
            reason="rule",
            expiration=ExpirationInfoV1(expiresAt="2026-03-01T10:00:00+00:00"),
        )
    )
    mock_redis.xack.assert_called_once_with(
        b"xcore:evt:moderation:ban",
        f"{settings.redis_group_prefix}:discord-ban",
        b"7-0",
    )


@pytest.mark.asyncio
async def test_consume_mutes_dispatches_event(
    settings: Settings, mock_redis: AsyncMock
) -> None:
    payload = {
        "messageType": "moderation.mute.created",
        "messageVersion": 1,
        "target": {"playerUuid": "u-1", "playerName": "pizduk"},
        "actor": {"actorName": "admin", "actorDiscordId": "456"},
        "reason": "rule",
        "expiration": {"expiresAt": "2026-03-01T10:00:00+00:00"},
    }
    mock_redis.xreadgroup.side_effect = [
        [
            [
                b"xcore:evt:moderation:mute",
                [
                    (
                        b"7-1",
                        {"payload_json": json.dumps(payload)},
                    )
                ],
            ]
        ],
        KeyboardInterrupt("stop"),
    ]

    bus = RedisBus(settings)
    bus._redis = mock_redis

    callback = AsyncMock()
    try:
        await bus.consume_mutes(callback)
    except KeyboardInterrupt:
        pass

    callback.assert_called_once_with(
        ModerationMuteCreatedV1(
            target=PlayerRefV1(playerUuid="u-1", playerName="pizduk"),
            actor=ActorRefV1(
                actorName="admin",
                actorDiscordId="456",
            ),
            reason="rule",
            expiration=ExpirationInfoV1(expiresAt="2026-03-01T10:00:00+00:00"),
        )
    )
    mock_redis.xack.assert_called_once_with(
        b"xcore:evt:moderation:mute",
        f"{settings.redis_group_prefix}:discord-mute",
        b"7-1",
    )


@pytest.mark.asyncio
async def test_consume_vote_kicks_dispatches_event(
    settings: Settings, mock_redis: AsyncMock
) -> None:
    payload = {
        "messageType": "moderation.vote-kick.created",
        "messageVersion": 1,
        "target": {
            "playerUuid": "uuid-target",
            "playerPid": 42,
            "playerName": "Target",
        },
        "actor": {"actorName": "Starter", "actorDiscordId": "123456"},
        "reason": "griefing",
        "votesFor": [{"playerName": "Starter", "playerPid": 7, "discordId": "123456"}],
        "votesAgainst": [
            {"playerName": "Voter2", "playerPid": 8, "discordId": "654321"}
        ],
    }
    mock_redis.xreadgroup.side_effect = [
        [
            [
                b"xcore:evt:moderation:votekick",
                [
                    (
                        b"7-2",
                        {"payload_json": json.dumps(payload)},
                    )
                ],
            ]
        ],
        KeyboardInterrupt("stop"),
    ]

    bus = RedisBus(settings)
    bus._redis = mock_redis

    callback = AsyncMock()
    try:
        await bus.consume_vote_kicks(callback)
    except KeyboardInterrupt:
        pass

    callback.assert_called_once_with(
        ModerationVoteKickCreatedV1(
            target=PlayerRefV1(
                playerUuid="uuid-target",
                playerPid=42,
                playerName="Target",
            ),
            actor=ActorRefV1(
                actorName="Starter",
                actorDiscordId="123456",
            ),
            reason="griefing",
            votesFor=(
                VoteKickParticipantV1(
                    playerName="Starter",
                    playerPid=7,
                    discordId="123456",
                ),
            ),
            votesAgainst=(
                VoteKickParticipantV1(
                    playerName="Voter2",
                    playerPid=8,
                    discordId="654321",
                ),
            ),
        )
    )


@pytest.mark.asyncio
async def test_consume_discord_link_status_changed_dispatches_generated_event(
    settings: Settings, mock_redis: AsyncMock
) -> None:
    payload = {
        "messageType": "discord.link.status-changed",
        "messageVersion": 1,
        "player": {"playerUuid": "uuid-7", "playerPid": 7, "playerName": "Target"},
        "discord": {"discordId": "123456", "discordUsername": "osp54"},
        "action": "linked",
        "server": "mini-pvp",
        "occurredAt": "2026-05-02T10:20:30.123+00:00",
    }
    mock_redis.xreadgroup.side_effect = [
        [
            [
                b"xcore:evt:discord:link-status",
                [
                    (
                        b"7-4",
                        {"payload_json": json.dumps(payload)},
                    )
                ],
            ]
        ],
        KeyboardInterrupt("stop"),
    ]

    bus = RedisBus(settings)
    bus._redis = mock_redis

    callback = AsyncMock()
    try:
        await bus.consume_discord_link_status_changed(callback)
    except KeyboardInterrupt:
        pass

    callback.assert_called_once_with(
        DiscordLinkStatusChangedV1(
            player=PlayerRefV1(playerUuid="uuid-7", playerPid=7, playerName="Target"),
            discord=DiscordIdentityRefV1(
                discordId="123456",
                discordUsername="osp54",
            ),
            action=DiscordLinkStatusChangedV1Action.LINKED,
            server="mini-pvp",
            occurredAt="2026-05-02T10:20:30.123+00:00",
        )
    )
    mock_redis.xack.assert_called_once_with(
        b"xcore:evt:discord:link-status",
        f"{settings.redis_group_prefix}:discord-link-status",
        b"7-4",
    )


@pytest.mark.asyncio
async def test_consume_vote_kicks_dispatches_canonical_generated_payload(
    settings: Settings, mock_redis: AsyncMock
) -> None:
    payload = {
        "messageType": "moderation.vote-kick.created",
        "messageVersion": 1,
        "target": {
            "playerUuid": "uuid-target",
            "playerPid": 42,
            "playerName": "Target",
        },
        "actor": {"actorName": "Starter", "actorDiscordId": "123456"},
        "reason": "griefing",
        "votesFor": [{"playerName": "Starter", "playerPid": 7, "discordId": "123456"}],
        "votesAgainst": [
            {"playerName": "Voter2", "playerPid": 8, "discordId": "654321"}
        ],
    }
    mock_redis.xreadgroup.side_effect = [
        [
            [
                b"xcore:evt:moderation:votekick",
                [
                    (
                        b"7-3",
                        {"payload_json": json.dumps(payload)},
                    )
                ],
            ]
        ],
        KeyboardInterrupt("stop"),
    ]

    bus = RedisBus(settings)
    bus._redis = mock_redis

    callback = AsyncMock()
    try:
        await bus.consume_vote_kicks(callback)
    except KeyboardInterrupt:
        pass

    callback.assert_called_once_with(
        ModerationVoteKickCreatedV1(
            target=PlayerRefV1(
                playerUuid="uuid-target",
                playerPid=42,
                playerName="Target",
            ),
            actor=ActorRefV1(actorName="Starter", actorDiscordId="123456"),
            reason="griefing",
            votesFor=(
                VoteKickParticipantV1(
                    playerName="Starter",
                    playerPid=7,
                    discordId="123456",
                ),
            ),
            votesAgainst=(
                VoteKickParticipantV1(
                    playerName="Voter2",
                    playerPid=8,
                    discordId="654321",
                ),
            ),
        )
    )


@pytest.mark.asyncio
async def test_consume_global_chat_dispatches_event(
    settings: Settings, mock_redis: AsyncMock
) -> None:
    payload = {
        "messageType": "chat.global",
        "messageVersion": 1,
        "authorName": "Alice",
        "message": "Hi",
        "server": "test-server",
    }
    mock_redis.xreadgroup.side_effect = [
        [
            [
                b"xcore:evt:chat:global",
                [
                    (
                        b"8-0",
                        {"payload_json": json.dumps(payload)},
                    )
                ],
            ]
        ],
        KeyboardInterrupt("stop"),
    ]

    bus = RedisBus(settings)
    bus._redis = mock_redis

    callback = AsyncMock()
    try:
        await bus.consume_global_chat(callback)
    except KeyboardInterrupt:
        pass

    callback.assert_called_once_with(
        ChatGlobalV1(authorName="Alice", message="Hi", server="test-server")
    )
    mock_redis.xack.assert_called_once_with(
        b"xcore:evt:chat:global",
        f"{settings.redis_group_prefix}:discord-global-chat",
        b"8-0",
    )


@pytest.mark.asyncio
async def test_consume_global_chat_callback_failure_not_acked(
    settings: Settings, mock_redis: AsyncMock
) -> None:
    payload = {
        "messageType": "chat.global",
        "messageVersion": 1,
        "authorName": "Alice",
        "message": "Hi",
        "server": "test-server",
    }
    mock_redis.xreadgroup.side_effect = [
        [
            [
                b"xcore:evt:chat:global",
                [
                    (
                        b"8-1",
                        {"payload_json": json.dumps(payload)},
                    )
                ],
            ]
        ],
        KeyboardInterrupt("stop"),
    ]

    bus = RedisBus(settings)
    bus._redis = mock_redis

    callback = AsyncMock(side_effect=RuntimeError("discord down"))
    try:
        await bus.consume_global_chat(callback)
    except KeyboardInterrupt:
        pass

    callback.assert_called_once()
    mock_redis.xack.assert_not_called()


@pytest.mark.asyncio
async def test_consume_server_heartbeats_dispatches_and_updates_registry(
    settings: Settings, mock_redis: AsyncMock
) -> None:
    payload = {
        "messageType": "server.heartbeat",
        "messageVersion": 1,
        "serverName": "mini-pvp",
        "discordChannelId": 321,
        "players": 3,
        "maxPlayers": 12,
        "version": "v1",
    }
    mock_redis.xreadgroup.side_effect = [
        [
            [
                b"xcore:evt:server:heartbeat",
                [
                    (
                        b"10-0",
                        {"payload_json": json.dumps(payload)},
                    )
                ],
            ]
        ],
        KeyboardInterrupt("stop"),
    ]

    bus = RedisBus(settings)
    bus._redis = mock_redis

    callback = AsyncMock()
    try:
        await bus.consume_server_heartbeats(callback)
    except KeyboardInterrupt:
        pass

    callback.assert_called_once_with(
        ServerHeartbeatV1(
            serverName="mini-pvp",
            discordChannelId=321,
            players=3,
            maxPlayers=12,
            version="v1",
        )
    )
    assert server_registry.get_channel_for_server("mini-pvp") == 321


@pytest.mark.asyncio
@pytest.mark.asyncio
async def test_consume_game_chat_dispatches_generated_event(
    settings: Settings, mock_redis: AsyncMock
) -> None:
    payload = {
        "messageType": "chat.message",
        "messageVersion": 1,
        "authorName": "Alice",
        "message": "Hi",
        "server": "test-server",
    }
    mock_redis.xreadgroup.side_effect = [
        [
            [
                b"xcore:evt:chat:message",
                [
                    (
                        b"12-0",
                        {"payload_json": json.dumps(payload)},
                    )
                ],
            ]
        ],
        KeyboardInterrupt("stop"),
    ]

    bus = RedisBus(settings)
    bus._redis = mock_redis

    callback = AsyncMock()
    try:
        await bus.consume_game_chat(callback)
    except KeyboardInterrupt:
        pass

    callback.assert_called_once_with(
        ChatMessageV1(authorName="Alice", message="Hi", server="test-server")
    )
    mock_redis.xack.assert_called_once_with(
        b"xcore:evt:chat:message",
        f"{settings.redis_group_prefix}:discord-chat",
        b"12-0",
    )
