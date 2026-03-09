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
    @DisplayName("admin confirm event persists admin flags and refreshes display")
    void adminConfirmEvent_persistsAdminFlagsAndRefreshesDisplay() {
        NetworkService network = mock(NetworkService.class);
        SessionService sessionService = mock(SessionService.class);
        FindService find = mock(FindService.class);
        PlayerDisplayService playerDisplayService = mock(PlayerDisplayService.class);

        Config config = new Config();
        config.server = "mini-pvp";

        ModerationSocketHandler handler = new ModerationSocketHandler(network, sessionService, find, config, playerDisplayService);

        Map<Class<?>, Cons<?>> listeners = new HashMap<>();
        captureListeners(network, listeners);

        Player player = Player.create();
        player.admin = false;
        player.name = "PlayerOne";

        Administration.PlayerInfo info = new Administration.PlayerInfo();
        info.adminUsid = "usid-1";
        info.lastName = "PlayerOne";

        Session session = mock(Session.class);
        session.data = PlayerData.builder().uuid("uuid-1").admin(false).adminConfirmed(false).build();
        Localization localization = mock(Localization.class);
        when(session.locale()).thenReturn(localization);
        when(find.playerInfo("uuid-1")).thenReturn(info);
        when(find.playerByUuid("uuid-1")).thenReturn(player);
        when(sessionService.get(player)).thenReturn(session);

        handler.registerListeners();

        listener(listeners, SocketEvents.AdminRequestConfirmEvent.class)
                .get(new SocketEvents.AdminRequestConfirmEvent("uuid-1", "mini-pvp"));

        verify(sessionService).updateAdminStatus(session, true, true);
        verify(playerDisplayService).refresh(session);
        verify(localization).send(eq("commands-login-confirmed"), any());
    }

    @Test
    @DisplayName("remove admin event persists cleared admin flags and refreshes display")
    void removeAdminEvent_persistsClearedAdminFlagsAndRefreshesDisplay() {
        NetworkService network = mock(NetworkService.class);
        SessionService sessionService = mock(SessionService.class);
        FindService find = mock(FindService.class);
        PlayerDisplayService playerDisplayService = mock(PlayerDisplayService.class);

        Config config = new Config();
        config.server = "mini-pvp";

        ModerationSocketHandler handler = new ModerationSocketHandler(network, sessionService, find, config, playerDisplayService);

        Map<Class<?>, Cons<?>> listeners = new HashMap<>();
        captureListeners(network, listeners);

        Player player = Player.create();
        player.admin = true;
        player.name = "PlayerOne";

        Administration.PlayerInfo info = new Administration.PlayerInfo();
        info.admin = true;
        info.lastName = "PlayerOne";

        Session session = mock(Session.class);
        session.data = PlayerData.builder().uuid("uuid-1").admin(true).adminConfirmed(true).build();
        when(find.playerInfo("uuid-1")).thenReturn(info);
        when(find.playerByUuid("uuid-1")).thenReturn(player);
        when(sessionService.get(player)).thenReturn(session);

        handler.registerListeners();

        listener(listeners, SocketEvents.RemoveAdmin.class)
                .get(new SocketEvents.RemoveAdmin("uuid-1"));

        verify(sessionService).updateAdminStatus(session, false, false);
        verify(playerDisplayService).refresh(session);
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
