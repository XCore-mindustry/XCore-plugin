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
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordLinkConfirmCommandV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordLinkStatusChangedV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordUnlinkCommandV1;
import org.xcore.protocol.generated.messages.discord.DiscordLinkStatusChangedV1Action;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsListRequestV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsListResponseV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsLoadCommandV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsRemoveRequestV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsRemoveResponseV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationAuditAppendedV1;
import org.xcore.protocol.generated.messages.moderation.ModerationAuditAppendedV1EntryType;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationBanCreatedV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationKickBannedCommandV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationMuteCreatedV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationPardonCommandV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationVoteKickCreatedV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages;
import org.xcore.protocol.generated.shared.ActorRefV1;
import org.xcore.protocol.generated.shared.ActorRefV1ActorType;
import org.xcore.protocol.generated.shared.DiscordIdentityRefV1;
import org.xcore.protocol.generated.shared.MapFileSourceV1;
import org.xcore.protocol.generated.shared.ModerationTargetRefV1;
import org.xcore.protocol.generated.shared.PlayerRefV1;
import org.xcore.plugin.model.BanData;
import org.xcore.plugin.model.MuteData;
import org.xcore.plugin.model.Punishment;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisStreamRouterTest {

    private final RedisStreamRouter router = new RedisStreamRouter();

    @Test
    @DisplayName("route rejects unsupported payload types without synthesizing transport metadata")
    void routeRejectsUnsupportedPayloadTypes() {
        assertThatThrownBy(() -> router.route(new Object(), "mini-pvp"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining(Object.class.getName());
    }

    @Test
    @DisplayName("route maps read-only events to expected stream and event type")
    void routeReadOnlyEvents() {
        BanData banData = punishment(new BanData(), "u", "n");
        MuteData muteData = punishment(new MuteData(), "u", "n");

        var messageRoute = router.route(new ChatMessageV1("a", "b", "mini-pvp"), "mini-pvp");
        var privateRoute = router.route(new ChatPrivateV1("uuid-from", 7, "Sender", "uuid-to", 42, "hello", "survival"), "mini-pvp");
        var serverActionRoute = router.route(new ServerActionV1("Server loaded", "mini-pvp"), "mini-pvp");
        var joinRoute = router.route(new PlayerJoinLeaveV1("p", "mini-pvp", true), "mini-pvp");
        var heartbeatRoute = router.route(new ServerHeartbeatV1("mini-pvp", 1L, 5, 30, "1.0.0", "127.0.0.1", 6567), "mini-pvp");
        var banRoute = router.route(
                org.xcore.plugin.service.network.ModerationProtocolMapper.toBanCreated(
                        banData,
                        "mini-pvp",
                        Instant.parse("2026-04-26T00:00:00Z")
                ),
                "mini-pvp"
        );
        var muteRoute = router.route(
                org.xcore.plugin.service.network.ModerationProtocolMapper.toMuteCreated(
                        muteData,
                        "mini-pvp",
                        Instant.parse("2026-04-26T00:00:01Z")
                ),
                "mini-pvp"
        );
        var voteKickRoute = router.route(
                org.xcore.plugin.service.network.ModerationProtocolMapper.toVoteKickCreated(
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
                new ModerationAuditAppendedV1(
                        ModerationAuditAppendedV1EntryType.BAN,
                        new ModerationTargetRefV1("uuid-target", 42, "target", null),
                        new ActorRefV1("Admin", "admin-1", ActorRefV1ActorType.DISCORD),
                        "reason",
                        "mini-pvp",
                        Instant.parse("2026-04-26T00:00:03Z").toString(),
                        java.util.Map.of("durationMs", 60000L)
                ),
                "mini-pvp"
        );
        var discordLinkCodeRoute = router.route(
                new DiscordLinkCodeCreatedV1(
                        "ABC123",
                        new PlayerRefV1("uuid-7", 7, "Target", null),
                        "mini-pvp",
                        Instant.parse("2026-04-26T00:00:04Z").toString(),
                        Instant.parse("2026-04-26T00:10:04Z").toString()
                ),
                "mini-pvp"
        );

        assertThat(messageRoute.streamKey()).isEqualTo("xcore:evt:chat:message");
        assertThat(messageRoute.messageType()).isEqualTo("chat.message");

        assertThat(privateRoute.streamKey()).isEqualTo("xcore:evt:chat:private");
        assertThat(privateRoute.messageType()).isEqualTo("chat.private");

        assertThat(serverActionRoute.streamKey()).isEqualTo("xcore:evt:server:action");
        assertThat(serverActionRoute.messageType()).isEqualTo("server.action");

        assertThat(joinRoute.streamKey()).isEqualTo("xcore:evt:player:joinleave");
        assertThat(joinRoute.messageType()).isEqualTo("player.join-leave");

        assertThat(heartbeatRoute.streamKey()).isEqualTo("xcore:evt:server:heartbeat");
        assertThat(heartbeatRoute.messageType()).isEqualTo("server.heartbeat");

        assertThat(banRoute.streamKey()).isEqualTo("xcore:evt:moderation:ban");
        assertThat(banRoute.messageType()).isEqualTo("moderation.ban.created");

        assertThat(muteRoute.streamKey()).isEqualTo("xcore:evt:moderation:mute");
        assertThat(muteRoute.messageType()).isEqualTo("moderation.mute.created");

        assertThat(voteKickRoute.streamKey()).isEqualTo("xcore:evt:moderation:votekick");
        assertThat(voteKickRoute.messageType()).isEqualTo("moderation.vote-kick.created");

        assertThat(auditRoute.streamKey()).isEqualTo("xcore:evt:moderation:audit");
        assertThat(auditRoute.messageType()).isEqualTo("moderation.audit.appended");

        assertThat(discordLinkCodeRoute.streamKey()).isEqualTo("xcore:evt:discord:link-code");
        assertThat(discordLinkCodeRoute.messageType()).isEqualTo("discord.link-code-created");
    }

    @Test
    @DisplayName("route maps server-targeted events using event payload server")
    void routeServerTargetedEvents() {
        var discordRoute = router.route(new ChatDiscordIngressCommandV1("bot", "hello", "mini-hexed"), "mini-pvp");
        var mapsRoute = router.route(new MapsLoadCommandV1("event", List.of(new MapFileSourceV1("https://example/maps/a.msav", "a.msav"))), "mini-pvp");
        var customNicknameRoute = router.route(new PlayerCustomNicknameChangedCommandV1("uuid-7", "Commander", "survival"), "mini-pvp");
        var activeBadgeRoute = router.route(new PlayerActiveBadgeChangedCommandV1("uuid-7", "translator", "mini-hexed"), "mini-pvp");
        var badgeRoute = router.route(new PlayerBadgeInventoryChangedCommandV1("uuid-7", "translator", List.of("translator"), "mini-pvp"), "mini-pvp");
        var badgeColorModeRoute = router.route(new PlayerBadgeSymbolColorModeChangedCommandV1("uuid-7", "player-color", "hexed"), "mini-pvp");
        var passwordRoute = router.route(new PlayerPasswordResetCommandV1("uuid-7", "mini-pvp"), "mini-pvp");
        var discordLinkConfirmRoute = router.route(
                new DiscordLinkConfirmCommandV1(
                        "ABC123",
                        new PlayerRefV1("uuid-7", 7, "Nick", null),
                        new DiscordIdentityRefV1("123", "discord-user"),
                        "mini-hexed",
                        Instant.parse("2026-04-26T00:00:10Z").toString()
                ),
                "mini-pvp"
        );
        var discordLinkStatusRoute = router.route(
                new DiscordLinkStatusChangedV1(
                        new PlayerRefV1("uuid-7", 7, "Nick", null),
                        new DiscordIdentityRefV1("123", "discord-user"),
                        DiscordLinkStatusChangedV1Action.LINKED,
                        "mini-pvp",
                        Instant.parse("2026-04-26T00:00:10Z").toString()
                ),
                "mini-pvp"
        );
        var discordAdminAccessRoute = router.route(
                new DiscordAdminAccessChangedCommandV1(
                        new PlayerRefV1("uuid-7", 7, "Nick", null),
                        new DiscordIdentityRefV1("123", "discord-user"),
                        true,
                        new ActorRefV1("DISCORD_ROLE", null, ActorRefV1ActorType.SYSTEM),
                        new ActorRefV1("tester", null, ActorRefV1ActorType.SYSTEM),
                        "sync",
                        "mini-pvp",
                        Instant.parse("2026-04-26T00:00:11Z").toString()
                ),
                "mini-pvp"
        );
        var discordUnlinkRoute = router.route(
                new DiscordUnlinkCommandV1(
                        new PlayerRefV1("uuid-7", 7, "Nick", null),
                        new DiscordIdentityRefV1("123", "discord-user"),
                        new ActorRefV1("tester", null, ActorRefV1ActorType.SYSTEM),
                        "mini-hexed",
                        Instant.parse("2026-04-26T00:00:13Z").toString()
                ),
                "mini-pvp"
        );

        assertThat(discordRoute.streamKey()).isEqualTo("xcore:cmd:discord-message:mini-hexed");
        assertThat(discordRoute.messageType()).isEqualTo("chat.discord-ingress.command");
        assertThat(mapsRoute.streamKey()).isEqualTo("xcore:cmd:maps-load:event");
        assertThat(mapsRoute.messageType()).isEqualTo("maps.load.command");
        assertThat(customNicknameRoute.streamKey()).isEqualTo("xcore:cmd:player-custom-nickname:survival");
        assertThat(customNicknameRoute.messageType()).isEqualTo("player.custom-nickname.changed.command");
        assertThat(activeBadgeRoute.streamKey()).isEqualTo("xcore:cmd:player-active-badge:mini-hexed");
        assertThat(activeBadgeRoute.messageType()).isEqualTo("player.active-badge.changed.command");
        assertThat(badgeRoute.streamKey()).isEqualTo("xcore:cmd:player-badge-inventory:mini-pvp");
        assertThat(badgeRoute.messageType()).isEqualTo("player.badge-inventory.changed.command");
        assertThat(badgeColorModeRoute.streamKey()).isEqualTo("xcore:cmd:player-badge-symbol-color-mode:hexed");
        assertThat(badgeColorModeRoute.messageType()).isEqualTo("player.badge-symbol-color-mode.changed.command");
        assertThat(passwordRoute.streamKey()).isEqualTo("xcore:cmd:player-password-reset:mini-pvp");
        assertThat(passwordRoute.messageType()).isEqualTo("player.password-reset.command");
        assertThat(discordLinkConfirmRoute.streamKey()).isEqualTo("xcore:cmd:discord-link-confirm:mini-hexed");
        assertThat(discordLinkConfirmRoute.messageType()).isEqualTo("discord.link.confirm.command");
        assertThat(discordLinkStatusRoute.streamKey()).isEqualTo("xcore:evt:discord:link-status");
        assertThat(discordLinkStatusRoute.messageType()).isEqualTo("discord.link.status-changed");
        assertThat(discordAdminAccessRoute.streamKey()).isEqualTo("xcore:cmd:discord-admin-access:mini-pvp");
        assertThat(discordAdminAccessRoute.messageType()).isEqualTo("discord.admin-access.changed.command");
        assertThat(discordUnlinkRoute.streamKey()).isEqualTo("xcore:cmd:discord-unlink:mini-hexed");
        assertThat(discordUnlinkRoute.messageType()).isEqualTo("discord.unlink.command");

        var discordAdminAccessOtherServerRoute = router.route(
                new DiscordAdminAccessChangedCommandV1(
                        new PlayerRefV1("uuid-8", 8, "Other", null),
                        new DiscordIdentityRefV1("456", "other-user"),
                        false,
                        new ActorRefV1("NONE", null, ActorRefV1ActorType.SYSTEM),
                        new ActorRefV1("tester", null, ActorRefV1ActorType.SYSTEM),
                        "sync",
                        "survival",
                        Instant.parse("2026-04-26T00:00:12Z").toString()
                ),
                "mini-pvp"
        );
        assertThat(discordAdminAccessOtherServerRoute.streamKey()).isEqualTo("xcore:cmd:discord-admin-access:survival");
    }

    @Test
    @DisplayName("subscribe streams include read-only and rpc request streams")
    void subscribeStreamsForTypes() {
        assertThat(router.subscribeStreamsFor(ChatMessageV1.class, "mini-pvp"))
                .containsExactly("xcore:evt:chat:message");

        assertThat(router.subscribeStreamsFor(ChatPrivateV1.class, "mini-pvp"))
                .containsExactly("xcore:evt:chat:private");

        assertThat(router.subscribeStreamsFor(ChatGlobalV1.class, "mini-pvp"))
                .containsExactly("xcore:evt:chat:global");

        assertThat(router.subscribeStreamsFor(ChatDiscordIngressCommandV1.class, "mini-pvp"))
                .containsExactly("xcore:cmd:discord-message:mini-pvp");

        assertThat(router.subscribeStreamsFor(PlayerJoinLeaveV1.class, "mini-pvp"))
                .containsExactly("xcore:evt:player:joinleave");

        assertThat(router.subscribeStreamsFor(ServerActionV1.class, "mini-pvp"))
                .containsExactly("xcore:evt:server:action");

        assertThat(router.subscribeStreamsFor(ServerHeartbeatV1.class, "mini-pvp"))
                .containsExactly("xcore:evt:server:heartbeat");

        assertThat(router.subscribeStreamsFor(MapsListRequestV1.class, "mini-pvp"))
                .containsExactly("xcore:rpc:req:mini-pvp");

        assertThat(router.subscribeStreamsFor(MapsRemoveRequestV1.class, "mini-pvp"))
                .containsExactly("xcore:rpc:req:mini-pvp");

        assertThat(router.subscribeStreamsFor(MapsLoadCommandV1.class, "mini-pvp"))
                .containsExactly("xcore:cmd:maps-load:mini-pvp");

        assertThat(router.subscribeStreamsFor(PlayerCustomNicknameChangedCommandV1.class, "mini-pvp"))
                .containsExactly("xcore:cmd:player-custom-nickname:mini-pvp");

        assertThat(router.subscribeStreamsFor(PlayerActiveBadgeChangedCommandV1.class, "mini-pvp"))
                .containsExactly("xcore:cmd:player-active-badge:mini-pvp");

        assertThat(router.subscribeStreamsFor(PlayerBadgeSymbolColorModeChangedCommandV1.class, "mini-pvp"))
                .containsExactly("xcore:cmd:player-badge-symbol-color-mode:mini-pvp");

        assertThat(router.subscribeStreamsFor(PlayerPasswordResetCommandV1.class, "mini-pvp"))
                .containsExactly("xcore:cmd:player-password-reset:mini-pvp");

        assertThat(router.subscribeStreamsFor(DiscordLinkConfirmCommandV1.class, "mini-pvp"))
                .containsExactly("xcore:cmd:discord-link-confirm:mini-pvp");

        assertThat(router.subscribeStreamsFor(DiscordLinkCodeCreatedV1.class, "mini-pvp"))
                .containsExactly("xcore:evt:discord:link-code");

        assertThat(router.subscribeStreamsFor(DiscordLinkStatusChangedV1.class, "mini-pvp"))
                .containsExactly("xcore:evt:discord:link-status");

        assertThat(router.subscribeStreamsFor(DiscordAdminAccessChangedCommandV1.class, "mini-pvp"))
                .containsExactly("xcore:cmd:discord-admin-access:mini-pvp");

        assertThat(router.subscribeStreamsFor(DiscordUnlinkCommandV1.class, "mini-pvp"))
                .containsExactly("xcore:cmd:discord-unlink:mini-pvp");

        assertThat(router.subscribeStreamsFor(ModerationBanCreatedV1.class, "mini-pvp"))
                .containsExactly("xcore:evt:moderation:ban");

        assertThat(router.subscribeStreamsFor(ModerationMuteCreatedV1.class, "mini-pvp"))
                .containsExactly("xcore:evt:moderation:mute");

        assertThat(router.subscribeStreamsFor(ModerationVoteKickCreatedV1.class, "mini-pvp"))
                .containsExactly("xcore:evt:moderation:votekick");

        assertThat(router.subscribeStreamsFor(ModerationAuditAppendedV1.class, "mini-pvp"))
                .containsExactly("xcore:evt:moderation:audit");

        assertThat(router.subscribeStreamsFor(ModerationKickBannedCommandV1.class, "mini-pvp"))
                .containsExactly("xcore:cmd:kick-banned:mini-pvp");

        assertThat(router.subscribeStreamsFor(ModerationPardonCommandV1.class, "mini-pvp"))
                .containsExactly("xcore:cmd:pardon-player:mini-pvp");
    }

    @Test
    @DisplayName("type classification and rpc response mapping are correct")
    void classificationAndResponseMapping() {
        assertThat(router.isReadOnlyType(ServerHeartbeatV1.class)).isTrue();
        assertThat(router.isReadOnlyType(ChatDiscordIngressCommandV1.class)).isFalse();
        assertThat(router.isReadOnlyType(ChatPrivateV1.class)).isTrue();
        assertThat(router.isReadOnlyType(DiscordLinkCodeCreatedV1.class)).isTrue();
        assertThat(router.isReadOnlyType(DiscordLinkStatusChangedV1.class)).isTrue();
        assertThat(router.isReadOnlyType(ModerationBanCreatedV1.class)).isTrue();
        assertThat(router.isReadOnlyType(ModerationMuteCreatedV1.class)).isTrue();
        assertThat(router.isReadOnlyType(ModerationVoteKickCreatedV1.class)).isTrue();
        assertThat(router.isReadOnlyType(ModerationAuditAppendedV1.class)).isTrue();
        assertThat(router.isReadOnlyType(DiscordAdminAccessChangedCommandV1.class)).isFalse();
        assertThat(router.isMutatingType(ModerationKickBannedCommandV1.class)).isTrue();
        assertThat(router.isMutatingType(ModerationPardonCommandV1.class)).isTrue();

        assertThat(router.isMutatingType(PlayerPasswordResetCommandV1.class)).isTrue();
        assertThat(router.isMutatingType(PlayerCustomNicknameChangedCommandV1.class)).isTrue();
        assertThat(router.isMutatingType(PlayerActiveBadgeChangedCommandV1.class)).isTrue();
        assertThat(router.isMutatingType(PlayerBadgeSymbolColorModeChangedCommandV1.class)).isTrue();
        assertThat(router.isMutatingType(MapsLoadCommandV1.class)).isTrue();
        assertThat(router.isMutatingType(ChatDiscordIngressCommandV1.class)).isTrue();
        assertThat(router.isMutatingType(DiscordLinkConfirmCommandV1.class)).isTrue();
        assertThat(router.isMutatingType(DiscordUnlinkCommandV1.class)).isTrue();
        assertThat(router.isMutatingType(DiscordAdminAccessChangedCommandV1.class)).isTrue();
        assertThat(router.isReadOnlyType(ChatGlobalV1.class)).isTrue();
        assertThat(router.isMutatingType(ChatMessageV1.class)).isFalse();

        assertThat(router.isRpcRequestType(MapsListRequestV1.class)).isTrue();
        assertThat(router.isRpcRequestType(ChatMessageV1.class)).isFalse();

        assertThat(router.shouldClaimIdempotency(ChatDiscordIngressCommandV1.class)).isTrue();
        assertThat(router.shouldClaimIdempotency(ModerationBanCreatedV1.class)).isTrue();
        assertThat(router.shouldClaimIdempotency(ChatMessageV1.class)).isFalse();
        assertThat(router.shouldClaimIdempotency(MapsListRequestV1.class)).isFalse();

        assertThat(router.responseTypeForRequest(MapsListRequestV1.class))
                .isEqualTo(MapsListResponseV1.class);
        assertThat(router.responseTypeForRequest(MapsRemoveRequestV1.class))
                .isEqualTo(MapsRemoveResponseV1.class);

        assertThat(router.rpcTypeForRequestClass(MapsListRequestV1.class))
                .isEqualTo("maps.list.request");
        assertThat(router.rpcTypeForRequestClass(MapsRemoveRequestV1.class))
                .isEqualTo("maps.remove.request");
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
