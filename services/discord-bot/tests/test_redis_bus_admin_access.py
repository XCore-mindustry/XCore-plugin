from __future__ import annotations


import pytest
from xcore_protocol.generated.discord import DiscordAdminAccessChangedCommandV1
from xcore_protocol.generated.shared import ActorRefV1ActorType

from xcore_discord_bot.redis_bus import RedisBus
from xcore_discord_bot.settings import Settings


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


@pytest.mark.asyncio
async def test_publish_discord_admin_access_changed_publishes_for_all_servers(
    settings: Settings,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    bus = RedisBus(settings)
    captured: dict[str, object] = {}

    async def fake_publish_for_all_servers(**kwargs):
        captured.update(kwargs)

    monkeypatch.setattr(bus, "_publish_for_all_servers", fake_publish_for_all_servers)

    await bus.publish_discord_admin_access_changed(
        player_uuid="uuid-7",
        player_pid=7,
        player_name="Target",
        discord_id="123",
        discord_username="discord-user",
        admin=True,
        source_name="DISCORD_ROLE",
        source_type=ActorRefV1ActorType.DISCORD,
        actor_name="boss",
        actor_discord_id=None,
        actor_type=ActorRefV1ActorType.DISCORD,
        reason="/admin add",
    )

    assert captured["stream_prefix"] == "xcore:cmd:discord-admin-access"
    assert captured["event_type"] == "discord.admin-access.changed.command"


@pytest.mark.asyncio
async def test_publish_discord_admin_access_changed_produces_valid_canonical_payload(
    settings: Settings,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    bus = RedisBus(settings)
    captured_payload: dict[str, object] = {}

    async def fake_publish_for_all_servers(**kwargs):
        captured_payload["payload_builder"] = kwargs["payload_builder"]

    monkeypatch.setattr(bus, "_publish_for_all_servers", fake_publish_for_all_servers)

    await bus.publish_discord_admin_access_changed(
        player_uuid="uuid-7",
        player_pid=7,
        player_name="Target",
        discord_id="123",
        discord_username="discord-user",
        admin=True,
        source_name="DISCORD_ROLE",
        source_type=ActorRefV1ActorType.DISCORD,
        actor_name="boss",
        actor_discord_id=None,
        actor_type=ActorRefV1ActorType.DISCORD,
        reason="/admin add",
    )

    payload_builder = captured_payload["payload_builder"]
    assert callable(payload_builder)

    wire = payload_builder("mini-pvp")
    parsed = DiscordAdminAccessChangedCommandV1.from_payload(wire)

    assert parsed.player.playerUuid == "uuid-7"
    assert parsed.player.playerPid == 7
    assert parsed.player.playerName == "Target"
    assert parsed.discord.discordId == "123"
    assert parsed.discord.discordUsername == "discord-user"
    assert parsed.admin is True
    assert parsed.source.actorName == "DISCORD_ROLE"
    assert parsed.source.actorType == ActorRefV1ActorType.DISCORD
    assert parsed.actor.actorName == "boss"
    assert parsed.actor.actorType == ActorRefV1ActorType.DISCORD
    assert parsed.reason == "/admin add"
    assert parsed.server == "mini-pvp"
