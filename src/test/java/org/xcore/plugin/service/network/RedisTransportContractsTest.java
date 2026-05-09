package org.xcore.plugin.service.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.protocol.generated.messages.chat.ChatMessages.ChatDiscordIngressCommandV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.ChatGlobalV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.ChatMessageV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.ChatPrivateV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.PlayerActiveBadgeChangedCommandV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.PlayerBadgeInventoryChangedCommandV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.PlayerBadgeSymbolColorModeChangedCommandV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.PlayerCustomNicknameChangedCommandV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.PlayerPasswordResetCommandV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.PlayerJoinLeaveV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.ServerActionV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.ServerHeartbeatV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordAdminAccessChangedCommandV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordLinkCodeCreatedV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordLinkStatusChangedV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsListRequestV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsListResponseV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsLoadCommandV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsRemoveRequestV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsRemoveResponseV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationBanCreatedV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationKickBannedCommandV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationMuteCreatedV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.ServerCommandExecuteCommandV1;
import org.xcore.protocol.generated.routes.ProtocolRoutes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract guards for Redis metadata shared with other XCore services.
 *
 * <p>These assertions intentionally lock down stable external strings and route metadata so transport
 * refactors cannot silently change the Redis compatibility surface.</p>
 */
class RedisTransportContractsTest {

    private final RedisProtocolRouteAdapter routeAdapter = new RedisProtocolRouteAdapter();
    private final RedisStreamRouter router = new RedisStreamRouter(routeAdapter);

    @Test
    @DisplayName("stable stream prefixes and patterns remain compatible")
    void stableStreamPrefixesAndPatternsRemainCompatible() {
        // Arrange
        String eventStream = "xcore:evt:chat:message";
        String commandStream = "xcore:cmd:player-password-reset:mini-pvp";
        String rpcRequestStream = "xcore:rpc:req:mini-pvp";
        String rpcResponseStream = "xcore:rpc:resp:discord-bot";

        // Act
        boolean eventStable = RedisTransportContracts.isStableExternalStream(eventStream);
        boolean commandStable = RedisTransportContracts.isStableExternalStream(commandStream);
        boolean rpcRequestStable = RedisTransportContracts.isStableExternalStream(rpcRequestStream);
        boolean rpcResponseStable = RedisTransportContracts.isStableExternalStream(rpcResponseStream);
        boolean dlqStable = RedisTransportContracts.isStableExternalStream("xcore:dlq:chat:message");
        boolean internalStable = RedisTransportContracts.isStableExternalStream("internal:evt:chat:message");

        // Assert
        assertThat(RedisTransportContracts.STREAM_PREFIX_EVENT).isEqualTo("xcore:evt:");
        assertThat(RedisTransportContracts.STREAM_PREFIX_COMMAND).isEqualTo("xcore:cmd:");
        assertThat(RedisTransportContracts.STREAM_PREFIX_RPC_REQUEST).isEqualTo("xcore:rpc:req:");
        assertThat(RedisTransportContracts.STREAM_PREFIX_RPC_RESPONSE).isEqualTo("xcore:rpc:resp:");
        assertThat(RedisTransportContracts.STABLE_STREAM_PATTERNS)
                .containsExactlyInAnyOrder("xcore:evt:*", "xcore:cmd:*", "xcore:rpc:req:*", "xcore:rpc:resp:*");

        assertThat(eventStable).isTrue();
        assertThat(commandStable).isTrue();
        assertThat(rpcRequestStable).isTrue();
        assertThat(rpcResponseStable).isTrue();
        assertThat(dlqStable).isFalse();
        assertThat(internalStable).isFalse();
    }

    @Test
    @DisplayName("event envelope fields stay stable and exclude rpc-only metadata")
    void eventEnvelopeFieldsStayStableAndExcludeRpcOnlyMetadata() {
        // Arrange
        List<String> eventFields = RedisTransportContracts.EVENT_ENVELOPE_FIELDS;

        // Act
        List<String> stableFields = eventFields;

        // Assert
        assertThat(RedisTransportContracts.ENVELOPE_SCHEMA_VERSION).isEqualTo("1");
        assertThat(stableFields).containsExactly(
                "schema_version",
                "event_type",
                "event_id",
                "idempotency_key",
                "producer",
                "created_at",
                "expires_at",
                "server",
                "payload_json"
        );
        assertThat(stableFields)
                .doesNotContain(
                        RedisTransportContracts.FIELD_RPC_TYPE,
                        RedisTransportContracts.FIELD_REPLY_TO,
                        RedisTransportContracts.FIELD_STATUS
                );
    }

    @Test
    @DisplayName("rpc request and response envelope fields stay stable and direction-specific")
    void rpcRequestAndResponseEnvelopeFieldsStayStableAndDirectionSpecific() {
        // Arrange
        List<String> requestFields = RedisTransportContracts.RPC_REQUEST_ENVELOPE_FIELDS;
        List<String> responseFields = RedisTransportContracts.RPC_RESPONSE_ENVELOPE_FIELDS;

        // Act
        List<String> stableRequestFields = requestFields;
        List<String> stableResponseFields = responseFields;

        // Assert
        assertThat(stableRequestFields).containsExactly(
                "schema_version",
                "rpc_type",
                "correlation_id",
                "request_id",
                "idempotency_key",
                "reply_to",
                "requested_by",
                "server",
                "timeout_ms",
                "created_at",
                "expires_at",
                "payload_json"
        );
        assertThat(stableRequestFields)
                .contains(RedisTransportContracts.FIELD_RPC_TYPE, RedisTransportContracts.FIELD_REPLY_TO)
                .doesNotContain(RedisTransportContracts.FIELD_EVENT_TYPE, RedisTransportContracts.FIELD_STATUS);

        assertThat(stableResponseFields).containsExactly(
                "schema_version",
                "rpc_type",
                "correlation_id",
                "server",
                "status",
                "error_code",
                "error_message",
                "responded_at",
                "payload_json"
        );
        assertThat(stableResponseFields)
                .contains(RedisTransportContracts.FIELD_STATUS, RedisTransportContracts.FIELD_ERROR_MESSAGE)
                .doesNotContain(
                        RedisTransportContracts.FIELD_REQUEST_ID,
                        RedisTransportContracts.FIELD_REPLY_TO,
                        RedisTransportContracts.FIELD_TIMEOUT_MS
                );
    }

    @Test
    @DisplayName("topology locks down representative event command and rpc route metadata")
    void topologyLocksDownRepresentativeEventCommandAndRpcRouteMetadata() {
        // Arrange
        RedisProtocolRouteCatalog.RouteSpec eventRoute = RedisProtocolRouteCatalog.routeFor(ChatGlobalV1.class);
        RedisProtocolRouteCatalog.RouteSpec messageRoute = RedisProtocolRouteCatalog.routeFor(ChatMessageV1.class);
        RedisProtocolRouteCatalog.RouteSpec discordIngressRoute = RedisProtocolRouteCatalog.routeFor(ChatDiscordIngressCommandV1.class);
        RedisProtocolRouteCatalog.RouteSpec privateRoute = RedisProtocolRouteCatalog.routeFor(ChatPrivateV1.class);
        RedisProtocolRouteCatalog.RouteSpec joinLeaveRoute = RedisProtocolRouteCatalog.routeFor(PlayerJoinLeaveV1.class);
        RedisProtocolRouteCatalog.RouteSpec serverActionRoute = RedisProtocolRouteCatalog.routeFor(ServerActionV1.class);
        RedisProtocolRouteCatalog.RouteSpec heartbeatRoute = RedisProtocolRouteCatalog.routeFor(ServerHeartbeatV1.class);
        RedisProtocolRouteCatalog.RouteSpec moderationRoute = RedisProtocolRouteCatalog.routeFor(ModerationBanCreatedV1.class);
        RedisProtocolRouteCatalog.RouteSpec muteRoute = RedisProtocolRouteCatalog.routeFor(ModerationMuteCreatedV1.class);
        RedisProtocolRouteCatalog.RouteSpec discordLinkCodeRoute = RedisProtocolRouteCatalog.routeFor(DiscordLinkCodeCreatedV1.class);
        RedisProtocolRouteCatalog.RouteSpec discordStatusRoute = RedisProtocolRouteCatalog.routeFor(DiscordLinkStatusChangedV1.class);
        RedisProtocolRouteCatalog.RouteSpec customNicknameCommandRoute = RedisProtocolRouteCatalog.routeFor(PlayerCustomNicknameChangedCommandV1.class);
        RedisProtocolRouteCatalog.RouteSpec activeBadgeCommandRoute = RedisProtocolRouteCatalog.routeFor(PlayerActiveBadgeChangedCommandV1.class);
        RedisProtocolRouteCatalog.RouteSpec badgeSymbolColorModeCommandRoute = RedisProtocolRouteCatalog.routeFor(PlayerBadgeSymbolColorModeChangedCommandV1.class);
        RedisProtocolRouteCatalog.RouteSpec commandRoute = RedisProtocolRouteCatalog.routeFor(PlayerPasswordResetCommandV1.class);
        RedisProtocolRouteCatalog.RouteSpec mapsLoadCommandRoute = RedisProtocolRouteCatalog.routeFor(MapsLoadCommandV1.class);
        RedisProtocolRouteCatalog.RouteSpec discordAdminCommandRoute = RedisProtocolRouteCatalog.routeFor(DiscordAdminAccessChangedCommandV1.class);
        RedisProtocolRouteCatalog.RouteSpec broadcastCommandRoute = RedisProtocolRouteCatalog.routeFor(ServerCommandExecuteCommandV1.class);
        RedisProtocolRouteCatalog.RouteSpec rpcRoute = RedisProtocolRouteCatalog.routeFor(MapsListRequestV1.class);
        RedisProtocolRouteCatalog.RouteSpec removeRpcRoute = RedisProtocolRouteCatalog.routeFor(MapsRemoveRequestV1.class);
        RedisProtocolRouteCatalog.RouteSpec kickBannedRoute = RedisProtocolRouteCatalog.routeFor(ModerationKickBannedCommandV1.class);

        // Act
        RedisProtocolRouteCatalog.RouteSpec stableEventRoute = eventRoute;
        RedisProtocolRouteCatalog.RouteSpec stableMessageRoute = messageRoute;
        RedisProtocolRouteCatalog.RouteSpec stableDiscordIngressRoute = discordIngressRoute;
        RedisProtocolRouteCatalog.RouteSpec stablePrivateRoute = privateRoute;
        RedisProtocolRouteCatalog.RouteSpec stableJoinLeaveRoute = joinLeaveRoute;
        RedisProtocolRouteCatalog.RouteSpec stableServerActionRoute = serverActionRoute;
        RedisProtocolRouteCatalog.RouteSpec stableHeartbeatRoute = heartbeatRoute;
        RedisProtocolRouteCatalog.RouteSpec stableModerationRoute = moderationRoute;
        RedisProtocolRouteCatalog.RouteSpec stableMuteRoute = muteRoute;
        RedisProtocolRouteCatalog.RouteSpec stableDiscordLinkCodeRoute = discordLinkCodeRoute;
        RedisProtocolRouteCatalog.RouteSpec stableDiscordStatusRoute = discordStatusRoute;
        RedisProtocolRouteCatalog.RouteSpec stableCustomNicknameCommandRoute = customNicknameCommandRoute;
        RedisProtocolRouteCatalog.RouteSpec stableActiveBadgeCommandRoute = activeBadgeCommandRoute;
        RedisProtocolRouteCatalog.RouteSpec stableBadgeSymbolColorModeCommandRoute = badgeSymbolColorModeCommandRoute;
        RedisProtocolRouteCatalog.RouteSpec stableCommandRoute = commandRoute;
        RedisProtocolRouteCatalog.RouteSpec stableMapsLoadCommandRoute = mapsLoadCommandRoute;
        RedisProtocolRouteCatalog.RouteSpec stableDiscordAdminCommandRoute = discordAdminCommandRoute;
        RedisProtocolRouteCatalog.RouteSpec stableBroadcastCommandRoute = broadcastCommandRoute;
        RedisProtocolRouteCatalog.RouteSpec stableRpcRoute = rpcRoute;
        RedisProtocolRouteCatalog.RouteSpec stableRemoveRpcRoute = removeRpcRoute;
        RedisProtocolRouteCatalog.RouteSpec stableKickBannedRoute = kickBannedRoute;

        // Assert
        assertThat(stableEventRoute).isNotNull();
        assertThat(stableEventRoute.streamPattern()).isEqualTo("xcore:evt:chat:global");
        assertThat(stableEventRoute.messageType()).isEqualTo("chat.global");
        assertThat(stableEventRoute.deliveryMode()).isEqualTo(RedisDeliveryMode.EVENT);
        assertThat(stableEventRoute.serverScope()).isEqualTo(RedisProtocolRouteCatalog.ServerScope.BROADCAST);
        assertThat(stableEventRoute.readOnly()).isTrue();
        assertThat(stableEventRoute.rpcRequest()).isFalse();

        assertThat(stableMessageRoute).isNotNull();
        assertThat(stableMessageRoute.streamPattern()).isEqualTo("xcore:evt:chat:message");
        assertThat(stableMessageRoute.messageType()).isEqualTo("chat.message");
        assertThat(stableMessageRoute.deliveryMode()).isEqualTo(RedisDeliveryMode.EVENT);
        assertThat(stableMessageRoute.serverScope()).isEqualTo(RedisProtocolRouteCatalog.ServerScope.BROADCAST);
        assertThat(stableMessageRoute.readOnly()).isTrue();
        assertThat(stableMessageRoute.rpcRequest()).isFalse();

        assertThat(stableDiscordIngressRoute).isNotNull();
        assertThat(stableDiscordIngressRoute.streamPattern()).isEqualTo("xcore:cmd:discord-message:{server}");
        assertThat(stableDiscordIngressRoute.messageType()).isEqualTo("chat.discord-ingress.command");
        assertThat(stableDiscordIngressRoute.deliveryMode()).isEqualTo(RedisDeliveryMode.COMMAND);
        assertThat(stableDiscordIngressRoute.serverScope()).isEqualTo(RedisProtocolRouteCatalog.ServerScope.PAYLOAD_SERVER);
        assertThat(stableDiscordIngressRoute.readOnly()).isFalse();
        assertThat(stableDiscordIngressRoute.rpcRequest()).isFalse();
        assertThat(stableDiscordIngressRoute.idempotentConsumerRecommended()).isTrue();

        assertThat(stablePrivateRoute).isNotNull();
        assertThat(stablePrivateRoute.streamPattern()).isEqualTo("xcore:evt:chat:private");
        assertThat(stablePrivateRoute.messageType()).isEqualTo("chat.private");
        assertThat(stablePrivateRoute.deliveryMode()).isEqualTo(RedisDeliveryMode.EVENT);
        assertThat(stablePrivateRoute.serverScope()).isEqualTo(RedisProtocolRouteCatalog.ServerScope.BROADCAST);
        assertThat(stablePrivateRoute.readOnly()).isTrue();
        assertThat(stablePrivateRoute.rpcRequest()).isFalse();

        assertThat(stableJoinLeaveRoute).isNotNull();
        assertThat(stableJoinLeaveRoute.streamPattern()).isEqualTo("xcore:evt:player:joinleave");
        assertThat(stableJoinLeaveRoute.messageType()).isEqualTo("player.join-leave");
        assertThat(stableJoinLeaveRoute.deliveryMode()).isEqualTo(RedisDeliveryMode.EVENT);
        assertThat(stableJoinLeaveRoute.serverScope()).isEqualTo(RedisProtocolRouteCatalog.ServerScope.BROADCAST);
        assertThat(stableJoinLeaveRoute.readOnly()).isTrue();
        assertThat(stableJoinLeaveRoute.rpcRequest()).isFalse();

        assertThat(stableServerActionRoute).isNotNull();
        assertThat(stableServerActionRoute.streamPattern()).isEqualTo("xcore:evt:server:action");
        assertThat(stableServerActionRoute.messageType()).isEqualTo("server.action");
        assertThat(stableServerActionRoute.deliveryMode()).isEqualTo(RedisDeliveryMode.EVENT);
        assertThat(stableServerActionRoute.serverScope()).isEqualTo(RedisProtocolRouteCatalog.ServerScope.BROADCAST);
        assertThat(stableServerActionRoute.readOnly()).isTrue();
        assertThat(stableServerActionRoute.rpcRequest()).isFalse();

        assertThat(stableHeartbeatRoute).isNotNull();
        assertThat(stableHeartbeatRoute.streamPattern()).isEqualTo("xcore:evt:server:heartbeat");
        assertThat(stableHeartbeatRoute.messageType()).isEqualTo("server.heartbeat");
        assertThat(stableHeartbeatRoute.deliveryMode()).isEqualTo(RedisDeliveryMode.EVENT);
        assertThat(stableHeartbeatRoute.serverScope()).isEqualTo(RedisProtocolRouteCatalog.ServerScope.BROADCAST);
        assertThat(stableHeartbeatRoute.readOnly()).isTrue();
        assertThat(stableHeartbeatRoute.rpcRequest()).isFalse();

        assertThat(stableModerationRoute).isNotNull();
        assertThat(stableModerationRoute.streamPattern()).isEqualTo("xcore:evt:moderation:ban");
        assertThat(stableModerationRoute.messageType()).isEqualTo("moderation.ban.created");
        assertThat(stableModerationRoute.readOnly()).isTrue();
        assertThat(stableModerationRoute.rpcRequest()).isFalse();

        assertThat(stableMuteRoute).isNotNull();
        assertThat(stableMuteRoute.streamPattern()).isEqualTo("xcore:evt:moderation:mute");
        assertThat(stableMuteRoute.messageType()).isEqualTo("moderation.mute.created");
        assertThat(stableMuteRoute.readOnly()).isTrue();
        assertThat(stableMuteRoute.rpcRequest()).isFalse();

        assertThat(stableDiscordLinkCodeRoute).isNotNull();
        assertThat(stableDiscordLinkCodeRoute.streamPattern()).isEqualTo("xcore:evt:discord:link-code");
        assertThat(stableDiscordLinkCodeRoute.messageType()).isEqualTo("discord.link-code-created");
        assertThat(stableDiscordLinkCodeRoute.deliveryMode()).isEqualTo(RedisDeliveryMode.EVENT);
        assertThat(stableDiscordLinkCodeRoute.serverScope()).isEqualTo(RedisProtocolRouteCatalog.ServerScope.BROADCAST);
        assertThat(stableDiscordLinkCodeRoute.readOnly()).isTrue();
        assertThat(stableDiscordLinkCodeRoute.rpcRequest()).isFalse();

        assertThat(stableDiscordStatusRoute).isNotNull();
        assertThat(stableDiscordStatusRoute.streamPattern()).isEqualTo("xcore:evt:discord:link-status");
        assertThat(stableDiscordStatusRoute.messageType()).isEqualTo("discord.link.status-changed");
        assertThat(stableDiscordStatusRoute.deliveryMode()).isEqualTo(RedisDeliveryMode.EVENT);
        assertThat(stableDiscordStatusRoute.serverScope()).isEqualTo(RedisProtocolRouteCatalog.ServerScope.BROADCAST);
        assertThat(stableDiscordStatusRoute.readOnly()).isTrue();
        assertThat(stableDiscordStatusRoute.rpcRequest()).isFalse();

        assertThat(stableCustomNicknameCommandRoute).isNotNull();
        assertThat(stableCustomNicknameCommandRoute.streamPattern()).isEqualTo("xcore:cmd:player-custom-nickname:{server}");
        assertThat(stableCustomNicknameCommandRoute.messageType()).isEqualTo("player.custom-nickname.changed.command");
        assertThat(stableCustomNicknameCommandRoute.deliveryMode()).isEqualTo(RedisDeliveryMode.COMMAND);
        assertThat(stableCustomNicknameCommandRoute.serverScope()).isEqualTo(RedisProtocolRouteCatalog.ServerScope.PAYLOAD_SERVER);
        assertThat(stableCustomNicknameCommandRoute.readOnly()).isFalse();
        assertThat(stableCustomNicknameCommandRoute.rpcRequest()).isFalse();

        assertThat(stableActiveBadgeCommandRoute).isNotNull();
        assertThat(stableActiveBadgeCommandRoute.streamPattern()).isEqualTo("xcore:cmd:player-active-badge:{server}");
        assertThat(stableActiveBadgeCommandRoute.messageType()).isEqualTo("player.active-badge.changed.command");
        assertThat(stableActiveBadgeCommandRoute.deliveryMode()).isEqualTo(RedisDeliveryMode.COMMAND);
        assertThat(stableActiveBadgeCommandRoute.serverScope()).isEqualTo(RedisProtocolRouteCatalog.ServerScope.PAYLOAD_SERVER);
        assertThat(stableActiveBadgeCommandRoute.readOnly()).isFalse();
        assertThat(stableActiveBadgeCommandRoute.rpcRequest()).isFalse();

        assertThat(stableBadgeSymbolColorModeCommandRoute).isNotNull();
        assertThat(stableBadgeSymbolColorModeCommandRoute.streamPattern()).isEqualTo("xcore:cmd:player-badge-symbol-color-mode:{server}");
        assertThat(stableBadgeSymbolColorModeCommandRoute.messageType()).isEqualTo("player.badge-symbol-color-mode.changed.command");
        assertThat(stableBadgeSymbolColorModeCommandRoute.deliveryMode()).isEqualTo(RedisDeliveryMode.COMMAND);
        assertThat(stableBadgeSymbolColorModeCommandRoute.serverScope()).isEqualTo(RedisProtocolRouteCatalog.ServerScope.PAYLOAD_SERVER);
        assertThat(stableBadgeSymbolColorModeCommandRoute.readOnly()).isFalse();
        assertThat(stableBadgeSymbolColorModeCommandRoute.rpcRequest()).isFalse();

        assertThat(stableCommandRoute).isNotNull();
        assertThat(stableCommandRoute.streamPattern()).isEqualTo("xcore:cmd:player-password-reset:{server}");
        assertThat(stableCommandRoute.messageType()).isEqualTo("player.password-reset.command");
        assertThat(stableCommandRoute.deliveryMode()).isEqualTo(RedisDeliveryMode.COMMAND);
        assertThat(stableCommandRoute.serverScope()).isEqualTo(RedisProtocolRouteCatalog.ServerScope.PAYLOAD_SERVER);
        assertThat(stableCommandRoute.readOnly()).isFalse();
        assertThat(stableCommandRoute.rpcRequest()).isFalse();

        assertThat(stableMapsLoadCommandRoute).isNotNull();
        assertThat(stableMapsLoadCommandRoute.streamPattern()).isEqualTo("xcore:cmd:maps-load:{server}");
        assertThat(stableMapsLoadCommandRoute.messageType()).isEqualTo("maps.load.command");
        assertThat(stableMapsLoadCommandRoute.deliveryMode()).isEqualTo(RedisDeliveryMode.COMMAND);
        assertThat(stableMapsLoadCommandRoute.serverScope()).isEqualTo(RedisProtocolRouteCatalog.ServerScope.PAYLOAD_SERVER);
        assertThat(stableMapsLoadCommandRoute.readOnly()).isFalse();
        assertThat(stableMapsLoadCommandRoute.rpcRequest()).isFalse();

        assertThat(stableDiscordAdminCommandRoute).isNotNull();
        assertThat(stableDiscordAdminCommandRoute.streamPattern()).isEqualTo("xcore:cmd:discord-admin-access:{server}");
        assertThat(stableDiscordAdminCommandRoute.messageType()).isEqualTo("discord.admin-access.changed.command");
        assertThat(stableDiscordAdminCommandRoute.deliveryMode()).isEqualTo(RedisDeliveryMode.COMMAND);
        assertThat(stableDiscordAdminCommandRoute.serverScope()).isEqualTo(RedisProtocolRouteCatalog.ServerScope.PAYLOAD_SERVER);
        assertThat(stableDiscordAdminCommandRoute.readOnly()).isFalse();
        assertThat(stableDiscordAdminCommandRoute.rpcRequest()).isFalse();

        assertThat(stableBroadcastCommandRoute).isNotNull();
        assertThat(stableBroadcastCommandRoute.streamPattern()).isEqualTo("xcore:cmd:execute-command:broadcast");
        assertThat(stableBroadcastCommandRoute.serverScope()).isEqualTo(RedisProtocolRouteCatalog.ServerScope.BROADCAST);
        assertThat(stableBroadcastCommandRoute.readOnly()).isFalse();

        assertThat(stableKickBannedRoute).isNotNull();
        assertThat(stableKickBannedRoute.streamPattern()).isEqualTo("xcore:cmd:kick-banned:{server}");
        assertThat(stableKickBannedRoute.messageType()).isEqualTo("moderation.kick-banned.command");
        assertThat(stableKickBannedRoute.readOnly()).isFalse();
        assertThat(stableKickBannedRoute.rpcRequest()).isFalse();

        assertThat(stableRpcRoute).isNotNull();
        assertThat(stableRpcRoute.streamPattern()).isEqualTo("xcore:rpc:req:{server}");
        assertThat(stableRpcRoute.messageType()).isEqualTo("maps.list.request");
        assertThat(stableRpcRoute.deliveryMode()).isEqualTo(RedisDeliveryMode.RPC_REQUEST);
        assertThat(stableRpcRoute.serverScope()).isEqualTo(RedisProtocolRouteCatalog.ServerScope.PAYLOAD_SERVER);
        assertThat(stableRpcRoute.readOnly()).isFalse();
        assertThat(stableRpcRoute.rpcRequest()).isTrue();
        assertThat(stableRpcRoute.responseType()).isEqualTo(MapsListResponseV1.class);

        assertThat(stableRemoveRpcRoute).isNotNull();
        assertThat(stableRemoveRpcRoute.streamPattern()).isEqualTo("xcore:rpc:req:{server}");
        assertThat(stableRemoveRpcRoute.messageType()).isEqualTo("maps.remove.request");
        assertThat(stableRemoveRpcRoute.deliveryMode()).isEqualTo(RedisDeliveryMode.RPC_REQUEST);
        assertThat(stableRemoveRpcRoute.serverScope()).isEqualTo(RedisProtocolRouteCatalog.ServerScope.PAYLOAD_SERVER);
        assertThat(stableRemoveRpcRoute.readOnly()).isFalse();
        assertThat(stableRemoveRpcRoute.rpcRequest()).isTrue();
        assertThat(stableRemoveRpcRoute.responseType()).isEqualTo(MapsRemoveResponseV1.class);
    }

    @Test
    @DisplayName("registry and router remain aligned with explicit transport topology")
    void registryAndRouterRemainAlignedWithExplicitTransportTopology() {
        // Arrange
        RedisProtocolRouteCatalog.RouteSpec playerSessionSpec = RedisProtocolRouteCatalog.routeFor(PlayerCustomNicknameChangedCommandV1.class);
        RedisProtocolRouteCatalog.RouteSpec commandSpec = RedisProtocolRouteCatalog.routeFor(PlayerPasswordResetCommandV1.class);
        RedisProtocolRouteCatalog.RouteSpec rpcSpec = RedisProtocolRouteCatalog.routeFor(MapsListRequestV1.class);
        RedisRouteDescriptor playerSessionDescriptor = routeAdapter.routeDescriptorFor(PlayerCustomNicknameChangedCommandV1.class);
        RedisRouteDescriptor commandDescriptor = routeAdapter.routeDescriptorFor(PlayerPasswordResetCommandV1.class);
        RedisRouteDescriptor rpcDescriptor = routeAdapter.routeDescriptorFor(MapsListRequestV1.class);

        // Act
        var playerSessionRoute = router.route(new PlayerCustomNicknameChangedCommandV1("uuid-7", "Commander", "survival"), "mini-pvp");
        var commandRoute = router.route(new PlayerPasswordResetCommandV1("uuid-7", "survival"), "mini-pvp");
        List<String> rpcSubscriptions = router.subscribeStreamsFor(MapsListRequestV1.class, "mini-pvp");

        // Assert
        assertThat(playerSessionDescriptor).isNotNull();
        assertThat(playerSessionDescriptor.streamPattern()).isEqualTo(playerSessionSpec.streamPattern());
        assertThat(playerSessionDescriptor.messageType()).isEqualTo(playerSessionSpec.messageType());
        assertThat(playerSessionDescriptor.isMutating()).isTrue();
        assertThat(playerSessionDescriptor.isReadOnly()).isFalse();
        assertThat(playerSessionRoute.streamKey()).isEqualTo("xcore:cmd:player-custom-nickname:survival");
        assertThat(playerSessionRoute.messageType()).isEqualTo("player.custom-nickname.changed.command");
        assertThat(playerSessionRoute.streamKey()).doesNotStartWith("xcore:evt:");

        assertThat(commandDescriptor).isNotNull();
        assertThat(commandDescriptor.streamPattern()).isEqualTo(commandSpec.streamPattern());
        assertThat(commandDescriptor.messageType()).isEqualTo(commandSpec.messageType());
        assertThat(commandDescriptor.isMutating()).isTrue();
        assertThat(commandDescriptor.isReadOnly()).isFalse();
        assertThat(commandRoute.streamKey()).isEqualTo("xcore:cmd:player-password-reset:survival");
        assertThat(commandRoute.messageType()).isEqualTo("player.password-reset.command");
        assertThat(commandRoute.streamKey()).doesNotStartWith("xcore:evt:");

        assertThat(rpcDescriptor).isNotNull();
        assertThat(rpcDescriptor.streamPattern()).isEqualTo(rpcSpec.streamPattern());
        assertThat(rpcDescriptor.messageType()).isEqualTo(rpcSpec.messageType());
        assertThat(rpcDescriptor.isRpcRequest()).isTrue();
        assertThat(rpcDescriptor.responseType()).isEqualTo(rpcSpec.responseType());
        assertThat(router.rpcTypeForRequestClass(MapsListRequestV1.class)).isEqualTo("maps.list.request");
        assertThat(rpcSubscriptions).containsExactly("xcore:rpc:req:mini-pvp");
        assertThat(router.responseTypeForRequest(MapsListRequestV1.class))
                .isEqualTo(MapsListResponseV1.class);
        assertThat(router.responseTypeForRequest(ChatMessageV1.class)).isNull();
    }

    @Test
    @DisplayName("redis route adapter mirrors generated protocol routes")
    void redisRouteAdapterMirrorsGeneratedProtocolRoutes() {
        for (ProtocolRoutes.RouteDescriptor protocolRoute : ProtocolRoutes.ROUTES_BY_PAYLOAD_TYPE.values()) {
            RedisRouteDescriptor redisRoute = routeAdapter.routeDescriptorFor(protocolRoute.payloadType());
            RedisProtocolRouteCatalog.RouteSpec topologyRoute = RedisProtocolRouteCatalog.routeFor(protocolRoute.payloadType());

            assertThat(redisRoute)
                    .as("redis route for %s", protocolRoute.messageType())
                    .isNotNull();
            assertThat(topologyRoute)
                    .as("topology route for %s", protocolRoute.messageType())
                    .isNotNull();

            assertThat(redisRoute.streamPattern()).isEqualTo(protocolRoute.stream());
            assertThat(redisRoute.messageType()).isEqualTo(protocolRoute.messageType());
            assertThat(redisRoute.ttlMillis()).isEqualTo(protocolRoute.ttlMs());
            assertThat(redisRoute.shouldClaimIdempotency())
                    .isEqualTo(protocolRoute.idempotentConsumerRecommended());
            assertThat(topologyRoute.streamPattern()).isEqualTo(protocolRoute.stream());
            assertThat(topologyRoute.messageType()).isEqualTo(protocolRoute.messageType());
            assertThat(topologyRoute.idempotentConsumerRecommended())
                    .isEqualTo(protocolRoute.idempotentConsumerRecommended());

            if (protocolRoute.response() == null) {
                assertThat(redisRoute.responseType()).isNull();
                assertThat(topologyRoute.responseType()).isNull();
            } else {
                assertThat(redisRoute.responseType()).isEqualTo(protocolRoute.response().payloadType());
                assertThat(topologyRoute.responseType()).isEqualTo(protocolRoute.response().payloadType());
            }
        }
    }
}
