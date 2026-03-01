from __future__ import annotations

import json
import pytest
from unittest.mock import AsyncMock

from xcore_discord_bot.redis_bus import RedisBus
from xcore_discord_bot.settings import Settings
from xcore_discord_bot.contracts import (
    BanEvent,
    GlobalChatEvent,
    PlayerJoinLeaveEvent,
    RawEvent,
    ServerHeartbeatEvent,
    ServerActionEvent,
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


# --- consume_admin_requests ---


@pytest.mark.asyncio
async def test_consume_admin_requests_dispatches_pid_and_server(
    settings: Settings, mock_redis: AsyncMock
) -> None:
    payload = {"pid": 42, "server": "test-server"}
    mock_redis.xreadgroup.side_effect = [
        [
            [
                b"xcore:evt:admin:request",
                [
                    (
                        b"1-0",
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
        await bus.consume_admin_requests(callback)
    except KeyboardInterrupt:
        pass

    callback.assert_called_once_with(42, "test-server")
    mock_redis.xack.assert_called_once_with(
        b"xcore:evt:admin:request",
        f"{settings.redis_group_prefix}:discord-admin-request",
        b"1-0",
    )


@pytest.mark.asyncio
async def test_consume_admin_requests_skips_malformed_payload(
    settings: Settings, mock_redis: AsyncMock
) -> None:
    mock_redis.xreadgroup.side_effect = [
        [
            [
                b"xcore:evt:admin:request",
                [
                    (
                        b"2-0",
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
        await bus.consume_admin_requests(callback)
    except KeyboardInterrupt:
        pass

    callback.assert_not_called()
    mock_redis.xack.assert_called_once()


@pytest.mark.asyncio
async def test_consume_admin_requests_callback_failure_not_acked(
    settings: Settings, mock_redis: AsyncMock
) -> None:
    payload = {"pid": 42, "server": "test-server"}
    mock_redis.xreadgroup.side_effect = [
        [
            [
                b"xcore:evt:admin:request",
                [
                    (
                        b"2-1",
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
        await bus.consume_admin_requests(callback)
    except KeyboardInterrupt:
        pass

    callback.assert_called_once_with(42, "test-server")
    mock_redis.xack.assert_not_called()


# --- consume_player_join_leave ---


@pytest.mark.asyncio
async def test_consume_player_join_leave_dispatches_join_event(
    settings: Settings, mock_redis: AsyncMock
) -> None:
    payload = {"playerName": "Alice", "server": "test-server", "join": True}
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
        PlayerJoinLeaveEvent(player_name="Alice", server="test-server", joined=True)
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
    payload = {"playerName": "Bob", "server": "test-server", "join": False}
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
        PlayerJoinLeaveEvent(player_name="Bob", server="test-server", joined=False)
    )


@pytest.mark.asyncio
async def test_consume_server_actions_dispatches_event(
    settings: Settings, mock_redis: AsyncMock
) -> None:
    payload = {"message": "Server started", "server": "test-server"}
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
        ServerActionEvent(message="Server started", server="test-server")
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
        "uuid": "u-1",
        "name": "pizduk",
        "admin_name": "admin",
        "reason": "rule",
        "expire_date": "2026-03-01T10:00:00+00:00",
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
        BanEvent(
            uuid="u-1",
            ip=None,
            name="pizduk",
            admin_name="admin",
            reason="rule",
            expire_date="2026-03-01T10:00:00+00:00",
        )
    )
    mock_redis.xack.assert_called_once_with(
        b"xcore:evt:moderation:ban",
        f"{settings.redis_group_prefix}:discord-ban",
        b"7-0",
    )


@pytest.mark.asyncio
async def test_consume_global_chat_dispatches_event(
    settings: Settings, mock_redis: AsyncMock
) -> None:
    payload = {"authorName": "Alice", "message": "Hi", "server": "test-server"}
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
        GlobalChatEvent(author_name="Alice", message="Hi", server="test-server")
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
    payload = {"authorName": "Alice", "message": "Hi", "server": "test-server"}
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
async def test_consume_raw_events_dispatches_event(
    settings: Settings, mock_redis: AsyncMock
) -> None:
    mock_redis.xreadgroup.side_effect = [
        [
            [
                b"xcore:evt:raw",
                [
                    (
                        b"9-0",
                        {
                            "event_type": "event.unknown",
                            "payload_json": '{"x":42}',
                        },
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
        await bus.consume_raw_events(callback)
    except KeyboardInterrupt:
        pass

    callback.assert_called_once_with(
        RawEvent(event_type="event.unknown", payload={"x": 42})
    )
    mock_redis.xack.assert_called_once_with(
        b"xcore:evt:raw",
        f"{settings.redis_group_prefix}:discord-raw",
        b"9-0",
    )


@pytest.mark.asyncio
async def test_consume_server_heartbeats_dispatches_and_updates_registry(
    settings: Settings, mock_redis: AsyncMock
) -> None:
    payload = {
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
        ServerHeartbeatEvent(
            server_name="mini-pvp",
            discord_channel_id=321,
            players=3,
            max_players=12,
            version="v1",
        )
    )
    assert server_registry.get_channel_for_server("mini-pvp") == 321


@pytest.mark.asyncio
async def test_consume_raw_events_updates_registry_for_heartbeat_type(
    settings: Settings, mock_redis: AsyncMock
) -> None:
    payload = {
        "serverName": "mini-pvp",
        "discordChannelId": 555,
        "players": 2,
        "maxPlayers": 10,
        "version": "v2",
    }
    mock_redis.xreadgroup.side_effect = [
        [
            [
                b"xcore:evt:raw",
                [
                    (
                        b"11-0",
                        {
                            "event_type": "org.xcore.plugin.event.SocketEvents$ServerHeartbeatEvent",
                            "payload_json": json.dumps(payload),
                        },
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
        await bus.consume_raw_events(callback)
    except KeyboardInterrupt:
        pass

    callback.assert_called_once_with(
        RawEvent(
            event_type="org.xcore.plugin.event.SocketEvents$ServerHeartbeatEvent",
            payload=payload,
        )
    )
    assert server_registry.get_channel_for_server("mini-pvp") == 555
