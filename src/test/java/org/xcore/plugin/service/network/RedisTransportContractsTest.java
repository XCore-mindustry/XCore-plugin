package org.xcore.plugin.service.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordAdminAccessChangedCommandV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordLinkCodeCreatedV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordLinkStatusChangedV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsListRequestV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsListResponseV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationBanCreatedV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationKickBannedCommandV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationMuteCreatedV1;
import org.xcore.plugin.event.TransportEvents;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract guards for Redis metadata shared with other XCore services.
 *
 * <p>These assertions intentionally lock down stable external strings and route metadata so transport
 * refactors cannot silently change the Redis compatibility surface.</p>
 */
class RedisTransportContractsTest {

    private final RedisRouteRegistry registry = new RedisRouteRegistry();
    private final RedisStreamRouter router = new RedisStreamRouter(registry);

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
        RedisTransportTopology.RouteSpec eventRoute = RedisTransportTopology.routeFor(TransportEvents.GlobalChatEvent.class);
        RedisTransportTopology.RouteSpec moderationRoute = RedisTransportTopology.routeFor(ModerationBanCreatedV1.class);
        RedisTransportTopology.RouteSpec muteRoute = RedisTransportTopology.routeFor(ModerationMuteCreatedV1.class);
        RedisTransportTopology.RouteSpec discordLinkCodeRoute = RedisTransportTopology.routeFor(DiscordLinkCodeCreatedV1.class);
        RedisTransportTopology.RouteSpec discordStatusRoute = RedisTransportTopology.routeFor(DiscordLinkStatusChangedV1.class);
        RedisTransportTopology.RouteSpec commandRoute = RedisTransportTopology.routeFor(TransportEvents.PlayerPasswordReset.class);
        RedisTransportTopology.RouteSpec discordAdminCommandRoute = RedisTransportTopology.routeFor(DiscordAdminAccessChangedCommandV1.class);
        RedisTransportTopology.RouteSpec broadcastCommandRoute = RedisTransportTopology.routeFor(TransportEvents.ExecuteCommand.class);
        RedisTransportTopology.RouteSpec rpcRoute = RedisTransportTopology.routeFor(MapsListRequestV1.class);
        RedisTransportTopology.RouteSpec kickBannedRoute = RedisTransportTopology.routeFor(ModerationKickBannedCommandV1.class);

        // Act
        RedisTransportTopology.RouteSpec stableEventRoute = eventRoute;
        RedisTransportTopology.RouteSpec stableModerationRoute = moderationRoute;
        RedisTransportTopology.RouteSpec stableMuteRoute = muteRoute;
        RedisTransportTopology.RouteSpec stableDiscordLinkCodeRoute = discordLinkCodeRoute;
        RedisTransportTopology.RouteSpec stableDiscordStatusRoute = discordStatusRoute;
        RedisTransportTopology.RouteSpec stableCommandRoute = commandRoute;
        RedisTransportTopology.RouteSpec stableDiscordAdminCommandRoute = discordAdminCommandRoute;
        RedisTransportTopology.RouteSpec stableBroadcastCommandRoute = broadcastCommandRoute;
        RedisTransportTopology.RouteSpec stableRpcRoute = rpcRoute;
        RedisTransportTopology.RouteSpec stableKickBannedRoute = kickBannedRoute;

        // Assert
        assertThat(stableEventRoute).isNotNull();
        assertThat(stableEventRoute.streamPattern()).isEqualTo("xcore:evt:chat:global");
        assertThat(stableEventRoute.eventType()).isEqualTo("chat.global");
        assertThat(stableEventRoute.deliveryMode()).isEqualTo(RedisTransportTopology.DeliveryMode.EVENT);
        assertThat(stableEventRoute.serverScope()).isEqualTo(RedisTransportTopology.ServerScope.BROADCAST);
        assertThat(stableEventRoute.readOnly()).isTrue();
        assertThat(stableEventRoute.rpcRequest()).isFalse();

        assertThat(stableModerationRoute).isNotNull();
        assertThat(stableModerationRoute.streamPattern()).isEqualTo("xcore:evt:moderation:ban");
        assertThat(stableModerationRoute.eventType()).isEqualTo("moderation.ban.created");
        assertThat(stableModerationRoute.readOnly()).isTrue();
        assertThat(stableModerationRoute.rpcRequest()).isFalse();

        assertThat(stableMuteRoute).isNotNull();
        assertThat(stableMuteRoute.streamPattern()).isEqualTo("xcore:evt:moderation:mute");
        assertThat(stableMuteRoute.eventType()).isEqualTo("moderation.mute.created");
        assertThat(stableMuteRoute.readOnly()).isTrue();
        assertThat(stableMuteRoute.rpcRequest()).isFalse();

        assertThat(stableDiscordLinkCodeRoute).isNotNull();
        assertThat(stableDiscordLinkCodeRoute.streamPattern()).isEqualTo("xcore:evt:discord:link-code");
        assertThat(stableDiscordLinkCodeRoute.eventType()).isEqualTo("discord.link-code-created");
        assertThat(stableDiscordLinkCodeRoute.deliveryMode()).isEqualTo(RedisTransportTopology.DeliveryMode.EVENT);
        assertThat(stableDiscordLinkCodeRoute.serverScope()).isEqualTo(RedisTransportTopology.ServerScope.BROADCAST);
        assertThat(stableDiscordLinkCodeRoute.readOnly()).isTrue();
        assertThat(stableDiscordLinkCodeRoute.rpcRequest()).isFalse();

        assertThat(stableDiscordStatusRoute).isNotNull();
        assertThat(stableDiscordStatusRoute.streamPattern()).isEqualTo("xcore:evt:discord:link-status");
        assertThat(stableDiscordStatusRoute.eventType()).isEqualTo("discord.link.status-changed");
        assertThat(stableDiscordStatusRoute.deliveryMode()).isEqualTo(RedisTransportTopology.DeliveryMode.EVENT);
        assertThat(stableDiscordStatusRoute.serverScope()).isEqualTo(RedisTransportTopology.ServerScope.BROADCAST);
        assertThat(stableDiscordStatusRoute.readOnly()).isTrue();
        assertThat(stableDiscordStatusRoute.rpcRequest()).isFalse();

        assertThat(stableCommandRoute).isNotNull();
        assertThat(stableCommandRoute.streamPattern()).isEqualTo("xcore:cmd:player-password-reset:{server}");
        assertThat(stableCommandRoute.eventType()).isEqualTo("player.password_reset");
        assertThat(stableCommandRoute.deliveryMode()).isEqualTo(RedisTransportTopology.DeliveryMode.COMMAND);
        assertThat(stableCommandRoute.serverScope()).isEqualTo(RedisTransportTopology.ServerScope.DEFAULT_SERVER);
        assertThat(stableCommandRoute.readOnly()).isFalse();
        assertThat(stableCommandRoute.rpcRequest()).isFalse();

        assertThat(stableDiscordAdminCommandRoute).isNotNull();
        assertThat(stableDiscordAdminCommandRoute.streamPattern()).isEqualTo("xcore:cmd:discord-admin-access:{server}");
        assertThat(stableDiscordAdminCommandRoute.eventType()).isEqualTo("discord.admin-access.changed.command");
        assertThat(stableDiscordAdminCommandRoute.deliveryMode()).isEqualTo(RedisTransportTopology.DeliveryMode.COMMAND);
        assertThat(stableDiscordAdminCommandRoute.serverScope()).isEqualTo(RedisTransportTopology.ServerScope.PAYLOAD_SERVER);
        assertThat(stableDiscordAdminCommandRoute.readOnly()).isFalse();
        assertThat(stableDiscordAdminCommandRoute.rpcRequest()).isFalse();

        assertThat(stableBroadcastCommandRoute).isNotNull();
        assertThat(stableBroadcastCommandRoute.streamPattern()).isEqualTo("xcore:cmd:execute-command:broadcast");
        assertThat(stableBroadcastCommandRoute.serverScope()).isEqualTo(RedisTransportTopology.ServerScope.BROADCAST);
        assertThat(stableBroadcastCommandRoute.readOnly()).isFalse();

        assertThat(stableKickBannedRoute).isNotNull();
        assertThat(stableKickBannedRoute.streamPattern()).isEqualTo("xcore:cmd:kick-banned:{server}");
        assertThat(stableKickBannedRoute.eventType()).isEqualTo("moderation.kick-banned.command");
        assertThat(stableKickBannedRoute.readOnly()).isFalse();
        assertThat(stableKickBannedRoute.rpcRequest()).isFalse();

        assertThat(stableRpcRoute).isNotNull();
        assertThat(stableRpcRoute.streamPattern()).isEqualTo("xcore:rpc:req:{server}");
        assertThat(stableRpcRoute.eventType()).isEqualTo("maps.list.request");
        assertThat(stableRpcRoute.deliveryMode()).isEqualTo(RedisTransportTopology.DeliveryMode.RPC_REQUEST);
        assertThat(stableRpcRoute.serverScope()).isEqualTo(RedisTransportTopology.ServerScope.PAYLOAD_SERVER);
        assertThat(stableRpcRoute.readOnly()).isFalse();
        assertThat(stableRpcRoute.rpcRequest()).isTrue();
        assertThat(stableRpcRoute.responseType()).isEqualTo(MapsListResponseV1.class);
    }

    @Test
    @DisplayName("registry and router remain aligned with explicit transport topology")
    void registryAndRouterRemainAlignedWithExplicitTransportTopology() {
        // Arrange
        RedisTransportTopology.RouteSpec commandSpec = RedisTransportTopology.routeFor(TransportEvents.PlayerPasswordReset.class);
        RedisTransportTopology.RouteSpec rpcSpec = RedisTransportTopology.routeFor(MapsListRequestV1.class);
        RedisRouteDescriptor commandDescriptor = registry.routeDescriptorFor(TransportEvents.PlayerPasswordReset.class);
        RedisRouteDescriptor rpcDescriptor = registry.routeDescriptorFor(MapsListRequestV1.class);

        // Act
        var commandRoute = router.route(new TransportEvents.PlayerPasswordReset("uuid-7"), "mini-pvp");
        List<String> rpcSubscriptions = router.subscribeStreamsFor(MapsListRequestV1.class, "mini-pvp");

        // Assert
        assertThat(commandDescriptor).isNotNull();
        assertThat(commandDescriptor.streamPattern()).isEqualTo(commandSpec.streamPattern());
        assertThat(commandDescriptor.eventType()).isEqualTo(commandSpec.eventType());
        assertThat(commandDescriptor.isMutating()).isTrue();
        assertThat(commandDescriptor.isReadOnly()).isFalse();
        assertThat(commandRoute.streamKey()).isEqualTo("xcore:cmd:player-password-reset:mini-pvp");
        assertThat(commandRoute.eventType()).isEqualTo("player.password_reset");
        assertThat(commandRoute.streamKey()).doesNotStartWith("xcore:evt:");

        assertThat(rpcDescriptor).isNotNull();
        assertThat(rpcDescriptor.streamPattern()).isEqualTo(rpcSpec.streamPattern());
        assertThat(rpcDescriptor.eventType()).isEqualTo(rpcSpec.eventType());
        assertThat(rpcDescriptor.isRpcRequest()).isTrue();
        assertThat(rpcDescriptor.responseType()).isEqualTo(rpcSpec.responseType());
        assertThat(router.rpcTypeForRequestClass(MapsListRequestV1.class)).isEqualTo("maps.list.request");
        assertThat(rpcSubscriptions).containsExactly("xcore:rpc:req:mini-pvp");
        assertThat(router.responseTypeForRequest(MapsListRequestV1.class))
                .isEqualTo(MapsListResponseV1.class);
        assertThat(router.responseTypeForRequest(TransportEvents.MessageEvent.class)).isNull();
    }
}
