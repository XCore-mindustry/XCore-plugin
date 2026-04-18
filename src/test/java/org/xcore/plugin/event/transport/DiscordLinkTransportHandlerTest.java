package org.xcore.plugin.event.transport;

import arc.func.Cons;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.event.TransportEvents;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.DiscordLinkService;
import org.xcore.plugin.service.NetworkService;
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

class DiscordLinkTransportHandlerTest {

    @Test
    @DisplayName("discord link confirm event confirms link and notifies online player")
    void discordLinkConfirmEvent_confirmsLinkAndNotifiesOnlinePlayer() {
        NetworkService network = mock(NetworkService.class);
        DiscordLinkService discordLinkService = mock(DiscordLinkService.class);
        SessionService sessionService = mock(SessionService.class);
        Config config = new Config();
        config.server = "mini-pvp";

        DiscordLinkTransportHandler handler = new DiscordLinkTransportHandler(network, discordLinkService, sessionService, config);
        Map<Class<?>, Cons<?>> listeners = new HashMap<>();

        doAnswer(invocation -> {
            listeners.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(network).subscribe(any(), any());

        Session session = mock(Session.class);
        session.data = PlayerData.builder().uuid("uuid-7").build();
        Localization localization = mock(Localization.class);
        when(session.locale()).thenReturn(localization);
        when(sessionService.get("uuid-7")).thenReturn(session);
        when(discordLinkService.confirmLink("ABC123", "uuid-7", 7, "123", "discord-user"))
                .thenReturn(DiscordLinkService.ConfirmResult.success(PlayerData.builder().uuid("uuid-7").build()));

        handler.registerListeners();

        listener(listeners, TransportEvents.DiscordLinkConfirmEvent.class)
                .get(new TransportEvents.DiscordLinkConfirmEvent("ABC123", "uuid-7", 7, "123", "discord-user", "mini-pvp", 1L));

        verify(localization).send(any(), any());
    }

    @Test
    @DisplayName("discord unlink event updates offline player data without online session")
    void discordUnlinkEvent_updatesOfflinePlayerDataWithoutOnlineSession() {
        NetworkService network = mock(NetworkService.class);
        DiscordLinkService discordLinkService = mock(DiscordLinkService.class);
        SessionService sessionService = mock(SessionService.class);
        Config config = new Config();
        config.server = "mini-pvp";

        DiscordLinkTransportHandler handler = new DiscordLinkTransportHandler(network, discordLinkService, sessionService, config);
        Map<Class<?>, Cons<?>> listeners = new HashMap<>();

        doAnswer(invocation -> {
            listeners.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(network).subscribe(any(), any());

        when(discordLinkService.unlink(eq("uuid-7"))).thenReturn(true);

        handler.registerListeners();

        listener(listeners, TransportEvents.DiscordUnlinkEvent.class)
                .get(new TransportEvents.DiscordUnlinkEvent("uuid-7", 7, "123", "discord", "mini-other", 1L));

        verify(discordLinkService).unlink("uuid-7");
    }

    @SuppressWarnings("unchecked")
    private static <T> Cons<T> listener(Map<Class<?>, Cons<?>> listeners, Class<T> type) {
        return (Cons<T>) listeners.get(type);
    }
}
