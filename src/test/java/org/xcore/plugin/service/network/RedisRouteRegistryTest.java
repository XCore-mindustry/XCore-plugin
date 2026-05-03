package org.xcore.plugin.service.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.protocol.generated.messages.chat.ChatMessages.ChatDiscordIngressCommandV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.ChatMessageV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.ServerHeartbeatV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordLinkConfirmCommandV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsLoadCommandV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationBanCreatedV1;
import org.xcore.protocol.generated.shared.DiscordIdentityRefV1;
import org.xcore.protocol.generated.shared.MapFileSourceV1;
import org.xcore.protocol.generated.shared.PlayerRefV1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisRouteRegistryTest {

    private final RedisRouteRegistry registry = new RedisRouteRegistry();
    private final RedisStreamRouter router = new RedisStreamRouter(registry);

    @Test
    @DisplayName("chat message resolves to chat stream descriptor")
    void testChatMessageRoutesToChatStream() {
        var descriptor = registry.routeDescriptorFor(ChatMessageV1.class);

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.streamPattern()).isEqualTo("xcore:evt:chat:message");
        assertThat(descriptor.isReadOnly()).isTrue();
        assertThat(descriptor.isMutating()).isFalse();
    }

    @Test
    @DisplayName("server heartbeat resolves to chat stream descriptor")
    void testServerHeartbeatRoutesToChatStream() {
        var descriptor = registry.routeDescriptorFor(ServerHeartbeatV1.class);

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.streamPattern()).isEqualTo("xcore:evt:server:heartbeat");
        assertThat(descriptor.isReadOnly()).isTrue();
        assertThat(descriptor.isMutating()).isFalse();
    }

    @Test
    @DisplayName("moderation ban resolves to moderation stream descriptor")
    void testModerationBanRoutesToModerationStream() {
        var descriptor = registry.routeDescriptorFor(ModerationBanCreatedV1.class);

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.streamPattern()).isEqualTo("xcore:evt:moderation:ban");
        assertThat(descriptor.isReadOnly()).isTrue();
        assertThat(descriptor.isMutating()).isFalse();
    }

    @Test
    @DisplayName("discord link confirm resolves to discord stream descriptor")
    void testDiscordLinkConfirmRoutesToDiscordStream() {
        var payload = new DiscordLinkConfirmCommandV1(
                "code",
                new PlayerRefV1("uuid", 1, "Player", null),
                new DiscordIdentityRefV1("discord", "user"),
                "survival",
                "2026-04-28T00:00:00Z"
        );
        var descriptor = registry.routeDescriptorFor(payload);

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.streamPattern()).isEqualTo("xcore:cmd:discord-link-confirm:{server}");
        assertThat(registry.resolveStreamKey(descriptor, payload, "mini-pvp"))
                .isEqualTo("xcore:cmd:discord-link-confirm:survival");
    }

    @Test
    @DisplayName("maps load command resolves to maps stream descriptor")
    void testMapsLoadCommandRoutesToMapsStream() {
        var payload = new MapsLoadCommandV1(
                "survival",
                java.util.List.of(new MapFileSourceV1("https://example/maps/a.msav", "a.msav"))
        );
        var descriptor = registry.routeDescriptorFor(payload);

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.streamPattern()).isEqualTo("xcore:cmd:maps-load:{server}");
        assertThat(registry.resolveStreamKey(descriptor, payload, "mini-pvp"))
                .isEqualTo("xcore:cmd:maps-load:survival");
    }

    @Test
    @DisplayName("unsupported payloads throw when routing")
    void testUnregisteredPayloadThrows() {
        assertThat(registry.routeDescriptorFor(new Object())).isNull();
        assertThatThrownBy(() -> router.route(new Object(), "mini-pvp"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining(Object.class.getName());
    }

    @Test
    @DisplayName("typed payload server routing remains registered")
    void typedPayloadServerRoutingRemainsRegistered() {
        var descriptor = registry.routeDescriptorFor(ChatDiscordIngressCommandV1.class);

        assertThat(descriptor).isNotNull();
        assertThat(registry.resolveStreamKey(descriptor, new ChatDiscordIngressCommandV1("bot", "hello", "survival"), "mini-pvp"))
                .isEqualTo("xcore:cmd:discord-message:survival");
    }
}
