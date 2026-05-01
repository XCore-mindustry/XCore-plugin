package org.xcore.plugin.event.transport;

import arc.func.Cons;
import com.ospx.flubundle.Bundle;
import mindustry.Vars;
import mindustry.core.NetServer;
import mindustry.gen.Player;
import mindustry.net.Administration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.service.DiscordAdminAccessService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.service.network.RedisNetworkBackend;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.MenuService;
import org.xcore.protocol.generated.messages.chat.ChatMessages.PlayerActiveBadgeChangedCommandV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.PlayerBadgeInventoryChangedCommandV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.PlayerBadgeSymbolColorModeChangedCommandV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.PlayerCustomNicknameChangedCommandV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.PlayerPasswordResetCommandV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordAdminAccessChangedCommandV1;
import org.xcore.protocol.generated.shared.DiscordIdentityRefV1;
import org.xcore.protocol.generated.shared.PlayerRefV1;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationTransportHandlerTest {

    private NetServer previousNetServer;

    @BeforeEach
    void setUp() {
        previousNetServer = Vars.netServer;
        NetServer netServer = mock(NetServer.class);
        netServer.admins = mock(Administration.class);
        Vars.netServer = netServer;
    }

    @AfterEach
    void tearDown() {
        Vars.netServer = previousNetServer;
    }

    @Test
    @DisplayName("discord admin access command applies persisted admin flags")
    void discordAdminAccessCommand_appliesPersistedAdminFlags() {
        NetworkService network = mock(NetworkService.class);
        SessionService sessionService = mock(SessionService.class);
        PlayerDisplayService playerDisplayService = mock(PlayerDisplayService.class);
        DiscordAdminAccessService discordAdminAccessService = mock(DiscordAdminAccessService.class);

        Config config = new Config();
        config.server = "mini-pvp";

        ModerationTransportHandler handler = new ModerationTransportHandler(network, sessionService, config, playerDisplayService, discordAdminAccessService);

        Map<Class<?>, Cons<?>> listeners = new HashMap<>();
        captureListeners(network, listeners);

        when(discordAdminAccessService.applyDiscordAdminAccess("uuid-1", "123", "discord-user")).thenReturn(true);

        handler.registerListeners();

        listener(listeners, DiscordAdminAccessChangedCommandV1.class)
                .get(new DiscordAdminAccessChangedCommandV1(
                        new PlayerRefV1("uuid-1", 7, "Player", null),
                        new DiscordIdentityRefV1("123", "discord-user"),
                        true,
                        DiscordAdminAccessService.SOURCE_DISCORD_ROLE,
                        "tester",
                        "sync",
                        "mini-pvp",
                        "2026-04-28T00:00:10Z"
                ));

        verify(discordAdminAccessService).applyDiscordAdminAccess("uuid-1", "123", "discord-user");
    }

    @Test
    @DisplayName("discord admin revoke command clears persisted admin flags")
    void discordAdminRevokeCommand_clearsPersistedAdminFlags() {
        NetworkService network = mock(NetworkService.class);
        SessionService sessionService = mock(SessionService.class);
        PlayerDisplayService playerDisplayService = mock(PlayerDisplayService.class);
        DiscordAdminAccessService discordAdminAccessService = mock(DiscordAdminAccessService.class);

        Config config = new Config();
        config.server = "mini-pvp";

        ModerationTransportHandler handler = new ModerationTransportHandler(network, sessionService, config, playerDisplayService, discordAdminAccessService);

        Map<Class<?>, Cons<?>> listeners = new HashMap<>();
        captureListeners(network, listeners);

        when(discordAdminAccessService.revokeDiscordAdminAccess("uuid-1")).thenReturn(true);

        handler.registerListeners();

        listener(listeners, DiscordAdminAccessChangedCommandV1.class)
                .get(new DiscordAdminAccessChangedCommandV1(
                        new PlayerRefV1("uuid-1", 7, "Player", null),
                        new DiscordIdentityRefV1("123", "discord-user"),
                        false,
                        DiscordAdminAccessService.SOURCE_DISCORD_ROLE,
                        "tester",
                        "sync",
                        "mini-pvp",
                        "2026-04-28T00:00:11Z"
                ));

        verify(discordAdminAccessService).revokeDiscordAdminAccess("uuid-1");
    }

    @Test
    @DisplayName("player session commands update session state and refresh display when needed")
    void playerSessionCommands_updateSessionStateAndRefreshDisplay() {
        NetworkService network = mock(NetworkService.class);
        SessionService sessionService = mock(SessionService.class);
        PlayerDisplayService playerDisplayService = mock(PlayerDisplayService.class);
        DiscordAdminAccessService discordAdminAccessService = mock(DiscordAdminAccessService.class);

        Config config = new Config();
        config.server = "mini-pvp";

        ModerationTransportHandler handler = new ModerationTransportHandler(network, sessionService, config, playerDisplayService, discordAdminAccessService);

        Map<Class<?>, Cons<?>> listeners = new HashMap<>();
        captureListeners(network, listeners);

        PlayerData playerData = new PlayerData();
        playerData.uuid = "uuid-1";
        playerData.customNickname = "Old";
        playerData.activeBadge = "";
        playerData.badgeSymbolColorMode = "default";
        Session session = new Session(
                mock(GlobalConfig.class),
                mock(Bundle.class),
                mock(MenuService.class),
                mock(PlayerDataRepository.class),
                mock(Player.class),
                playerData
        );
        when(sessionService.get("uuid-1")).thenReturn(session);

        handler.registerListeners();

        listener(listeners, PlayerCustomNicknameChangedCommandV1.class)
                .get(new PlayerCustomNicknameChangedCommandV1("uuid-1", "Commander", "survival"));
        listener(listeners, PlayerActiveBadgeChangedCommandV1.class)
                .get(new PlayerActiveBadgeChangedCommandV1("uuid-1", "translator", "survival"));
        listener(listeners, PlayerBadgeSymbolColorModeChangedCommandV1.class)
                .get(new PlayerBadgeSymbolColorModeChangedCommandV1("uuid-1", "player-color", "survival"));

        assertThat(session.data.customNickname).isEqualTo("Commander");
        assertThat(session.data.activeBadge).isEqualTo("translator");
        assertThat(session.data.badgeSymbolColorMode).isEqualTo("player-color");
        verify(playerDisplayService, times(2)).refresh(session);
    }

    @Test
    @DisplayName("badge inventory command updates unlocked badges and refreshes display")
    void badgeInventoryCommand_updatesUnlockedBadgesAndRefreshesDisplay() {
        NetworkService network = mock(NetworkService.class);
        SessionService sessionService = mock(SessionService.class);
        PlayerDisplayService playerDisplayService = mock(PlayerDisplayService.class);
        DiscordAdminAccessService discordAdminAccessService = mock(DiscordAdminAccessService.class);

        Config config = new Config();
        config.server = "mini-pvp";

        ModerationTransportHandler handler = new ModerationTransportHandler(network, sessionService, config, playerDisplayService, discordAdminAccessService);

        Map<Class<?>, Cons<?>> listeners = new HashMap<>();
        captureListeners(network, listeners);

        PlayerData playerData = new PlayerData();
        playerData.uuid = "uuid-1";
        playerData.activeBadge = "old-badge";
        playerData.unlockedBadges = new java.util.HashSet<>();
        Session session = new Session(
                mock(GlobalConfig.class),
                mock(Bundle.class),
                mock(MenuService.class),
                mock(PlayerDataRepository.class),
                mock(Player.class),
                playerData
        );
        when(sessionService.get("uuid-1")).thenReturn(session);

        handler.registerListeners();

        listener(listeners, PlayerBadgeInventoryChangedCommandV1.class)
                .get(new PlayerBadgeInventoryChangedCommandV1("uuid-1", "translator", List.of("translator", "contributor"), "survival"));

        assertThat(session.data.activeBadge).isEqualTo("translator");
        assertThat(session.data.unlockedBadges).containsExactlyInAnyOrder("translator", "contributor");
        verify(playerDisplayService, times(1)).refresh(session);
    }

    @Test
    @DisplayName("password reset command clears password without refresh")
    void passwordResetCommand_clearsPasswordWithoutRefresh() {
        NetworkService network = mock(NetworkService.class);
        SessionService sessionService = mock(SessionService.class);
        PlayerDisplayService playerDisplayService = mock(PlayerDisplayService.class);
        DiscordAdminAccessService discordAdminAccessService = mock(DiscordAdminAccessService.class);

        Config config = new Config();
        config.server = "mini-pvp";

        ModerationTransportHandler handler = new ModerationTransportHandler(network, sessionService, config, playerDisplayService, discordAdminAccessService);

        Map<Class<?>, Cons<?>> listeners = new HashMap<>();
        captureListeners(network, listeners);

        PlayerData playerData = new PlayerData();
        playerData.uuid = "uuid-1";
        playerData.password = "old-hash";
        Session session = new Session(
                mock(GlobalConfig.class),
                mock(Bundle.class),
                mock(MenuService.class),
                mock(PlayerDataRepository.class),
                mock(Player.class),
                playerData
        );
        when(sessionService.get("uuid-1")).thenReturn(session);

        handler.registerListeners();

        listener(listeners, PlayerPasswordResetCommandV1.class)
                .get(new PlayerPasswordResetCommandV1("uuid-1", "survival"));

        assertThat(session.data.password).isEmpty();
        verify(playerDisplayService, times(0)).refresh(any());
    }

    private static void captureListeners(NetworkService network, Map<Class<?>, Cons<?>> listeners) {
        doAnswer(invocation -> {
            listeners.put(invocation.getArgument(0), invocation.getArgument(1));
            return mock(RedisNetworkBackend.Subscription.class);
        }).when(network).subscribe(any(), any());
    }

    @SuppressWarnings("unchecked")
    private static <T> Cons<T> listener(Map<Class<?>, Cons<?>> listeners, Class<T> type) {
        return (Cons<T>) listeners.get(type);
    }
}
