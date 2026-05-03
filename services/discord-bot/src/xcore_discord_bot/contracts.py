from __future__ import annotations

from typing import Any

from xcore_protocol.generated import (
    ChatGlobalV1,
    ChatMessageV1,
    DiscordLinkStatusChangedV1,
    ModerationBanCreatedV1,
    ModerationMuteCreatedV1,
    ModerationVoteKickCreatedV1,
    PlayerJoinLeaveV1,
    ServerActionV1,
    ServerHeartbeatV1,
)


def parse_chat_message_payload(payload: dict[str, Any]) -> ChatMessageV1:
    return ChatMessageV1.from_payload(payload)


def parse_player_join_leave_payload(payload: dict[str, Any]) -> PlayerJoinLeaveV1:
    return PlayerJoinLeaveV1.from_payload(payload)


def parse_server_action_payload(payload: dict[str, Any]) -> ServerActionV1:
    return ServerActionV1.from_payload(payload)


def parse_ban_payload(payload: dict[str, Any]) -> ModerationBanCreatedV1:
    return ModerationBanCreatedV1.from_payload(payload)


def parse_mute_payload(payload: dict[str, Any]) -> ModerationMuteCreatedV1:
    return ModerationMuteCreatedV1.from_payload(payload)


def parse_vote_kick_payload(payload: dict[str, Any]) -> ModerationVoteKickCreatedV1:
    return ModerationVoteKickCreatedV1.from_payload(payload)


def parse_global_chat_payload(payload: dict[str, Any]) -> ChatGlobalV1:
    return ChatGlobalV1.from_payload(payload)


def parse_discord_link_status_payload(
    payload: dict[str, Any],
) -> DiscordLinkStatusChangedV1:
    return DiscordLinkStatusChangedV1.from_payload(payload)


def parse_server_heartbeat_payload(payload: dict[str, Any]) -> ServerHeartbeatV1:
    return ServerHeartbeatV1.from_payload(payload)


__all__ = [
    "ChatGlobalV1",
    "ChatMessageV1",
    "DiscordLinkStatusChangedV1",
    "ModerationBanCreatedV1",
    "ModerationMuteCreatedV1",
    "ModerationVoteKickCreatedV1",
    "parse_chat_message_payload",
    "parse_ban_payload",
    "parse_discord_link_status_payload",
    "parse_global_chat_payload",
    "parse_mute_payload",
    "parse_player_join_leave_payload",
    "parse_server_action_payload",
    "parse_server_heartbeat_payload",
    "parse_vote_kick_payload",
    "PlayerJoinLeaveV1",
    "ServerActionV1",
    "ServerHeartbeatV1",
]
