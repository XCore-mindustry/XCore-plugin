from __future__ import annotations

from fakeredis.aioredis import FakeRedis
import pytest

from xcore_discord_bot.redis_bus import RedisBus
from xcore_discord_bot.settings import Settings


def _settings() -> Settings:
    return Settings(
        discord_token="token",
        discord_admin_role_id=100,
        discord_general_admin_role_id=100,
        discord_map_reviewer_role_id=100,
        discord_private_channel_id=200,
        redis_url="redis://127.0.0.1:6379",
        redis_group_prefix="xcore:cg",
        redis_consumer_name="bot",
        mongo_uri="mongodb://127.0.0.1:27017",
        mongo_db_name="xcore",
        server_channel_map={"mini-pvp": 123},
        rpc_timeout_ms=5000,
    )


@pytest.mark.asyncio
async def test_claim_idempotency_nx() -> None:
    bus = RedisBus(_settings())
    bus._redis = FakeRedis(decode_responses=True)

    first = await bus.claim_idempotency("cmd:test", ttl_seconds=60)
    second = await bus.claim_idempotency("cmd:test", ttl_seconds=60)

    assert first is True
    assert second is False

    await bus.close()


@pytest.mark.asyncio
async def test_publish_maps_load_payload() -> None:
    settings = _settings()
    bus = RedisBus(settings)
    redis = FakeRedis(decode_responses=True)
    bus._redis = redis

    await bus.publish_maps_load(
        server="mini-pvp",
        files=[{"url": "https://example/map.msav", "filename": "map.msav"}],
    )

    entries = await redis.xread({"xcore:cmd:maps-load:mini-pvp": "0-0"}, count=1)
    assert entries
    _stream, messages = entries[0]
    _id, fields = messages[0]

    assert fields["event_type"] == "maps.load"
    assert fields["server"] == "mini-pvp"
    assert "map.msav" in fields["payload_json"]

    await bus.close()


@pytest.mark.asyncio
async def test_publish_admin_confirm_and_remove_admin_payloads() -> None:
    settings = _settings()
    bus = RedisBus(settings)
    redis = FakeRedis(decode_responses=True)
    bus._redis = redis

    await bus.publish_admin_confirm(uuid_value="uuid-1", server="mini-pvp")
    await bus.publish_remove_admin(uuid_value="uuid-2")
    await bus.publish_pardon_player(uuid_value="uuid-3")
    await bus.publish_reload_player_data_cache()

    confirm_entries = await redis.xread(
        {"xcore:cmd:admin-confirm:mini-pvp": "0-0"}, count=1
    )
    remove_entries = await redis.xread(
        {"xcore:cmd:remove-admin:mini-pvp": "0-0"}, count=1
    )
    pardon_entries = await redis.xread(
        {"xcore:cmd:pardon-player:mini-pvp": "0-0"}, count=1
    )
    reload_entries = await redis.xread(
        {"xcore:cmd:reload-cache:mini-pvp": "0-0"}, count=1
    )

    assert confirm_entries
    assert remove_entries
    assert pardon_entries
    assert reload_entries

    assert confirm_entries[0][1][0][1]["event_type"] == "admin.confirm"
    assert "uuid-1" in confirm_entries[0][1][0][1]["payload_json"]

    assert remove_entries[0][1][0][1]["event_type"] == "admin.remove"
    assert "uuid-2" in remove_entries[0][1][0][1]["payload_json"]

    assert pardon_entries[0][1][0][1]["event_type"] == "moderation.pardon"
    assert "uuid-3" in pardon_entries[0][1][0][1]["payload_json"]

    assert reload_entries[0][1][0][1]["event_type"] == "cache.reload_player_data"

    await bus.close()
