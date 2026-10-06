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
    ChatPrivateV1,
)

from .identity import (
    PlayerJoinLeaveV1,
    PlayerCustomNicknameChangedCommandV1,
    PlayerActiveBadgeChangedCommandV1,
    PlayerBadgeInventoryChangedCommandV1,
    PlayerBadgeSymbolColorModeChangedCommandV1,
)

from .server import (
    ServerActionV1,
    ServerCommandExecuteCommandV1,
    PlayerDataCacheReloadCommandV1,
    ServerHeartbeatV1,
)

from .security import (
    PlayerPasswordResetCommandV1,
    SecurityPermissionsChangedV1,
    SecurityStaffSyncRequestV1,
    SecurityStaffSyncResponseV1,
    SecurityStaffResetPasswordRequestV1,
    SecurityStaffResetPasswordResponseV1,
)

from .discord import (
    DiscordLinkCodeCreatedV1,
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

from .sentinel import (
    SentinelSubnetRulesInvalidatedV1,
    SentinelSubnetSweepCommandV1,
    SentinelSubnetRulesCommandV1,
    SentinelSubnetRulesResponseV1,
    SentinelSubnetRulesListRequestV1,
    SentinelSubnetRulesListResponseV1,
    SentinelSubnetRulesCheckRequestV1,
    SentinelSubnetRulesCheckResponseV1,
)

from .rating import (
    RatingSeasonStartedV1,
    RatingSeasonEndingSoonV1,
    RatingSeasonEndedV1,
    RatingSeasonRescheduledV1,
    RatingSeasonRescheduleRequestV1,
    RatingSeasonRescheduleResponseV1,
    RatingAccountsMergeRequestV1,
    RatingAccountsMergeResponseV1,
    RatingSeasonPrizesSetRequestV1,
    RatingSeasonPrizesSetResponseV1,
    RatingPrizeGrantUpdateRequestV1,
    RatingPrizeGrantUpdateResponseV1,
)

@dataclass(frozen=True, slots=True)
class RouteResponseDescriptor:
    messageType: str
    messageVersion: int
    payloadType: type[Any]
    stream: str
    bindings: dict[str, str]


@dataclass(frozen=True, slots=True)
class RouteDescriptor:
    family: str
    methodName: str
    messageType: str
    messageVersion: int
    payloadType: type[Any]
    kind: str
    stream: str
    bindings: dict[str, str]
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
    bindings={'server': 'payload.server'},
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
        bindings={'requester': 'rpc.requester'},
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
    bindings={'server': 'payload.server'},
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
        bindings={'requester': 'rpc.requester'},
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
    bindings={'server': 'payload.server'},
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
    bindings={},
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
    bindings={},
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
    bindings={'server': 'payload.server'},
    targetScope='server',
    ttlMs=60000,
    replayable=False,
    idempotentConsumerRecommended=True,
    owner='chat',
    response=None,
)

CHAT_PRIVATE_V1 = RouteDescriptor(
    family='chat',
    methodName='chatPrivateV1Route',
    messageType='chat.private',
    messageVersion=1,
    payloadType=ChatPrivateV1,
    kind='event',
    stream='xcore:evt:chat:private',
    bindings={},
    targetScope='broadcast',
    ttlMs=60000,
    replayable=True,
    idempotentConsumerRecommended=False,
    owner='chat',
    response=None,
)

PLAYER_JOIN_LEAVE_V1 = RouteDescriptor(
    family='identity',
    methodName='playerJoinLeaveV1Route',
    messageType='player.join-leave',
    messageVersion=1,
    payloadType=PlayerJoinLeaveV1,
    kind='event',
    stream='xcore:evt:player:joinleave',
    bindings={},
    targetScope='broadcast',
    ttlMs=60000,
    replayable=True,
    idempotentConsumerRecommended=False,
    owner='player-session',
    response=None,
)

PLAYER_CUSTOM_NICKNAME_CHANGED_COMMAND_V1 = RouteDescriptor(
    family='identity',
    methodName='playerCustomNicknameChangedCommandV1Route',
    messageType='player.custom-nickname.changed.command',
    messageVersion=1,
    payloadType=PlayerCustomNicknameChangedCommandV1,
    kind='command',
    stream='xcore:cmd:player-custom-nickname:{server}',
    bindings={'server': 'payload.server'},
    targetScope='server',
    ttlMs=120000,
    replayable=False,
    idempotentConsumerRecommended=True,
    owner='player-session',
    response=None,
)

PLAYER_ACTIVE_BADGE_CHANGED_COMMAND_V1 = RouteDescriptor(
    family='identity',
    methodName='playerActiveBadgeChangedCommandV1Route',
    messageType='player.active-badge.changed.command',
    messageVersion=1,
    payloadType=PlayerActiveBadgeChangedCommandV1,
    kind='command',
    stream='xcore:cmd:player-active-badge:{server}',
    bindings={'server': 'payload.server'},
    targetScope='server',
    ttlMs=120000,
    replayable=False,
    idempotentConsumerRecommended=True,
    owner='player-session',
    response=None,
)

PLAYER_BADGE_INVENTORY_CHANGED_COMMAND_V1 = RouteDescriptor(
    family='identity',
    methodName='playerBadgeInventoryChangedCommandV1Route',
    messageType='player.badge-inventory.changed.command',
    messageVersion=1,
    payloadType=PlayerBadgeInventoryChangedCommandV1,
    kind='command',
    stream='xcore:cmd:player-badge-inventory:{server}',
    bindings={'server': 'payload.server'},
    targetScope='server',
    ttlMs=120000,
    replayable=False,
    idempotentConsumerRecommended=True,
    owner='player-session',
    response=None,
)

PLAYER_BADGE_SYMBOL_COLOR_MODE_CHANGED_COMMAND_V1 = RouteDescriptor(
    family='identity',
    methodName='playerBadgeSymbolColorModeChangedCommandV1Route',
    messageType='player.badge-symbol-color-mode.changed.command',
    messageVersion=1,
    payloadType=PlayerBadgeSymbolColorModeChangedCommandV1,
    kind='command',
    stream='xcore:cmd:player-badge-symbol-color-mode:{server}',
    bindings={'server': 'payload.server'},
    targetScope='server',
    ttlMs=120000,
    replayable=False,
    idempotentConsumerRecommended=True,
    owner='player-session',
    response=None,
)

SERVER_ACTION_V1 = RouteDescriptor(
    family='server',
    methodName='serverActionV1Route',
    messageType='server.action',
    messageVersion=1,
    payloadType=ServerActionV1,
    kind='event',
    stream='xcore:evt:server:action',
    bindings={},
    targetScope='broadcast',
    ttlMs=60000,
    replayable=True,
    idempotentConsumerRecommended=False,
    owner='server-runtime',
    response=None,
)

SERVER_COMMAND_EXECUTE_COMMAND_V1 = RouteDescriptor(
    family='server',
    methodName='serverCommandExecuteCommandV1Route',
    messageType='server-command.execute.command',
    messageVersion=1,
    payloadType=ServerCommandExecuteCommandV1,
    kind='command',
    stream='xcore:cmd:execute-command:broadcast',
    bindings={},
    targetScope='broadcast',
    ttlMs=120000,
    replayable=False,
    idempotentConsumerRecommended=False,
    owner='server-runtime',
    response=None,
)

PLAYER_DATA_CACHE_RELOAD_COMMAND_V1 = RouteDescriptor(
    family='server',
    methodName='playerDataCacheReloadCommandV1Route',
    messageType='player-data-cache.reload.command',
    messageVersion=1,
    payloadType=PlayerDataCacheReloadCommandV1,
    kind='command',
    stream='xcore:cmd:reload-cache:{server}',
    bindings={'server': 'payload.server'},
    targetScope='server',
    ttlMs=120000,
    replayable=False,
    idempotentConsumerRecommended=True,
    owner='player-session',
    response=None,
)

SERVER_HEARTBEAT_V1 = RouteDescriptor(
    family='server',
    methodName='serverHeartbeatV1Route',
    messageType='server.heartbeat',
    messageVersion=1,
    payloadType=ServerHeartbeatV1,
    kind='event',
    stream='xcore:evt:server:heartbeat',
    bindings={},
    targetScope='broadcast',
    ttlMs=60000,
    replayable=True,
    idempotentConsumerRecommended=False,
    owner='server-runtime',
    response=None,
)

PLAYER_PASSWORD_RESET_COMMAND_V1 = RouteDescriptor(
    family='security',
    methodName='playerPasswordResetCommandV1Route',
    messageType='player.password-reset.command',
    messageVersion=1,
    payloadType=PlayerPasswordResetCommandV1,
    kind='command',
    stream='xcore:cmd:player-password-reset:{server}',
    bindings={'server': 'payload.server'},
    targetScope='server',
    ttlMs=120000,
    replayable=False,
    idempotentConsumerRecommended=True,
    owner='player-session',
    response=None,
)

SECURITY_PERMISSIONS_CHANGED_V1 = RouteDescriptor(
    family='security',
    methodName='securityPermissionsChangedV1Route',
    messageType='security.permissions.changed',
    messageVersion=1,
    payloadType=SecurityPermissionsChangedV1,
    kind='event',
    stream='xcore:evt:security:permissions-changed',
    bindings={},
    targetScope='broadcast',
    ttlMs=300000,
    replayable=True,
    idempotentConsumerRecommended=True,
    owner='permissions',
    response=None,
)

SECURITY_STAFF_SYNC_REQUEST_V1 = RouteDescriptor(
    family='security',
    methodName='securityStaffSyncRequestV1Route',
    messageType='security.staff.sync.request',
    messageVersion=1,
    payloadType=SecurityStaffSyncRequestV1,
    kind='rpc-request',
    stream='xcore:rpc:req:{server}',
    bindings={'server': 'payload.server'},
    targetScope='server',
    ttlMs=10000,
    replayable=False,
    idempotentConsumerRecommended=True,
    owner='permissions',
    response=RouteResponseDescriptor(
        messageType='security.staff.sync.response',
        messageVersion=1,
        payloadType=SecurityStaffSyncResponseV1,
        stream='xcore:rpc:resp:{requester}',
        bindings={'requester': 'rpc.requester'},
    ),
)

SECURITY_STAFF_RESET_PASSWORD_REQUEST_V1 = RouteDescriptor(
    family='security',
    methodName='securityStaffResetPasswordRequestV1Route',
    messageType='security.staff.reset-password.request',
    messageVersion=1,
    payloadType=SecurityStaffResetPasswordRequestV1,
    kind='rpc-request',
    stream='xcore:rpc:req:{server}',
    bindings={'server': 'payload.server'},
    targetScope='server',
    ttlMs=10000,
    replayable=False,
    idempotentConsumerRecommended=True,
    owner='permissions',
    response=RouteResponseDescriptor(
        messageType='security.staff.reset-password.response',
        messageVersion=1,
        payloadType=SecurityStaffResetPasswordResponseV1,
        stream='xcore:rpc:resp:{requester}',
        bindings={'requester': 'rpc.requester'},
    ),
)

DISCORD_LINK_CODE_CREATED_V1 = RouteDescriptor(
    family='discord',
    methodName='discordLinkCodeCreatedV1Route',
    messageType='discord.link-code-created',
    messageVersion=1,
    payloadType=DiscordLinkCodeCreatedV1,
    kind='event',
    stream='xcore:evt:discord:link-code',
    bindings={},
    targetScope='broadcast',
    ttlMs=120000,
    replayable=True,
    idempotentConsumerRecommended=True,
    owner='discord-linking',
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
    bindings={'server': 'payload.server'},
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
    bindings={'server': 'payload.server'},
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
    bindings={},
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
    bindings={'server': 'payload.server'},
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
    bindings={},
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
    bindings={},
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
    bindings={},
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
    bindings={'server': 'payload.server'},
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
    bindings={'server': 'payload.server'},
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
    bindings={},
    targetScope='broadcast',
    ttlMs=120000,
    replayable=True,
    idempotentConsumerRecommended=True,
    owner='moderation',
    response=None,
)

SENTINEL_SUBNET_RULES_INVALIDATED_V1 = RouteDescriptor(
    family='sentinel',
    methodName='sentinelSubnetRulesInvalidatedV1Route',
    messageType='sentinel.subnet-rules.invalidated',
    messageVersion=1,
    payloadType=SentinelSubnetRulesInvalidatedV1,
    kind='event',
    stream='xcore:evt:sentinel:subnet:invalidated',
    bindings={},
    targetScope='broadcast',
    ttlMs=60000,
    replayable=True,
    idempotentConsumerRecommended=True,
    owner='sentinel',
    response=None,
)

SENTINEL_SUBNET_SWEEP_COMMAND_V1 = RouteDescriptor(
    family='sentinel',
    methodName='sentinelSubnetSweepCommandV1Route',
    messageType='sentinel.subnet-sweep.command',
    messageVersion=1,
    payloadType=SentinelSubnetSweepCommandV1,
    kind='command',
    stream='xcore:cmd:sentinel:subnet:sweep:broadcast',
    bindings={},
    targetScope='broadcast',
    ttlMs=60000,
    replayable=False,
    idempotentConsumerRecommended=True,
    owner='sentinel',
    response=None,
)

SENTINEL_SUBNET_RULES_COMMAND_V1 = RouteDescriptor(
    family='sentinel',
    methodName='sentinelSubnetRulesCommandV1Route',
    messageType='sentinel.subnet-rules.command',
    messageVersion=1,
    payloadType=SentinelSubnetRulesCommandV1,
    kind='rpc-request',
    stream='xcore:rpc:req:{targetServer}',
    bindings={'targetServer': 'payload.targetServer'},
    targetScope='server',
    ttlMs=10000,
    replayable=False,
    idempotentConsumerRecommended=True,
    owner='sentinel',
    response=RouteResponseDescriptor(
        messageType='sentinel.subnet-rules.response',
        messageVersion=1,
        payloadType=SentinelSubnetRulesResponseV1,
        stream='xcore:rpc:resp:{requester}',
        bindings={'requester': 'rpc.requester'},
    ),
)

SENTINEL_SUBNET_RULES_LIST_REQUEST_V1 = RouteDescriptor(
    family='sentinel',
    methodName='sentinelSubnetRulesListRequestV1Route',
    messageType='sentinel.subnet-rules.list.request',
    messageVersion=1,
    payloadType=SentinelSubnetRulesListRequestV1,
    kind='rpc-request',
    stream='xcore:rpc:req:{targetServer}',
    bindings={'targetServer': 'payload.targetServer'},
    targetScope='server',
    ttlMs=10000,
    replayable=False,
    idempotentConsumerRecommended=False,
    owner='sentinel',
    response=RouteResponseDescriptor(
        messageType='sentinel.subnet-rules.list.response',
        messageVersion=1,
        payloadType=SentinelSubnetRulesListResponseV1,
        stream='xcore:rpc:resp:{requester}',
        bindings={'requester': 'rpc.requester'},
    ),
)

SENTINEL_SUBNET_RULES_CHECK_REQUEST_V1 = RouteDescriptor(
    family='sentinel',
    methodName='sentinelSubnetRulesCheckRequestV1Route',
    messageType='sentinel.subnet-rules.check.request',
    messageVersion=1,
    payloadType=SentinelSubnetRulesCheckRequestV1,
    kind='rpc-request',
    stream='xcore:rpc:req:{targetServer}',
    bindings={'targetServer': 'payload.targetServer'},
    targetScope='server',
    ttlMs=10000,
    replayable=False,
    idempotentConsumerRecommended=False,
    owner='sentinel',
    response=RouteResponseDescriptor(
        messageType='sentinel.subnet-rules.check.response',
        messageVersion=1,
        payloadType=SentinelSubnetRulesCheckResponseV1,
        stream='xcore:rpc:resp:{requester}',
        bindings={'requester': 'rpc.requester'},
    ),
)

RATING_SEASON_STARTED_V1 = RouteDescriptor(
    family='rating',
    methodName='ratingSeasonStartedV1Route',
    messageType='rating.season.started',
    messageVersion=1,
    payloadType=RatingSeasonStartedV1,
    kind='event',
    stream='xcore:evt:rating:season-started',
    bindings={},
    targetScope='broadcast',
    ttlMs=86400000,
    replayable=True,
    idempotentConsumerRecommended=True,
    owner='rating-seasons',
    response=None,
)

RATING_SEASON_ENDING_SOON_V1 = RouteDescriptor(
    family='rating',
    methodName='ratingSeasonEndingSoonV1Route',
    messageType='rating.season.ending-soon',
    messageVersion=1,
    payloadType=RatingSeasonEndingSoonV1,
    kind='event',
    stream='xcore:evt:rating:season-ending-soon',
    bindings={},
    targetScope='broadcast',
    ttlMs=86400000,
    replayable=True,
    idempotentConsumerRecommended=True,
    owner='rating-seasons',
    response=None,
)

RATING_SEASON_ENDED_V1 = RouteDescriptor(
    family='rating',
    methodName='ratingSeasonEndedV1Route',
    messageType='rating.season.ended',
    messageVersion=1,
    payloadType=RatingSeasonEndedV1,
    kind='event',
    stream='xcore:evt:rating:season-ended',
    bindings={},
    targetScope='broadcast',
    ttlMs=86400000,
    replayable=True,
    idempotentConsumerRecommended=True,
    owner='rating-seasons',
    response=None,
)

RATING_SEASON_RESCHEDULED_V1 = RouteDescriptor(
    family='rating',
    methodName='ratingSeasonRescheduledV1Route',
    messageType='rating.season.rescheduled',
    messageVersion=1,
    payloadType=RatingSeasonRescheduledV1,
    kind='event',
    stream='xcore:evt:rating:season-rescheduled',
    bindings={},
    targetScope='broadcast',
    ttlMs=86400000,
    replayable=True,
    idempotentConsumerRecommended=True,
    owner='rating-seasons',
    response=None,
)

RATING_SEASON_RESCHEDULE_REQUEST_V1 = RouteDescriptor(
    family='rating',
    methodName='ratingSeasonRescheduleRequestV1Route',
    messageType='rating.season.reschedule.request',
    messageVersion=1,
    payloadType=RatingSeasonRescheduleRequestV1,
    kind='rpc-request',
    stream='xcore:rpc:req:{server}',
    bindings={'server': 'payload.server'},
    targetScope='server',
    ttlMs=10000,
    replayable=False,
    idempotentConsumerRecommended=True,
    owner='rating-seasons',
    response=RouteResponseDescriptor(
        messageType='rating.season.reschedule.response',
        messageVersion=1,
        payloadType=RatingSeasonRescheduleResponseV1,
        stream='xcore:rpc:resp:{requester}',
        bindings={'requester': 'rpc.requester'},
    ),
)

RATING_ACCOUNTS_MERGE_REQUEST_V1 = RouteDescriptor(
    family='rating',
    methodName='ratingAccountsMergeRequestV1Route',
    messageType='rating.accounts.merge.request',
    messageVersion=1,
    payloadType=RatingAccountsMergeRequestV1,
    kind='rpc-request',
    stream='xcore:rpc:req:{server}',
    bindings={'server': 'payload.server'},
    targetScope='server',
    ttlMs=10000,
    replayable=False,
    idempotentConsumerRecommended=True,
    owner='rating-accounts',
    response=RouteResponseDescriptor(
        messageType='rating.accounts.merge.response',
        messageVersion=1,
        payloadType=RatingAccountsMergeResponseV1,
        stream='xcore:rpc:resp:{requester}',
        bindings={'requester': 'rpc.requester'},
    ),
)

RATING_SEASON_PRIZES_SET_REQUEST_V1 = RouteDescriptor(
    family='rating',
    methodName='ratingSeasonPrizesSetRequestV1Route',
    messageType='rating.season.prizes.set.request',
    messageVersion=1,
    payloadType=RatingSeasonPrizesSetRequestV1,
    kind='rpc-request',
    stream='xcore:rpc:req:{server}',
    bindings={'server': 'payload.server'},
    targetScope='server',
    ttlMs=10000,
    replayable=False,
    idempotentConsumerRecommended=True,
    owner='rating-prizes',
    response=RouteResponseDescriptor(
        messageType='rating.season.prizes.set.response',
        messageVersion=1,
        payloadType=RatingSeasonPrizesSetResponseV1,
        stream='xcore:rpc:resp:{requester}',
        bindings={'requester': 'rpc.requester'},
    ),
)

RATING_PRIZE_GRANT_UPDATE_REQUEST_V1 = RouteDescriptor(
    family='rating',
    methodName='ratingPrizeGrantUpdateRequestV1Route',
    messageType='rating.prize.grant.update.request',
    messageVersion=1,
    payloadType=RatingPrizeGrantUpdateRequestV1,
    kind='rpc-request',
    stream='xcore:rpc:req:{server}',
    bindings={'server': 'payload.server'},
    targetScope='server',
    ttlMs=10000,
    replayable=False,
    idempotentConsumerRecommended=True,
    owner='rating-prizes',
    response=RouteResponseDescriptor(
        messageType='rating.prize.grant.update.response',
        messageVersion=1,
        payloadType=RatingPrizeGrantUpdateResponseV1,
        stream='xcore:rpc:resp:{requester}',
        bindings={'requester': 'rpc.requester'},
    ),
)

ROUTES_BY_MESSAGE: dict[tuple[str, int], RouteDescriptor] = {
    ('maps.list.request', 1): MAPS_LIST_REQUEST_V1,
    ('maps.remove.request', 1): MAPS_REMOVE_REQUEST_V1,
    ('maps.load.command', 1): MAPS_LOAD_COMMAND_V1,
    ('chat.message', 1): CHAT_MESSAGE_V1,
    ('chat.global', 1): CHAT_GLOBAL_V1,
    ('chat.discord-ingress.command', 1): CHAT_DISCORD_INGRESS_COMMAND_V1,
    ('chat.private', 1): CHAT_PRIVATE_V1,
    ('player.join-leave', 1): PLAYER_JOIN_LEAVE_V1,
    ('player.custom-nickname.changed.command', 1): PLAYER_CUSTOM_NICKNAME_CHANGED_COMMAND_V1,
    ('player.active-badge.changed.command', 1): PLAYER_ACTIVE_BADGE_CHANGED_COMMAND_V1,
    ('player.badge-inventory.changed.command', 1): PLAYER_BADGE_INVENTORY_CHANGED_COMMAND_V1,
    ('player.badge-symbol-color-mode.changed.command', 1): PLAYER_BADGE_SYMBOL_COLOR_MODE_CHANGED_COMMAND_V1,
    ('server.action', 1): SERVER_ACTION_V1,
    ('server-command.execute.command', 1): SERVER_COMMAND_EXECUTE_COMMAND_V1,
    ('player-data-cache.reload.command', 1): PLAYER_DATA_CACHE_RELOAD_COMMAND_V1,
    ('server.heartbeat', 1): SERVER_HEARTBEAT_V1,
    ('player.password-reset.command', 1): PLAYER_PASSWORD_RESET_COMMAND_V1,
    ('security.permissions.changed', 1): SECURITY_PERMISSIONS_CHANGED_V1,
    ('security.staff.sync.request', 1): SECURITY_STAFF_SYNC_REQUEST_V1,
    ('security.staff.reset-password.request', 1): SECURITY_STAFF_RESET_PASSWORD_REQUEST_V1,
    ('discord.link-code-created', 1): DISCORD_LINK_CODE_CREATED_V1,
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
    ('sentinel.subnet-rules.invalidated', 1): SENTINEL_SUBNET_RULES_INVALIDATED_V1,
    ('sentinel.subnet-sweep.command', 1): SENTINEL_SUBNET_SWEEP_COMMAND_V1,
    ('sentinel.subnet-rules.command', 1): SENTINEL_SUBNET_RULES_COMMAND_V1,
    ('sentinel.subnet-rules.list.request', 1): SENTINEL_SUBNET_RULES_LIST_REQUEST_V1,
    ('sentinel.subnet-rules.check.request', 1): SENTINEL_SUBNET_RULES_CHECK_REQUEST_V1,
    ('rating.season.started', 1): RATING_SEASON_STARTED_V1,
    ('rating.season.ending-soon', 1): RATING_SEASON_ENDING_SOON_V1,
    ('rating.season.ended', 1): RATING_SEASON_ENDED_V1,
    ('rating.season.rescheduled', 1): RATING_SEASON_RESCHEDULED_V1,
    ('rating.season.reschedule.request', 1): RATING_SEASON_RESCHEDULE_REQUEST_V1,
    ('rating.accounts.merge.request', 1): RATING_ACCOUNTS_MERGE_REQUEST_V1,
    ('rating.season.prizes.set.request', 1): RATING_SEASON_PRIZES_SET_REQUEST_V1,
    ('rating.prize.grant.update.request', 1): RATING_PRIZE_GRANT_UPDATE_REQUEST_V1,
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
    "CHAT_PRIVATE_V1",
    "PLAYER_JOIN_LEAVE_V1",
    "PLAYER_CUSTOM_NICKNAME_CHANGED_COMMAND_V1",
    "PLAYER_ACTIVE_BADGE_CHANGED_COMMAND_V1",
    "PLAYER_BADGE_INVENTORY_CHANGED_COMMAND_V1",
    "PLAYER_BADGE_SYMBOL_COLOR_MODE_CHANGED_COMMAND_V1",
    "SERVER_ACTION_V1",
    "SERVER_COMMAND_EXECUTE_COMMAND_V1",
    "PLAYER_DATA_CACHE_RELOAD_COMMAND_V1",
    "SERVER_HEARTBEAT_V1",
    "PLAYER_PASSWORD_RESET_COMMAND_V1",
    "SECURITY_PERMISSIONS_CHANGED_V1",
    "SECURITY_STAFF_SYNC_REQUEST_V1",
    "SECURITY_STAFF_RESET_PASSWORD_REQUEST_V1",
    "DISCORD_LINK_CODE_CREATED_V1",
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
    "SENTINEL_SUBNET_RULES_INVALIDATED_V1",
    "SENTINEL_SUBNET_SWEEP_COMMAND_V1",
    "SENTINEL_SUBNET_RULES_COMMAND_V1",
    "SENTINEL_SUBNET_RULES_LIST_REQUEST_V1",
    "SENTINEL_SUBNET_RULES_CHECK_REQUEST_V1",
    "RATING_SEASON_STARTED_V1",
    "RATING_SEASON_ENDING_SOON_V1",
    "RATING_SEASON_ENDED_V1",
    "RATING_SEASON_RESCHEDULED_V1",
    "RATING_SEASON_RESCHEDULE_REQUEST_V1",
    "RATING_ACCOUNTS_MERGE_REQUEST_V1",
    "RATING_SEASON_PRIZES_SET_REQUEST_V1",
    "RATING_PRIZE_GRANT_UPDATE_REQUEST_V1",
    "RouteDescriptor",
    "RouteResponseDescriptor",
    "ROUTES_BY_MESSAGE",
    "MapsRouteDescriptor",
    "MapsRouteResponseDescriptor",
    "MAPS_ROUTES_BY_MESSAGE",
]
