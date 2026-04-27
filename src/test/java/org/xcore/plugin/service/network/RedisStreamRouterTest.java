package org.xcore.plugin.service.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages;
import org.xcore.protocol.generated.shared.ActorRefV1;
import org.xcore.protocol.generated.shared.PlayerRefV1;
import org.xcore.plugin.event.TransportEvents;
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

        var messageRoute = router.route(new TransportEvents.MessageEvent("a", "b", "mini-pvp"), "mini-pvp");
        var joinRoute = router.route(new TransportEvents.PlayerJoinLeaveEvent("p", "mini-pvp", true), "mini-pvp");
        var banRoute = router.route(
                org.xcore.plugin.service.network.ModerationProtocolMapper.toBanCreatedEvent(
                        banData,
                        "mini-pvp",
                        Instant.parse("2026-04-26T00:00:00Z")
                ),
                "mini-pvp"
        );
        var muteRoute = router.route(
                org.xcore.plugin.service.network.ModerationProtocolMapper.toMuteCreatedEvent(
                        muteData,
                        "mini-pvp",
                        Instant.parse("2026-04-26T00:00:01Z")
                ),
                "mini-pvp"
        );
        var voteKickRoute = router.route(
                org.xcore.plugin.service.network.ModerationProtocolMapper.toVoteKickCreatedEvent(
                        "uuid-target",
                        42,
                        "target",
                        "starter",
                        7,
                        "123",
                        "griefing",
                        List.of(org.xcore.plugin.service.network.ModerationProtocolMapper.toVoteKickParticipant("starter", 7, "123")),
                        List.of(),
                        "mini-pvp",
                        Instant.parse("2026-04-26T00:00:02Z")
                ),
                "mini-pvp"
        );
        var auditRoute = router.route(
                new TransportEvents.ModerationAuditAppendedProtocolEvent(
                        new ModerationMessages.ModerationAuditAppendedV1(
                                "ban",
                                new PlayerRefV1("uuid-target", 42, "target", null),
                                new ActorRefV1("Admin", "admin-1", "discord"),
                                "reason",
                                "mini-pvp",
                                Instant.parse("2026-04-26T00:00:03Z").toString(),
                                java.util.Map.of("durationMs", 60000L)
                        )
                ),
                "mini-pvp"
        );

        assertThat(messageRoute.streamKey()).isEqualTo("xcore:evt:chat:message");
        assertThat(messageRoute.eventType()).isEqualTo("chat.message");

        assertThat(joinRoute.streamKey()).isEqualTo("xcore:evt:player:joinleave");
        assertThat(joinRoute.eventType()).isEqualTo("player.join_leave");

        assertThat(banRoute.streamKey()).isEqualTo("xcore:evt:moderation:ban");
        assertThat(banRoute.eventType()).isEqualTo("moderation.ban.created");

        assertThat(muteRoute.streamKey()).isEqualTo("xcore:evt:moderation:mute");
        assertThat(muteRoute.eventType()).isEqualTo("moderation.mute.created");

        assertThat(voteKickRoute.streamKey()).isEqualTo("xcore:evt:moderation:votekick");
        assertThat(voteKickRoute.eventType()).isEqualTo("moderation.vote-kick.created");

        assertThat(auditRoute.streamKey()).isEqualTo("xcore:evt:moderation:audit");
        assertThat(auditRoute.eventType()).isEqualTo("moderation.audit.appended");
    }

    @Test
    @DisplayName("route maps server-targeted events using event payload server")
    void routeServerTargetedEvents() {
        var discordRoute = router.route(new TransportEvents.DiscordMessageEvent("bot", "hello", "mini-hexed"), "mini-pvp");
        var mapsRoute = router.route(new TransportEvents.LoadMapsV2(new TransportEvents.FileURL[0], "event"), "mini-pvp");
        var badgeRoute = router.route(new TransportEvents.PlayerBadgeInventoryChanged("uuid-7", "translator", java.util.Set.of("translator")), "mini-pvp");
        var badgeColorModeRoute = router.route(new TransportEvents.PlayerBadgeSymbolColorModeChanged("uuid-7", "player-color"), "mini-pvp");
        var passwordRoute = router.route(new TransportEvents.PlayerPasswordReset("uuid-7"), "mini-pvp");
        var discordLinkConfirmRoute = router.route(new TransportEvents.DiscordLinkConfirmEvent("ABC123", "uuid-7", 7, "123", "discord-user", "mini-hexed", 10L), "mini-pvp");
        var discordLinkStatusRoute = router.route(new TransportEvents.DiscordLinkStatusChangedEvent("uuid-7", 7, "Nick", "123", "discord-user", "linked", "mini-pvp", 10L), "mini-pvp");
        var discordAdminAccessRoute = router.route(new TransportEvents.DiscordAdminAccessChanged("uuid-7", 7, "123", "discord-user", true, "DISCORD_ROLE", "tester", "sync", "mini-pvp", 11L), "mini-pvp");

        assertThat(discordRoute.streamKey()).isEqualTo("xcore:cmd:discord-message:mini-hexed");
        assertThat(mapsRoute.streamKey()).isEqualTo("xcore:cmd:maps-load:event");
        assertThat(badgeRoute.streamKey()).isEqualTo("xcore:cmd:player-badge-inventory:mini-pvp");
        assertThat(badgeRoute.eventType()).isEqualTo("player.badge_inventory");
        assertThat(badgeColorModeRoute.streamKey()).isEqualTo("xcore:cmd:player-badge-symbol-color-mode:mini-pvp");
        assertThat(badgeColorModeRoute.eventType()).isEqualTo("player.badge_symbol_color_mode");
        assertThat(passwordRoute.streamKey()).isEqualTo("xcore:cmd:player-password-reset:mini-pvp");
        assertThat(passwordRoute.eventType()).isEqualTo("player.password_reset");
        assertThat(discordLinkConfirmRoute.streamKey()).isEqualTo("xcore:cmd:discord-link-confirm:mini-hexed");
        assertThat(discordLinkConfirmRoute.eventType()).isEqualTo("discord.link_confirm");
        assertThat(discordLinkStatusRoute.streamKey()).isEqualTo("xcore:evt:discord:link-status");
        assertThat(discordLinkStatusRoute.eventType()).isEqualTo("discord.link_status_changed");
        assertThat(discordAdminAccessRoute.streamKey()).isEqualTo("xcore:cmd:discord-admin-access:mini-pvp");
        assertThat(discordAdminAccessRoute.eventType()).isEqualTo("discord.admin_access_changed");

        var discordAdminAccessOtherServerRoute = router.route(new TransportEvents.DiscordAdminAccessChanged("uuid-8", 8, "456", "other-user", false, "NONE", "tester", "sync", "survival", 12L), "mini-pvp");
        assertThat(discordAdminAccessOtherServerRoute.streamKey()).isEqualTo("xcore:cmd:discord-admin-access:survival");
    }

    @Test
    @DisplayName("subscribe streams include read-only and rpc request streams")
    void subscribeStreamsForTypes() {
        assertThat(router.subscribeStreamsFor(TransportEvents.GlobalChatEvent.class, "mini-pvp"))
                .containsExactly("xcore:evt:chat:global");

        assertThat(router.subscribeStreamsFor(TransportEvents.MapsListRequest.class, "mini-pvp"))
                .containsExactly("xcore:rpc:req:mini-pvp");

        assertThat(router.subscribeStreamsFor(TransportEvents.MapRemoveRequest.class, "mini-pvp"))
                .containsExactly("xcore:rpc:req:mini-pvp");

        assertThat(router.subscribeStreamsFor(TransportEvents.PlayerPasswordReset.class, "mini-pvp"))
                .containsExactly("xcore:cmd:player-password-reset:mini-pvp");

        assertThat(router.subscribeStreamsFor(TransportEvents.PlayerBadgeSymbolColorModeChanged.class, "mini-pvp"))
                .containsExactly("xcore:cmd:player-badge-symbol-color-mode:mini-pvp");

        assertThat(router.subscribeStreamsFor(TransportEvents.DiscordLinkConfirmEvent.class, "mini-pvp"))
                .containsExactly("xcore:cmd:discord-link-confirm:mini-pvp");

        assertThat(router.subscribeStreamsFor(TransportEvents.DiscordLinkStatusChangedEvent.class, "mini-pvp"))
                .containsExactly("xcore:evt:discord:link-status");

        assertThat(router.subscribeStreamsFor(TransportEvents.DiscordAdminAccessChanged.class, "mini-pvp"))
                .containsExactly("xcore:cmd:discord-admin-access:mini-pvp");

        assertThat(router.subscribeStreamsFor(TransportEvents.ModerationBanCreatedEvent.class, "mini-pvp"))
                .containsExactly("xcore:evt:moderation:ban");

        assertThat(router.subscribeStreamsFor(TransportEvents.ModerationMuteCreatedEvent.class, "mini-pvp"))
                .containsExactly("xcore:evt:moderation:mute");

        assertThat(router.subscribeStreamsFor(TransportEvents.ModerationVoteKickCreatedEvent.class, "mini-pvp"))
                .containsExactly("xcore:evt:moderation:votekick");

        assertThat(router.subscribeStreamsFor(TransportEvents.ModerationAuditAppendedProtocolEvent.class, "mini-pvp"))
                .containsExactly("xcore:evt:moderation:audit");

        assertThat(router.subscribeStreamsFor(TransportEvents.ModerationKickBannedCommandEvent.class, "mini-pvp"))
                .containsExactly("xcore:cmd:kick-banned:mini-pvp");

        assertThat(router.subscribeStreamsFor(TransportEvents.ModerationPardonCommandEvent.class, "mini-pvp"))
                .containsExactly("xcore:cmd:pardon-player:mini-pvp");
    }

    @Test
    @DisplayName("type classification and rpc response mapping are correct")
    void classificationAndResponseMapping() {
        assertThat(router.isReadOnlyType(TransportEvents.DiscordLinkStatusChangedEvent.class)).isTrue();
        assertThat(router.isReadOnlyType(TransportEvents.ModerationBanCreatedEvent.class)).isTrue();
        assertThat(router.isReadOnlyType(TransportEvents.ModerationMuteCreatedEvent.class)).isTrue();
        assertThat(router.isReadOnlyType(TransportEvents.ModerationVoteKickCreatedEvent.class)).isTrue();
        assertThat(router.isReadOnlyType(TransportEvents.ModerationAuditAppendedProtocolEvent.class)).isTrue();
        assertThat(router.isReadOnlyType(TransportEvents.DiscordAdminAccessChanged.class)).isFalse();
        assertThat(router.isMutatingType(TransportEvents.ModerationKickBannedCommandEvent.class)).isTrue();
        assertThat(router.isMutatingType(TransportEvents.ModerationPardonCommandEvent.class)).isTrue();

        assertThat(router.isMutatingType(TransportEvents.PlayerPasswordReset.class)).isTrue();
        assertThat(router.isMutatingType(TransportEvents.PlayerBadgeSymbolColorModeChanged.class)).isTrue();
        assertThat(router.isMutatingType(TransportEvents.DiscordLinkConfirmEvent.class)).isTrue();
        assertThat(router.isMutatingType(TransportEvents.DiscordAdminAccessChanged.class)).isTrue();
        assertThat(router.isMutatingType(TransportEvents.MessageEvent.class)).isFalse();

        assertThat(router.isRpcRequestType(TransportEvents.MapsListRequest.class)).isTrue();
        assertThat(router.isRpcRequestType(TransportEvents.MessageEvent.class)).isFalse();

        assertThat(router.responseTypeForRequest(TransportEvents.MapsListRequest.class))
                .isEqualTo(TransportEvents.MapsListResponse.class);
        assertThat(router.responseTypeForRequest(TransportEvents.MapRemoveRequest.class))
                .isEqualTo(TransportEvents.MapRemoveResponse.class);

        assertThat(router.rpcTypeForRequestClass(TransportEvents.MapsListRequest.class))
                .isEqualTo("maps.list");
        assertThat(router.rpcTypeForRequestClass(TransportEvents.MapRemoveRequest.class))
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
