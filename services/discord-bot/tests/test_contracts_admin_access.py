from __future__ import annotations

from xcore_discord_bot.contracts import DiscordAdminAccessChangedEvent


def test_discord_admin_access_changed_from_payload() -> None:
    payload = {
        "playerUuid": "uuid-7",
        "playerPid": "7",
        "discordId": "123456",
        "discordUsername": "osp54",
        "admin": True,
        "adminSource": "DISCORD_ROLE",
        "requestedBy": "boss",
        "reason": "/admin add",
        "server": "mini-pvp",
        "occurredAt": "123456789",
    }
    event = DiscordAdminAccessChangedEvent.from_payload(payload)
    assert event.player_uuid == "uuid-7"
    assert event.player_pid == 7
    assert event.discord_id == "123456"
    assert event.discord_username == "osp54"
    assert event.admin is True
    assert event.admin_source == "DISCORD_ROLE"
    assert event.requested_by == "boss"
    assert event.reason == "/admin add"
    assert event.server == "mini-pvp"
    assert event.occurred_at == 123456789
