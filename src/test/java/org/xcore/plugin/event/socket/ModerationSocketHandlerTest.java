package org.xcore.plugin.event.socket;

import arc.func.Cons;
import mindustry.Vars;
import mindustry.core.NetServer;
import mindustry.gen.Player;
import mindustry.net.Administration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.FindService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.service.DiscordAdminAccessService;
import org.xcore.plugin.service.network.RedisNetworkBackend;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationSocketHandlerTest {

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
    @DisplayName("discord admin access event applies persisted admin flags")
    void discordAdminAccessEvent_appliesPersistedAdminFlags() {
        NetworkService network = mock(NetworkService.class);
        SessionService sessionService = mock(SessionService.class);
        FindService find = mock(FindService.class);
        PlayerDisplayService playerDisplayService = mock(PlayerDisplayService.class);
        DiscordAdminAccessService discordAdminAccessService = mock(DiscordAdminAccessService.class);

        Config config = new Config();
        config.server = "mini-pvp";

        ModerationSocketHandler handler = new ModerationSocketHandler(network, sessionService, find, config, playerDisplayService, discordAdminAccessService);

        Map<Class<?>, Cons<?>> listeners = new HashMap<>();
        captureListeners(network, listeners);

        when(discordAdminAccessService.applyDiscordAdminAccess("uuid-1", "123", "discord-user")).thenReturn(true);

        handler.registerListeners();

        listener(listeners, SocketEvents.DiscordAdminAccessChanged.class)
                .get(new SocketEvents.DiscordAdminAccessChanged(
                        "uuid-1", 7, "123", "discord-user", true,
                        DiscordAdminAccessService.SOURCE_DISCORD_ROLE, "tester", "sync", "mini-pvp", 10L
                ));

        verify(discordAdminAccessService).applyDiscordAdminAccess("uuid-1", "123", "discord-user");
    }

    @Test
    @DisplayName("discord admin revoke event clears persisted admin flags")
    void discordAdminRevokeEvent_clearsPersistedAdminFlags() {
        NetworkService network = mock(NetworkService.class);
        SessionService sessionService = mock(SessionService.class);
        FindService find = mock(FindService.class);
        PlayerDisplayService playerDisplayService = mock(PlayerDisplayService.class);
        DiscordAdminAccessService discordAdminAccessService = mock(DiscordAdminAccessService.class);

        Config config = new Config();
        config.server = "mini-pvp";

        ModerationSocketHandler handler = new ModerationSocketHandler(network, sessionService, find, config, playerDisplayService, discordAdminAccessService);

        Map<Class<?>, Cons<?>> listeners = new HashMap<>();
        captureListeners(network, listeners);

        when(discordAdminAccessService.revokeDiscordAdminAccess("uuid-1")).thenReturn(true);

        handler.registerListeners();

        listener(listeners, SocketEvents.DiscordAdminAccessChanged.class)
                .get(new SocketEvents.DiscordAdminAccessChanged(
                        "uuid-1", 7, "123", "discord-user", false,
                        DiscordAdminAccessService.SOURCE_DISCORD_ROLE, "tester", "sync", "mini-pvp", 11L
                ));

        verify(discordAdminAccessService).revokeDiscordAdminAccess("uuid-1");
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
