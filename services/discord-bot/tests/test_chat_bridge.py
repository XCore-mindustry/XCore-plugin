import pytest
from unittest.mock import AsyncMock
from xcore_discord_bot.redis_bus import RedisBus
from xcore_discord_bot.settings import Settings
from xcore_discord_bot.contracts import ChatMessageV1


@pytest.fixture
def mock_redis():
    return AsyncMock()


@pytest.fixture
def settings():
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


@pytest.mark.asyncio
async def test_consume_game_chat_drops_bot_producer(settings, mock_redis):
    # Pass plain strings instead of bytes for test mock so dictionary access works naturally
    # (lettuce-core/redis-py usually return bytes for keys, but sometimes configured to decode strings)
    mock_redis.xreadgroup.return_value = [
        [
            b"xcore:evt:chat:message",
            [
                (
                    b"123-0",
                    {
                        b"producer": b"discord-bot",
                        b"payload_json": b'{"messageType": "chat.message", "messageVersion": 1, "authorName": "Player", "message": "Hi", "server": "test-server"}',
                    },
                )
            ],
        ]
    ]

    # We will raise a StopIteration exception in xreadgroup to exit the while loop
    mock_redis.xreadgroup.side_effect = [
        mock_redis.xreadgroup.return_value,
        KeyboardInterrupt("Stop loop"),
    ]

    bus = RedisBus(settings)
    bus._redis = mock_redis

    callback = AsyncMock()

    try:
        await bus.consume_game_chat(callback)
    except KeyboardInterrupt:
        pass

    callback.assert_not_called()
    mock_redis.xack.assert_called_once_with(
        b"xcore:evt:chat:message",
        f"{settings.redis_group_prefix}:discord-chat",
        b"123-0",
    )


@pytest.mark.asyncio
async def test_consume_game_chat_allows_server_producer(settings, mock_redis):
    mock_redis.xreadgroup.return_value = [
        [
            b"xcore:evt:chat:message",
            [
                (
                    b"123-0",
                    {
                        b"producer": b"server:test-server",
                        b"payload_json": b'{"messageType": "chat.message", "messageVersion": 1, "authorName": "Player", "message": "Hi", "server": "test-server"}',
                    },
                )
            ],
        ]
    ]

    mock_redis.xreadgroup.side_effect = [
        mock_redis.xreadgroup.return_value,
        KeyboardInterrupt("Stop loop"),
    ]

    bus = RedisBus(settings)
    bus._redis = mock_redis

    callback = AsyncMock()

    try:
        await bus.consume_game_chat(callback)
    except KeyboardInterrupt:
        pass

    callback.assert_called_once_with(
        ChatMessageV1(authorName="Player", message="Hi", server="test-server")
    )
    mock_redis.xack.assert_called_once_with(
        b"xcore:evt:chat:message",
        f"{settings.redis_group_prefix}:discord-chat",
        b"123-0",
    )
