import pytest
from unittest.mock import AsyncMock, MagicMock
from xcore_discord_bot.bot import XCoreDiscordBot
from xcore_discord_bot.settings import Settings
from xcore_discord_bot.contracts import GameChatMessage

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
        server_channel_map={"test-server": 1234},
        rpc_timeout_ms=5000,
    )

@pytest.mark.asyncio
async def test_game_chat_formatting_escapes_backticks(settings):
    bot = XCoreDiscordBot(settings, AsyncMock(), AsyncMock())
    
    mock_channel = AsyncMock()
    # Mocking discord.py channel which uses Messageable class
    import discord.abc
    mock_channel.__class__ = discord.abc.Messageable
    
    bot.get_channel = MagicMock(return_value=mock_channel)
    
    # We will test the inner dispatch function directly to verify formatting
    dispatch = None
    
    
    async def capture_callback(cb):
        nonlocal dispatch
        dispatch = cb
        raise KeyboardInterrupt()
        
    bot._bus.consume_game_chat = AsyncMock(side_effect=capture_callback)
    
    try:
        await bot._consume_game_chat()
    except KeyboardInterrupt:
        pass
        
    assert dispatch is not None
    
    # Test with normal message
    await dispatch(GameChatMessage(author_name="Player", message="Hello world", server="test-server"))
    mock_channel.send.assert_called_with("`Player: Hello world`")
    
    # Test with malicious backticks in author name
    await dispatch(GameChatMessage(author_name="`Admin`", message="Hello world", server="test-server"))
    mock_channel.send.assert_called_with("`Admin: Hello world`")
    
    # Test with malicious backticks in message
    await dispatch(GameChatMessage(author_name="Player", message="`rm -rf /`", server="test-server"))
    mock_channel.send.assert_called_with("`Player: rm -rf /`")
