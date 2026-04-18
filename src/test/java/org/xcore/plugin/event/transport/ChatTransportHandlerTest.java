package org.xcore.plugin.event.transport;

import arc.func.Cons;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.event.TransportEvents;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.PrivateMessageService;
import org.xcore.plugin.service.network.RedisNetworkBackend;
import org.xcore.plugin.session.SessionService;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ChatTransportHandlerTest {

    @Test
    @DisplayName("global chat event is broadcast only to players with global chat enabled")
    void globalChatEvent_isBroadcastOnlyToPlayersWithGlobalChatEnabled() {
        NetworkService network = mock(NetworkService.class);
        SessionService sessionService = mock(SessionService.class);
        PrivateMessageService privateMessageService = mock(PrivateMessageService.class);
        Config config = new Config();
        config.server = "mini-pvp";

        ChatTransportHandler handler = new ChatTransportHandler(network, sessionService, privateMessageService, config);

        Map<Class<?>, Cons<?>> listeners = new HashMap<>();
        captureListeners(network, listeners);

        handler.registerListeners();

        listener(listeners, TransportEvents.GlobalChatEvent.class)
                .get(new TransportEvents.GlobalChatEvent("player", "hello", "alpha"));

        verify(sessionService).broadcastFiltered(
                org.mockito.Mockito.eq("global-chat-format"),
                org.mockito.Mockito.eq(com.ospx.flubundle.Bundle.args(
                        "server", "alpha",
                        "author", "player",
                        "message", "hello"
                )),
                any()
        );
    }

    @Test
    @DisplayName("discord relay event is ignored for other servers")
    void discordRelayEvent_isIgnoredForOtherServers() {
        NetworkService network = mock(NetworkService.class);
        SessionService sessionService = mock(SessionService.class);
        PrivateMessageService privateMessageService = mock(PrivateMessageService.class);
        Config config = new Config();
        config.server = "mini-pvp";

        ChatTransportHandler handler = new ChatTransportHandler(network, sessionService, privateMessageService, config);

        Map<Class<?>, Cons<?>> listeners = new HashMap<>();
        captureListeners(network, listeners);

        handler.registerListeners();

        listener(listeners, TransportEvents.DiscordMessageEvent.class)
                .get(new TransportEvents.DiscordMessageEvent("bot", "hello", "other-server"));

        verifyNoInteractions(sessionService);
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
