package org.xcore.plugin.service.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.event.SocketEvents.VoteKickEvent;
import org.xcore.plugin.event.SocketEvents.VoteKickParticipant;
import org.xcore.plugin.model.BanData;
import org.xcore.plugin.model.MuteData;
import org.xcore.plugin.model.Punishment;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedisStreamRouterTest {

    private final RedisStreamRouter router = new RedisStreamRouter();

    @Test
    @DisplayName("route maps read-only events to expected stream and event type")
    void routeReadOnlyEvents() {
        BanData banData = punishment(new BanData(), "u", "n");
        MuteData muteData = punishment(new MuteData(), "u", "n");

        var messageRoute = router.route(new SocketEvents.MessageEvent("a", "b", "mini-pvp"), "mini-pvp");
        var joinRoute = router.route(new SocketEvents.PlayerJoinLeaveEvent("p", "mini-pvp", true), "mini-pvp");
        var banRoute = router.route(banData, "mini-pvp");
        var muteRoute = router.route(muteData, "mini-pvp");
        var voteKickRoute = router.route(
                new VoteKickEvent(
                        "target",
                        42,
                        "uuid-target",
                        "starter",
                        7,
                        "123",
                        "griefing",
                        List.of(new VoteKickParticipant("starter", 7, "123")),
                        List.of(),
                        "started",
                        "mini-pvp",
                        10L
                ),
                "mini-pvp"
        );

        assertThat(messageRoute.streamKey()).isEqualTo("xcore:evt:chat:message");
        assertThat(messageRoute.eventType()).isEqualTo("chat.message");

        assertThat(joinRoute.streamKey()).isEqualTo("xcore:evt:player:joinleave");
        assertThat(joinRoute.eventType()).isEqualTo("player.join_leave");

        assertThat(banRoute.streamKey()).isEqualTo("xcore:evt:moderation:ban");
        assertThat(banRoute.eventType()).isEqualTo("moderation.ban");

        assertThat(muteRoute.streamKey()).isEqualTo("xcore:evt:moderation:mute");
        assertThat(muteRoute.eventType()).isEqualTo("moderation.mute");

        assertThat(voteKickRoute.streamKey()).isEqualTo("xcore:evt:moderation:votekick");
        assertThat(voteKickRoute.eventType()).isEqualTo("moderation.votekick");
    }

    @Test
    @DisplayName("route maps server-targeted events using event payload server")
    void routeServerTargetedEvents() {
        var discordRoute = router.route(new SocketEvents.DiscordMessageEvent("bot", "hello", "mini-hexed"), "mini-pvp");
        var mapsRoute = router.route(new SocketEvents.LoadMapsV2(new SocketEvents.FileURL[0], "event"), "mini-pvp");
        var badgeRoute = router.route(new SocketEvents.PlayerBadgeInventoryChanged("uuid-7", "translator", java.util.Set.of("translator")), "mini-pvp");
        var passwordRoute = router.route(new SocketEvents.PlayerPasswordReset("uuid-7"), "mini-pvp");
        var discordLinkConfirmRoute = router.route(new SocketEvents.DiscordLinkConfirmEvent("ABC123", "uuid-7", 7, "123", "discord-user", "mini-hexed", 10L), "mini-pvp");
        var discordLinkStatusRoute = router.route(new SocketEvents.DiscordLinkStatusChangedEvent("uuid-7", 7, "Nick", "123", "discord-user", "linked", "mini-pvp", 10L), "mini-pvp");
        var discordAdminAccessRoute = router.route(new SocketEvents.DiscordAdminAccessChanged("uuid-7", 7, "123", "discord-user", true, "DISCORD_ROLE", "tester", "sync", "mini-pvp", 11L), "mini-pvp");

        assertThat(discordRoute.streamKey()).isEqualTo("xcore:cmd:discord-message:mini-hexed");
        assertThat(mapsRoute.streamKey()).isEqualTo("xcore:cmd:maps-load:event");
        assertThat(badgeRoute.streamKey()).isEqualTo("xcore:cmd:player-badge-inventory:mini-pvp");
        assertThat(badgeRoute.eventType()).isEqualTo("player.badge_inventory");
        assertThat(passwordRoute.streamKey()).isEqualTo("xcore:cmd:player-password-reset:mini-pvp");
        assertThat(passwordRoute.eventType()).isEqualTo("player.password_reset");
        assertThat(discordLinkConfirmRoute.streamKey()).isEqualTo("xcore:cmd:discord-link-confirm:mini-hexed");
        assertThat(discordLinkConfirmRoute.eventType()).isEqualTo("discord.link_confirm");
        assertThat(discordLinkStatusRoute.streamKey()).isEqualTo("xcore:evt:discord:link-status");
        assertThat(discordLinkStatusRoute.eventType()).isEqualTo("discord.link_status_changed");
        assertThat(discordAdminAccessRoute.streamKey()).isEqualTo("xcore:cmd:discord-admin-access:mini-pvp");
        assertThat(discordAdminAccessRoute.eventType()).isEqualTo("discord.admin_access_changed");

        var discordAdminAccessOtherServerRoute = router.route(new SocketEvents.DiscordAdminAccessChanged("uuid-8", 8, "456", "other-user", false, "NONE", "tester", "sync", "survival", 12L), "mini-pvp");
        assertThat(discordAdminAccessOtherServerRoute.streamKey()).isEqualTo("xcore:cmd:discord-admin-access:survival");
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

        assertThat(router.subscribeStreamsFor(SocketEvents.DiscordLinkConfirmEvent.class, "mini-pvp"))
                .containsExactly("xcore:cmd:discord-link-confirm:mini-pvp");

        assertThat(router.subscribeStreamsFor(SocketEvents.DiscordLinkStatusChangedEvent.class, "mini-pvp"))
                .containsExactly("xcore:evt:discord:link-status");

        assertThat(router.subscribeStreamsFor(SocketEvents.DiscordAdminAccessChanged.class, "mini-pvp"))
                .containsExactly("xcore:cmd:discord-admin-access:mini-pvp");

        assertThat(router.subscribeStreamsFor(BanData.class, "mini-pvp"))
                .containsExactly("xcore:evt:moderation:ban");

        assertThat(router.subscribeStreamsFor(MuteData.class, "mini-pvp"))
                .containsExactly("xcore:evt:moderation:mute");

        assertThat(router.subscribeStreamsFor(VoteKickEvent.class, "mini-pvp"))
                .containsExactly("xcore:evt:moderation:votekick");
    }

    @Test
    @DisplayName("type classification and rpc response mapping are correct")
    void classificationAndResponseMapping() {
        assertThat(router.isReadOnlyType(SocketEvents.DiscordLinkStatusChangedEvent.class)).isTrue();
        assertThat(router.isReadOnlyType(BanData.class)).isTrue();
        assertThat(router.isReadOnlyType(MuteData.class)).isTrue();
        assertThat(router.isReadOnlyType(VoteKickEvent.class)).isTrue();
        assertThat(router.isReadOnlyType(SocketEvents.DiscordAdminAccessChanged.class)).isFalse();

        assertThat(router.isMutatingType(SocketEvents.PlayerPasswordReset.class)).isTrue();
        assertThat(router.isMutatingType(SocketEvents.DiscordLinkConfirmEvent.class)).isTrue();
        assertThat(router.isMutatingType(SocketEvents.DiscordAdminAccessChanged.class)).isTrue();
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

    private static <T extends Punishment> T punishment(T value, String uuid, String name) {
        value.uuid = uuid;
        value.name = name;
        value.adminName = "admin";
        value.reason = "rule";
        value.expireDate = Instant.now().plusSeconds(60);
        return value;
    }
}
