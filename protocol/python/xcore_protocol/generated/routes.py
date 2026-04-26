"""Generated canonical route descriptors."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from .maps import (
    MapsListRequestV1,
    MapsListResponseV1,
    MapsRemoveRequestV1,
    MapsRemoveResponseV1,
    MapsLoadCommandV1,
)

from .chat import (
    ChatMessageV1,
    ChatGlobalV1,
    ChatDiscordIngressCommandV1,
    ServerActionV1,
    PlayerJoinLeaveV1,
    ServerHeartbeatV1,
)

from .discord import (
    DiscordLinkConfirmCommandV1,
    DiscordUnlinkCommandV1,
    DiscordLinkStatusChangedV1,
    DiscordAdminAccessChangedCommandV1,
)

from .moderation import (
    ModerationBanCreatedV1,
    ModerationMuteCreatedV1,
    ModerationVoteKickCreatedV1,
    ModerationKickBannedCommandV1,
    ModerationPardonCommandV1,
    ModerationAuditAppendedV1,
)

@dataclass(frozen=True, slots=True)
class RouteResponseDescriptor:
    messageType: str
    messageVersion: int
    payloadType: type[Any]
    stream: str


@dataclass(frozen=True, slots=True)
class RouteDescriptor:
    family: str
    methodName: str
    messageType: str
    messageVersion: int
    payloadType: type[Any]
    kind: str
    stream: str
    targetScope: str
    ttlMs: int
    replayable: bool
    idempotentConsumerRecommended: bool
    owner: str
    response: RouteResponseDescriptor | None = None

MAPS_LIST_REQUEST_V1 = RouteDescriptor(
    family='maps',
    methodName='mapsListRequestV1Route',
    messageType='maps.list.request',
    messageVersion=1,
    payloadType=MapsListRequestV1,
    kind='rpc-request',
    stream='xcore:rpc:req:{server}',
    targetScope='server',
    ttlMs=10000,
    replayable=False,
    idempotentConsumerRecommended=False,
    owner='maps',
    response=RouteResponseDescriptor(
        messageType='maps.list.response',
        messageVersion=1,
        payloadType=MapsListResponseV1,
        stream='xcore:rpc:resp:{requester}',
    ),
)

MAPS_REMOVE_REQUEST_V1 = RouteDescriptor(
    family='maps',
    methodName='mapsRemoveRequestV1Route',
    messageType='maps.remove.request',
    messageVersion=1,
    payloadType=MapsRemoveRequestV1,
    kind='rpc-request',
    stream='xcore:rpc:req:{server}',
    targetScope='server',
    ttlMs=10000,
    replayable=False,
    idempotentConsumerRecommended=True,
    owner='maps',
    response=RouteResponseDescriptor(
        messageType='maps.remove.response',
        messageVersion=1,
        payloadType=MapsRemoveResponseV1,
        stream='xcore:rpc:resp:{requester}',
    ),
)

MAPS_LOAD_COMMAND_V1 = RouteDescriptor(
    family='maps',
    methodName='mapsLoadCommandV1Route',
    messageType='maps.load.command',
    messageVersion=1,
    payloadType=MapsLoadCommandV1,
    kind='command',
    stream='xcore:cmd:maps-load:{server}',
    targetScope='server',
    ttlMs=300000,
    replayable=False,
    idempotentConsumerRecommended=True,
    owner='maps',
    response=None,
)

CHAT_MESSAGE_V1 = RouteDescriptor(
    family='chat',
    methodName='chatMessageV1Route',
    messageType='chat.message',
    messageVersion=1,
    payloadType=ChatMessageV1,
    kind='event',
    stream='xcore:evt:chat:message',
    targetScope='broadcast',
    ttlMs=60000,
    replayable=True,
    idempotentConsumerRecommended=False,
    owner='chat',
    response=None,
)

CHAT_GLOBAL_V1 = RouteDescriptor(
    family='chat',
    methodName='chatGlobalV1Route',
    messageType='chat.global',
    messageVersion=1,
    payloadType=ChatGlobalV1,
    kind='event',
    stream='xcore:evt:chat:global',
    targetScope='broadcast',
    ttlMs=60000,
    replayable=True,
    idempotentConsumerRecommended=False,
    owner='chat',
    response=None,
)

CHAT_DISCORD_INGRESS_COMMAND_V1 = RouteDescriptor(
    family='chat',
    methodName='chatDiscordIngressCommandV1Route',
    messageType='chat.discord-ingress.command',
    messageVersion=1,
    payloadType=ChatDiscordIngressCommandV1,
    kind='command',
    stream='xcore:cmd:discord-message:{server}',
    targetScope='server',
    ttlMs=60000,
    replayable=False,
    idempotentConsumerRecommended=True,
    owner='chat',
    response=None,
)

SERVER_ACTION_V1 = RouteDescriptor(
    family='chat',
    methodName='serverActionV1Route',
    messageType='server.action',
    messageVersion=1,
    payloadType=ServerActionV1,
    kind='event',
    stream='xcore:evt:server:action',
    targetScope='broadcast',
    ttlMs=60000,
    replayable=True,
    idempotentConsumerRecommended=False,
    owner='server-runtime',
    response=None,
)

PLAYER_JOIN_LEAVE_V1 = RouteDescriptor(
    family='chat',
    methodName='playerJoinLeaveV1Route',
    messageType='player.join-leave',
    messageVersion=1,
    payloadType=PlayerJoinLeaveV1,
    kind='event',
    stream='xcore:evt:player:joinleave',
    targetScope='broadcast',
    ttlMs=60000,
    replayable=True,
    idempotentConsumerRecommended=False,
    owner='player-session',
    response=None,
)

SERVER_HEARTBEAT_V1 = RouteDescriptor(
    family='chat',
    methodName='serverHeartbeatV1Route',
    messageType='server.heartbeat',
    messageVersion=1,
    payloadType=ServerHeartbeatV1,
    kind='event',
    stream='xcore:evt:server:heartbeat',
    targetScope='broadcast',
    ttlMs=60000,
    replayable=True,
    idempotentConsumerRecommended=False,
    owner='server-runtime',
    response=None,
)

DISCORD_LINK_CONFIRM_COMMAND_V1 = RouteDescriptor(
    family='discord',
    methodName='discordLinkConfirmCommandV1Route',
    messageType='discord.link.confirm.command',
    messageVersion=1,
    payloadType=DiscordLinkConfirmCommandV1,
    kind='command',
    stream='xcore:cmd:discord-link-confirm:{server}',
    targetScope='server',
    ttlMs=120000,
    replayable=False,
    idempotentConsumerRecommended=True,
    owner='discord-linking',
    response=None,
)

DISCORD_UNLINK_COMMAND_V1 = RouteDescriptor(
    family='discord',
    methodName='discordUnlinkCommandV1Route',
    messageType='discord.unlink.command',
    messageVersion=1,
    payloadType=DiscordUnlinkCommandV1,
    kind='command',
    stream='xcore:cmd:discord-unlink:{server}',
    targetScope='server',
    ttlMs=120000,
    replayable=False,
    idempotentConsumerRecommended=True,
    owner='discord-linking',
    response=None,
)

DISCORD_LINK_STATUS_CHANGED_V1 = RouteDescriptor(
    family='discord',
    methodName='discordLinkStatusChangedV1Route',
    messageType='discord.link.status-changed',
    messageVersion=1,
    payloadType=DiscordLinkStatusChangedV1,
    kind='event',
    stream='xcore:evt:discord:link-status',
    targetScope='broadcast',
    ttlMs=120000,
    replayable=True,
    idempotentConsumerRecommended=True,
    owner='discord-linking',
    response=None,
)

DISCORD_ADMIN_ACCESS_CHANGED_COMMAND_V1 = RouteDescriptor(
    family='discord',
    methodName='discordAdminAccessChangedCommandV1Route',
    messageType='discord.admin-access.changed.command',
    messageVersion=1,
    payloadType=DiscordAdminAccessChangedCommandV1,
    kind='command',
    stream='xcore:cmd:discord-admin-access:{server}',
    targetScope='server',
    ttlMs=120000,
    replayable=False,
    idempotentConsumerRecommended=True,
    owner='discord-admin-access',
    response=None,
)

MODERATION_BAN_CREATED_V1 = RouteDescriptor(
    family='moderation',
    methodName='moderationBanCreatedV1Route',
    messageType='moderation.ban.created',
    messageVersion=1,
    payloadType=ModerationBanCreatedV1,
    kind='event',
    stream='xcore:evt:moderation:ban',
    targetScope='broadcast',
    ttlMs=120000,
    replayable=True,
    idempotentConsumerRecommended=True,
    owner='moderation',
    response=None,
)

MODERATION_MUTE_CREATED_V1 = RouteDescriptor(
    family='moderation',
    methodName='moderationMuteCreatedV1Route',
    messageType='moderation.mute.created',
    messageVersion=1,
    payloadType=ModerationMuteCreatedV1,
    kind='event',
    stream='xcore:evt:moderation:mute',
    targetScope='broadcast',
    ttlMs=120000,
    replayable=True,
    idempotentConsumerRecommended=True,
    owner='moderation',
    response=None,
)

MODERATION_VOTE_KICK_CREATED_V1 = RouteDescriptor(
    family='moderation',
    methodName='moderationVoteKickCreatedV1Route',
    messageType='moderation.vote-kick.created',
    messageVersion=1,
    payloadType=ModerationVoteKickCreatedV1,
    kind='event',
    stream='xcore:evt:moderation:votekick',
    targetScope='broadcast',
    ttlMs=120000,
    replayable=True,
    idempotentConsumerRecommended=True,
    owner='moderation',
    response=None,
)

MODERATION_KICK_BANNED_COMMAND_V1 = RouteDescriptor(
    family='moderation',
    methodName='moderationKickBannedCommandV1Route',
    messageType='moderation.kick-banned.command',
    messageVersion=1,
    payloadType=ModerationKickBannedCommandV1,
    kind='command',
    stream='xcore:cmd:kick-banned:{server}',
    targetScope='server',
    ttlMs=120000,
    replayable=False,
    idempotentConsumerRecommended=True,
    owner='moderation',
    response=None,
)

MODERATION_PARDON_COMMAND_V1 = RouteDescriptor(
    family='moderation',
    methodName='moderationPardonCommandV1Route',
    messageType='moderation.pardon.command',
    messageVersion=1,
    payloadType=ModerationPardonCommandV1,
    kind='command',
    stream='xcore:cmd:pardon-player:{server}',
    targetScope='server',
    ttlMs=120000,
    replayable=False,
    idempotentConsumerRecommended=True,
    owner='moderation',
    response=None,
)

MODERATION_AUDIT_APPENDED_V1 = RouteDescriptor(
    family='moderation',
    methodName='moderationAuditAppendedV1Route',
    messageType='moderation.audit.appended',
    messageVersion=1,
    payloadType=ModerationAuditAppendedV1,
    kind='event',
    stream='xcore:evt:moderation:audit',
    targetScope='broadcast',
    ttlMs=120000,
    replayable=True,
    idempotentConsumerRecommended=True,
    owner='moderation',
    response=None,
)

ROUTES_BY_MESSAGE: dict[tuple[str, int], RouteDescriptor] = {
    ('maps.list.request', 1): MAPS_LIST_REQUEST_V1,
    ('maps.remove.request', 1): MAPS_REMOVE_REQUEST_V1,
    ('maps.load.command', 1): MAPS_LOAD_COMMAND_V1,
    ('chat.message', 1): CHAT_MESSAGE_V1,
    ('chat.global', 1): CHAT_GLOBAL_V1,
    ('chat.discord-ingress.command', 1): CHAT_DISCORD_INGRESS_COMMAND_V1,
    ('server.action', 1): SERVER_ACTION_V1,
    ('player.join-leave', 1): PLAYER_JOIN_LEAVE_V1,
    ('server.heartbeat', 1): SERVER_HEARTBEAT_V1,
    ('discord.link.confirm.command', 1): DISCORD_LINK_CONFIRM_COMMAND_V1,
    ('discord.unlink.command', 1): DISCORD_UNLINK_COMMAND_V1,
    ('discord.link.status-changed', 1): DISCORD_LINK_STATUS_CHANGED_V1,
    ('discord.admin-access.changed.command', 1): DISCORD_ADMIN_ACCESS_CHANGED_COMMAND_V1,
    ('moderation.ban.created', 1): MODERATION_BAN_CREATED_V1,
    ('moderation.mute.created', 1): MODERATION_MUTE_CREATED_V1,
    ('moderation.vote-kick.created', 1): MODERATION_VOTE_KICK_CREATED_V1,
    ('moderation.kick-banned.command', 1): MODERATION_KICK_BANNED_COMMAND_V1,
    ('moderation.pardon.command', 1): MODERATION_PARDON_COMMAND_V1,
    ('moderation.audit.appended', 1): MODERATION_AUDIT_APPENDED_V1,
}

MapsRouteResponseDescriptor = RouteResponseDescriptor
MapsRouteDescriptor = RouteDescriptor
MAPS_ROUTES_BY_MESSAGE = ROUTES_BY_MESSAGE

__all__ = [
    "MAPS_LIST_REQUEST_V1",
    "MAPS_REMOVE_REQUEST_V1",
    "MAPS_LOAD_COMMAND_V1",
    "CHAT_MESSAGE_V1",
    "CHAT_GLOBAL_V1",
    "CHAT_DISCORD_INGRESS_COMMAND_V1",
    "SERVER_ACTION_V1",
    "PLAYER_JOIN_LEAVE_V1",
    "SERVER_HEARTBEAT_V1",
    "DISCORD_LINK_CONFIRM_COMMAND_V1",
    "DISCORD_UNLINK_COMMAND_V1",
    "DISCORD_LINK_STATUS_CHANGED_V1",
    "DISCORD_ADMIN_ACCESS_CHANGED_COMMAND_V1",
    "MODERATION_BAN_CREATED_V1",
    "MODERATION_MUTE_CREATED_V1",
    "MODERATION_VOTE_KICK_CREATED_V1",
    "MODERATION_KICK_BANNED_COMMAND_V1",
    "MODERATION_PARDON_COMMAND_V1",
    "MODERATION_AUDIT_APPENDED_V1",
    "RouteDescriptor",
    "RouteResponseDescriptor",
    "ROUTES_BY_MESSAGE",
    "MapsRouteDescriptor",
    "MapsRouteResponseDescriptor",
    "MAPS_ROUTES_BY_MESSAGE",
]
