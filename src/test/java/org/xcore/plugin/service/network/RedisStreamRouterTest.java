package org.xcore.plugin.service.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.model.BanData;
import org.xcore.plugin.model.MuteData;

import static org.assertj.core.api.Assertions.assertThat;

class RedisStreamRouterTest {

    private final RedisStreamRouter router = new RedisStreamRouter();

    @Test
    @DisplayName("route maps read-only events to expected stream and event type")
    void routeReadOnlyEvents() {
        var messageRoute = router.route(new SocketEvents.MessageEvent("a", "b", "mini-pvp"), "mini-pvp");
        var joinRoute = router.route(new SocketEvents.PlayerJoinLeaveEvent("p", "mini-pvp", true), "mini-pvp");
        var banRoute = router.route(BanData.builder().uuid("u").name("n").build(), "mini-pvp");
        var muteRoute = router.route(MuteData.builder().uuid("u").name("n").build(), "mini-pvp");

        assertThat(messageRoute.streamKey()).isEqualTo("xcore:evt:chat:message");
        assertThat(messageRoute.eventType()).isEqualTo("chat.message");

        assertThat(joinRoute.streamKey()).isEqualTo("xcore:evt:player:joinleave");
        assertThat(joinRoute.eventType()).isEqualTo("player.join_leave");

        assertThat(banRoute.streamKey()).isEqualTo("xcore:evt:moderation:ban");
        assertThat(banRoute.eventType()).isEqualTo("moderation.ban");

        assertThat(muteRoute.streamKey()).isEqualTo("xcore:evt:moderation:mute");
        assertThat(muteRoute.eventType()).isEqualTo("moderation.mute");
    }

    @Test
    @DisplayName("route maps server-targeted events using event payload server")
    void routeServerTargetedEvents() {
        var discordRoute = router.route(new SocketEvents.DiscordMessageEvent("bot", "hello", "mini-hexed"), "mini-pvp");
        var mapsRoute = router.route(new SocketEvents.LoadMapsV2(new SocketEvents.FileURL[0], "event"), "mini-pvp");
        var badgeRoute = router.route(new SocketEvents.PlayerBadgeInventoryChanged("uuid-7", "translator", java.util.Set.of("translator")), "mini-pvp");
        var passwordRoute = router.route(new SocketEvents.PlayerPasswordReset("uuid-7"), "mini-pvp");

        assertThat(discordRoute.streamKey()).isEqualTo("xcore:cmd:discord-message:mini-hexed");
        assertThat(mapsRoute.streamKey()).isEqualTo("xcore:cmd:maps-load:event");
        assertThat(badgeRoute.streamKey()).isEqualTo("xcore:cmd:player-badge-inventory:mini-pvp");
        assertThat(badgeRoute.eventType()).isEqualTo("player.badge_inventory");
        assertThat(passwordRoute.streamKey()).isEqualTo("xcore:cmd:player-password-reset:mini-pvp");
        assertThat(passwordRoute.eventType()).isEqualTo("player.password_reset");
    }

    @Test
    @DisplayName("subscribe streams include read-only and rpc request streams")
    void subscribeStreamsForTypes() {
        assertThat(router.subscribeStreamsFor(SocketEvents.GlobalChatEvent.class, "mini-pvp"))
                .containsExactly("xcore:evt:chat:global");

        assertThat(router.subscribeStreamsFor(SocketEvents.MapsListRequest.class, "mini-pvp"))
                .containsExactly("xcore:rpc:req:mini-pvp");

        assertThat(router.subscribeStreamsFor(SocketEvents.MapRemoveRequest.class, "mini-pvp"))
                .containsExactly("xcore:rpc:req:mini-pvp");

        assertThat(router.subscribeStreamsFor(SocketEvents.PlayerPasswordReset.class, "mini-pvp"))
                .containsExactly("xcore:cmd:player-password-reset:mini-pvp");

        assertThat(router.subscribeStreamsFor(BanData.class, "mini-pvp"))
                .containsExactly("xcore:evt:moderation:ban");

        assertThat(router.subscribeStreamsFor(MuteData.class, "mini-pvp"))
                .containsExactly("xcore:evt:moderation:mute");
    }

    @Test
    @DisplayName("type classification and rpc response mapping are correct")
    void classificationAndResponseMapping() {
        assertThat(router.isReadOnlyType(SocketEvents.AdminRequestEvent.class)).isTrue();
        assertThat(router.isReadOnlyType(BanData.class)).isTrue();
        assertThat(router.isReadOnlyType(MuteData.class)).isTrue();
        assertThat(router.isReadOnlyType(SocketEvents.RemoveAdmin.class)).isFalse();

        assertThat(router.isMutatingType(SocketEvents.RemoveAdmin.class)).isTrue();
        assertThat(router.isMutatingType(SocketEvents.PlayerPasswordReset.class)).isTrue();
        assertThat(router.isMutatingType(SocketEvents.MessageEvent.class)).isFalse();

        assertThat(router.isRpcRequestType(SocketEvents.MapsListRequest.class)).isTrue();
        assertThat(router.isRpcRequestType(SocketEvents.MessageEvent.class)).isFalse();

        assertThat(router.responseTypeForRequest(SocketEvents.MapsListRequest.class))
                .isEqualTo(SocketEvents.MapsListResponse.class);
        assertThat(router.responseTypeForRequest(SocketEvents.MapRemoveRequest.class))
                .isEqualTo(SocketEvents.MapRemoveResponse.class);

        assertThat(router.rpcTypeForRequestClass(SocketEvents.MapsListRequest.class))
                .isEqualTo("maps.list");
        assertThat(router.rpcTypeForRequestClass(SocketEvents.MapRemoveRequest.class))
                .isEqualTo("maps.remove");
    }
}
