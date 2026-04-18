package org.xcore.plugin.service.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.event.TransportEvents;
import org.xcore.plugin.model.BanData;

import static org.assertj.core.api.Assertions.assertThat;

class RedisRouteRegistryTest {

    private final RedisRouteRegistry registry = new RedisRouteRegistry();

    @Test
    @DisplayName("payload server resolver uses typed server contract")
    void payloadServerResolverUsesTypedContract() {
        RedisRouteDescriptor descriptor = registry.routeDescriptorFor(TransportEvents.DiscordLinkConfirmEvent.class);

        String stream = registry.resolveStreamKey(
                descriptor,
                new TransportEvents.DiscordLinkConfirmEvent("code", "uuid", 1, "discord", "user", "survival", 123L),
                "mini-pvp"
        );

        assertThat(stream).isEqualTo("xcore:cmd:discord-link-confirm:survival");
    }

    @Test
    @DisplayName("default server resolver keeps server-local mutating events on current server")
    void defaultServerResolverUsesDefaultServer() {
        RedisRouteDescriptor descriptor = registry.routeDescriptorFor(TransportEvents.PlayerPasswordReset.class);

        String stream = registry.resolveStreamKey(
                descriptor,
                new TransportEvents.PlayerPasswordReset("uuid-1"),
                "mini-pvp"
        );

        assertThat(stream).isEqualTo("xcore:cmd:player-password-reset:mini-pvp");
    }

    @Test
    @DisplayName("rpc route descriptor carries response type metadata")
    void rpcRouteDescriptorCarriesResponseType() {
        RedisRouteDescriptor descriptor = registry.routeDescriptorFor(TransportEvents.MapsListRequest.class);

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.isRpcRequest()).isTrue();
        assertThat(descriptor.responseType()).isEqualTo(TransportEvents.MapsListResponse.class);
        assertThat(registry.rpcTypeForRequestClass(TransportEvents.MapsListRequest.class)).isEqualTo("maps.list");
    }

    @Test
    @DisplayName("read-only and mutating classification comes from registry descriptors")
    void classificationComesFromRegistry() {
        assertThat(registry.isReadOnlyType(TransportEvents.GlobalChatEvent.class)).isTrue();
        assertThat(registry.isReadOnlyType(BanData.class)).isTrue();
        assertThat(registry.isMutatingType(TransportEvents.ExecuteCommand.class)).isTrue();
        assertThat(registry.isMutatingType(TransportEvents.GlobalChatEvent.class)).isFalse();
    }
}
