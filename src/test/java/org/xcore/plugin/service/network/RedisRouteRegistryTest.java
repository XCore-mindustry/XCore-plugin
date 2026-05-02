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
import org.xcore.protocol.generated.messages.chat.ChatMessages.ServerCommandExecuteCommandV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.ServerHeartbeatV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordAdminAccessChangedCommandV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordLinkConfirmCommandV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordLinkStatusChangedV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordUnlinkCommandV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsListRequestV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsListResponseV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsLoadCommandV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsRemoveRequestV1;
import org.xcore.protocol.generated.messages.maps.MapsMessages.MapsRemoveResponseV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationAuditAppendedV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationBanCreatedV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationKickBannedCommandV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationMuteCreatedV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationPardonCommandV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationVoteKickCreatedV1;
import org.xcore.protocol.generated.shared.DiscordIdentityRefV1;
import org.xcore.protocol.generated.shared.MapFileSourceV1;
import org.xcore.protocol.generated.shared.PlayerRefV1;

import static org.assertj.core.api.Assertions.assertThat;

class RedisRouteRegistryTest {

    private final RedisRouteRegistry registry = new RedisRouteRegistry();

    @Test
    @DisplayName("unknown payload types have no registry descriptor")
    void unknownPayloadTypesHaveNoRegistryDescriptor() {
        assertThat(registry.routeDescriptorFor(new Object())).isNull();
        assertThat(registry.routeDescriptorFor(Object.class)).isNull();
    }

    @Test
    @DisplayName("payload server resolver uses typed server contract")
    void payloadServerResolverUsesTypedContract() {
        RedisRouteDescriptor descriptor = registry.routeDescriptorFor(DiscordLinkConfirmCommandV1.class);

        String stream = registry.resolveStreamKey(
                descriptor,
                new DiscordLinkConfirmCommandV1(
                        "code",
                        new PlayerRefV1("uuid", 1, "Player", null),
                        new DiscordIdentityRefV1("discord", "user"),
                        "survival",
                        "2026-04-28T00:00:00Z"
                ),
                "mini-pvp"
        );

        assertThat(stream).isEqualTo("xcore:cmd:discord-link-confirm:survival");
    }

    @Test
    @DisplayName("discord ingress command uses typed payload server contract")
    void discordIngressCommandUsesTypedPayloadServerContract() {
        RedisRouteDescriptor descriptor = registry.routeDescriptorFor(ChatDiscordIngressCommandV1.class);

        String stream = registry.resolveStreamKey(
                descriptor,
                new ChatDiscordIngressCommandV1("bot", "hello", "survival"),
                "mini-pvp"
        );

        assertThat(stream).isEqualTo("xcore:cmd:discord-message:survival");
    }

    @Test
    @DisplayName("unlink command uses typed payload server contract")
    void unlinkCommandUsesTypedPayloadServerContract() {
        RedisRouteDescriptor descriptor = registry.routeDescriptorFor(DiscordUnlinkCommandV1.class);

        String stream = registry.resolveStreamKey(
                descriptor,
                new DiscordUnlinkCommandV1(
                        new PlayerRefV1("uuid", 1, "Player", null),
                        new DiscordIdentityRefV1("discord", "user"),
                        "moderator",
                        "survival",
                        "2026-04-28T00:00:00Z"
                ),
                "mini-pvp"
        );

        assertThat(stream).isEqualTo("xcore:cmd:discord-unlink:survival");
    }

    @Test
    @DisplayName("player password reset command uses typed payload server contract")
    void playerPasswordResetCommandUsesTypedPayloadServerContract() {
        RedisRouteDescriptor descriptor = registry.routeDescriptorFor(PlayerPasswordResetCommandV1.class);

        String stream = registry.resolveStreamKey(
                descriptor,
                new PlayerPasswordResetCommandV1("uuid-1", "survival"),
                "mini-pvp"
        );

        assertThat(stream).isEqualTo("xcore:cmd:player-password-reset:survival");
    }

    @Test
    @DisplayName("rpc route descriptor carries response type metadata")
    void rpcRouteDescriptorCarriesResponseType() {
        RedisRouteDescriptor descriptor = registry.routeDescriptorFor(MapsListRequestV1.class);
        RedisRouteDescriptor removeDescriptor = registry.routeDescriptorFor(MapsRemoveRequestV1.class);

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.isRpcRequest()).isTrue();
        assertThat(descriptor.responseType()).isEqualTo(MapsListResponseV1.class);
        assertThat(registry.rpcTypeForRequestClass(MapsListRequestV1.class)).isEqualTo("maps.list.request");

        assertThat(removeDescriptor).isNotNull();
        assertThat(removeDescriptor.isRpcRequest()).isTrue();
        assertThat(removeDescriptor.responseType()).isEqualTo(MapsRemoveResponseV1.class);
        assertThat(registry.rpcTypeForRequestClass(MapsRemoveRequestV1.class)).isEqualTo("maps.remove.request");
    }

    @Test
    @DisplayName("maps load command uses typed payload server contract")
    void mapsLoadCommandUsesTypedPayloadServerContract() {
        RedisRouteDescriptor descriptor = registry.routeDescriptorFor(MapsLoadCommandV1.class);

        String stream = registry.resolveStreamKey(
                descriptor,
                new MapsLoadCommandV1("survival", java.util.List.of(new MapFileSourceV1("https://example/maps/a.msav", "a.msav"))),
                "mini-pvp"
        );

        assertThat(stream).isEqualTo("xcore:cmd:maps-load:survival");
    }

    @Test
    @DisplayName("player session commands use typed payload server contract")
    void playerSessionCommandsUseTypedPayloadServerContract() {
        String customNicknameStream = registry.resolveStreamKey(
                registry.routeDescriptorFor(PlayerCustomNicknameChangedCommandV1.class),
                new PlayerCustomNicknameChangedCommandV1("uuid", "Commander", "survival"),
                "mini-pvp"
        );
        String activeBadgeStream = registry.resolveStreamKey(
                registry.routeDescriptorFor(PlayerActiveBadgeChangedCommandV1.class),
                new PlayerActiveBadgeChangedCommandV1("uuid", "translator", "hexed"),
                "mini-pvp"
        );
        String badgeColorModeStream = registry.resolveStreamKey(
                registry.routeDescriptorFor(PlayerBadgeSymbolColorModeChangedCommandV1.class),
                new PlayerBadgeSymbolColorModeChangedCommandV1("uuid", "player-color", "mini-hexed"),
                "mini-pvp"
        );

        assertThat(customNicknameStream).isEqualTo("xcore:cmd:player-custom-nickname:survival");
        assertThat(activeBadgeStream).isEqualTo("xcore:cmd:player-active-badge:hexed");
        assertThat(badgeColorModeStream).isEqualTo("xcore:cmd:player-badge-symbol-color-mode:mini-hexed");
    }

    @Test
    @DisplayName("read-only and mutating classification comes from registry descriptors")
    void classificationComesFromRegistry() {
        assertThat(registry.isReadOnlyType(ChatMessageV1.class)).isTrue();
        assertThat(registry.isReadOnlyType(ChatGlobalV1.class)).isTrue();
        assertThat(registry.isReadOnlyType(ChatDiscordIngressCommandV1.class)).isTrue();
        assertThat(registry.isReadOnlyType(ChatPrivateV1.class)).isTrue();
        assertThat(registry.isReadOnlyType(PlayerJoinLeaveV1.class)).isTrue();
        assertThat(registry.isReadOnlyType(ServerActionV1.class)).isTrue();
        assertThat(registry.isReadOnlyType(ServerHeartbeatV1.class)).isTrue();
        assertThat(registry.isReadOnlyType(DiscordLinkStatusChangedV1.class)).isTrue();
        assertThat(registry.isMutatingType(DiscordUnlinkCommandV1.class)).isTrue();
        assertThat(registry.isMutatingType(DiscordAdminAccessChangedCommandV1.class)).isTrue();
        assertThat(registry.isMutatingType(MapsLoadCommandV1.class)).isTrue();
        assertThat(registry.isMutatingType(PlayerCustomNicknameChangedCommandV1.class)).isTrue();
        assertThat(registry.isMutatingType(PlayerActiveBadgeChangedCommandV1.class)).isTrue();
        assertThat(registry.isMutatingType(PlayerBadgeSymbolColorModeChangedCommandV1.class)).isTrue();
        assertThat(registry.isReadOnlyType(ModerationBanCreatedV1.class)).isTrue();
        assertThat(registry.isReadOnlyType(ModerationMuteCreatedV1.class)).isTrue();
        assertThat(registry.isReadOnlyType(ModerationVoteKickCreatedV1.class)).isTrue();
        assertThat(registry.isReadOnlyType(ModerationAuditAppendedV1.class)).isTrue();
        assertThat(registry.isMutatingType(ModerationKickBannedCommandV1.class)).isTrue();
        assertThat(registry.isMutatingType(ModerationPardonCommandV1.class)).isTrue();
        assertThat(registry.isMutatingType(ServerCommandExecuteCommandV1.class)).isTrue();
        assertThat(registry.isMutatingType(ChatGlobalV1.class)).isFalse();
    }
}
