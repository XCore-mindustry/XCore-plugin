package org.xcore.plugin.event.transport;

import arc.func.Cons;
import mindustry.Vars;
import mindustry.core.NetServer;
import mindustry.net.Administration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.event.TransportEvents;
import org.xcore.plugin.service.DiscordAdminAccessService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.service.network.RedisNetworkBackend;
import org.xcore.plugin.session.SessionService;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordAdminAccessChangedCommandV1;
import org.xcore.protocol.generated.shared.DiscordIdentityRefV1;
import org.xcore.protocol.generated.shared.PlayerRefV1;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
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
