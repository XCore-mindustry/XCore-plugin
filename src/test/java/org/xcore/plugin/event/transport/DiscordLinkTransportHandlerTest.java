package org.xcore.plugin.event.transport;

import arc.func.Cons;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.event.TransportEvents;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.DiscordLinkService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordLinkConfirmCommandV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordUnlinkCommandV1;
import org.xcore.protocol.generated.shared.ActorRefV1;
import org.xcore.protocol.generated.shared.ActorRefV1ActorType;
import org.xcore.protocol.generated.shared.DiscordIdentityRefV1;
import org.xcore.protocol.generated.shared.PlayerRefV1;

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
    @DisplayName("discord link confirm command confirms link and notifies online player")
    void discordLinkConfirmCommand_confirmsLinkAndNotifiesOnlinePlayer() {
        NetworkService network = mock(NetworkService.class);
        DiscordLinkService discordLinkService = mock(DiscordLinkService.class);
        SessionService sessionService = mock(SessionService.class);

        DiscordLinkTransportHandler handler = new DiscordLinkTransportHandler(network, discordLinkService, sessionService);
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

        listener(listeners, DiscordLinkConfirmCommandV1.class)
                .get(new DiscordLinkConfirmCommandV1(
                        "ABC123",
                        new PlayerRefV1("uuid-7", 7, "Target", null),
                        new DiscordIdentityRefV1("123", "discord-user"),
                        "mini-pvp",
                        "2026-04-28T00:00:01Z"
                ));

        verify(localization).send(any(), any());
    }

    @Test
    @DisplayName("discord unlink command updates offline player data without online session")
    void discordUnlinkCommand_updatesOfflinePlayerDataWithoutOnlineSession() {
        NetworkService network = mock(NetworkService.class);
        DiscordLinkService discordLinkService = mock(DiscordLinkService.class);
        SessionService sessionService = mock(SessionService.class);

        DiscordLinkTransportHandler handler = new DiscordLinkTransportHandler(network, discordLinkService, sessionService);
        Map<Class<?>, Cons<?>> listeners = new HashMap<>();

        doAnswer(invocation -> {
            listeners.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(network).subscribe(any(), any());

        when(discordLinkService.unlink(eq("uuid-7"))).thenReturn(true);

        handler.registerListeners();

        listener(listeners, DiscordUnlinkCommandV1.class)
                .get(new DiscordUnlinkCommandV1(
                        new PlayerRefV1("uuid-7", 7, "Target", null),
                        new DiscordIdentityRefV1("123", "discord"),
                        new ActorRefV1("discord", null, ActorRefV1ActorType.SYSTEM),
                        "mini-other",
                        "2026-04-28T00:00:01Z"
                ));

        verify(discordLinkService).unlink("uuid-7");
    }

    @SuppressWarnings("unchecked")
    private static <T> Cons<T> listener(Map<Class<?>, Cons<?>> listeners, Class<T> type) {
        return (Cons<T>) listeners.get(type);
    }
}
