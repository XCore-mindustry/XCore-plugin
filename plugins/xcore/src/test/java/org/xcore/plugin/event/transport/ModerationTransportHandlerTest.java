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
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.MainThreadDispatcher;
import org.xcore.plugin.concurrent.StorageExecutor;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.rating.ladder.LadderService;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.service.network.RedisNetworkBackend;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.MenuService;
import org.xcore.protocol.generated.messages.identity.IdentityMessages.PlayerActiveBadgeChangedCommandV1;
import org.xcore.protocol.generated.messages.identity.IdentityMessages.PlayerBadgeInventoryChangedCommandV1;
import org.xcore.protocol.generated.messages.identity.IdentityMessages.PlayerBadgeSymbolColorModeChangedCommandV1;
import org.xcore.protocol.generated.messages.identity.IdentityMessages.PlayerCustomNicknameChangedCommandV1;

import java.util.ArrayList;
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

    /**
     * Runs marshalled work inline, so the pre-existing behavioural assertions stay
     * synchronous. The marshalling itself is asserted separately, below.
     */
    private Async async;

    @BeforeEach
    void setUp() {
        previousNetServer = Vars.netServer;
        NetServer netServer = mock(NetServer.class);
        netServer.admins = mock(Administration.class);
        Vars.netServer = netServer;
        async = new Async(new StorageExecutor(4), Runnable::run);
    }

    @AfterEach
    void tearDown() {
        Vars.netServer = previousNetServer;
    }

    @Test
    @DisplayName("player session commands update session state and refresh display when needed")
    void playerSessionCommands_updateSessionStateAndRefreshDisplay() {
        NetworkService network = mock(NetworkService.class);
        SessionService sessionService = mock(SessionService.class);
        PlayerDisplayService playerDisplayService = mock(PlayerDisplayService.class);

        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";

        ModerationTransportHandler handler = new ModerationTransportHandler(
                network, sessionService, config, playerDisplayService, mock(LadderService.class), async,
                new org.xcore.plugin.permission.RemoteConsoleScope());

        Map<Class<?>, Cons<?>> listeners = new HashMap<>();
        captureListeners(network, listeners);

        PlayerData playerData = new PlayerData();
        playerData.uuid = "uuid-1";
        playerData.customNickname = "Old";
        playerData.activeBadge = "";
        playerData.badgeSymbolColorMode = "default";
        Session session = new Session(
                mock(org.xcore.plugin.config.TomlSecretsConfig.class),
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

        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";

        ModerationTransportHandler handler = new ModerationTransportHandler(
                network, sessionService, config, playerDisplayService, mock(LadderService.class), async,
                new org.xcore.plugin.permission.RemoteConsoleScope());

        Map<Class<?>, Cons<?>> listeners = new HashMap<>();
        captureListeners(network, listeners);

        PlayerData playerData = new PlayerData();
        playerData.uuid = "uuid-1";
        playerData.activeBadge = "old-badge";
        playerData.unlockedBadges = new java.util.HashSet<>();
        Session session = new Session(
                mock(org.xcore.plugin.config.TomlSecretsConfig.class),
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
    @DisplayName("Mindustry mutations are deferred off the subscriber thread until the main thread runs them")
    void mindustryMutations_areMarshalledToMainThread() {
        NetworkService network = mock(NetworkService.class);
        SessionService sessionService = mock(SessionService.class);
        PlayerDisplayService playerDisplayService = mock(PlayerDisplayService.class);

        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";

        // A dispatcher that queues instead of running, standing in for a game thread that
        // has not come back around yet.
        List<Runnable> marshalled = new ArrayList<>();
        MainThreadDispatcher deferred = marshalled::add;

        ModerationTransportHandler handler = new ModerationTransportHandler(
                network, sessionService, config, playerDisplayService,
                mock(LadderService.class), new Async(new StorageExecutor(4), deferred),
                new org.xcore.plugin.permission.RemoteConsoleScope());

        Map<Class<?>, Cons<?>> listeners = new HashMap<>();
        captureListeners(network, listeners);

        PlayerData playerData = new PlayerData();
        playerData.uuid = "uuid-1";
        playerData.activeBadge = "old-badge";
        Session session = new Session(
                mock(org.xcore.plugin.config.TomlSecretsConfig.class),
                mock(Bundle.class),
                mock(MenuService.class),
                mock(PlayerDataRepository.class),
                mock(Player.class),
                playerData
        );
        when(sessionService.get("uuid-1")).thenReturn(session);

        handler.registerListeners();

        // Invoking the listener is what the Redis subscriber thread does. PlayerData and
        // Player.name must not be touched yet, because this is not the game thread.
        listener(listeners, PlayerActiveBadgeChangedCommandV1.class)
                .get(new PlayerActiveBadgeChangedCommandV1("uuid-1", "translator", "survival"));

        assertThat(playerData.activeBadge)
                .as("subscriber thread must not mutate game-facing state")
                .isEqualTo("old-badge");
        assertThat(marshalled)
                .as("the mutation must be queued for the game thread")
                .hasSize(1);

        marshalled.forEach(Runnable::run);

        assertThat(playerData.activeBadge).isEqualTo("translator");
        verify(playerDisplayService, times(1)).refresh(session);
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
