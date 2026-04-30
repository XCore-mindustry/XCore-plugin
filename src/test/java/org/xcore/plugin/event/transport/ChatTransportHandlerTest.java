package org.xcore.plugin.event.transport;

import arc.func.Cons;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.protocol.generated.messages.chat.ChatMessages.ChatDiscordIngressCommandV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.ChatGlobalV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.ChatPrivateV1;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.PrivateMessageService;
import org.xcore.plugin.service.network.RedisNetworkBackend;
import org.xcore.plugin.session.SessionService;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

        listener(listeners, ChatGlobalV1.class)
                .get(new ChatGlobalV1("player", "hello", "alpha"));

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

        listener(listeners, ChatDiscordIngressCommandV1.class)
                .get(new ChatDiscordIngressCommandV1("bot", "hello", "other-server"));

        verifyNoInteractions(sessionService);
    }

    @Test
    @DisplayName("private chat event is delivered only for remote servers")
    void privateChatEvent_isDeliveredOnlyForRemoteServers() {
        NetworkService network = mock(NetworkService.class);
        SessionService sessionService = mock(SessionService.class);
        PrivateMessageService privateMessageService = mock(PrivateMessageService.class);
        Config config = new Config();
        config.server = "mini-pvp";

        ChatTransportHandler handler = new ChatTransportHandler(network, sessionService, privateMessageService, config);

        Map<Class<?>, Cons<?>> listeners = new HashMap<>();
        captureListeners(network, listeners);

        handler.registerListeners();

        Session recipient = mock(Session.class);
        recipient.player = mock(mindustry.gen.Player.class);
        recipient.data = PlayerData.builder().uuid("uuid-to").pid(42).nickname("Target").build();
        when(sessionService.get("uuid-to")).thenReturn(recipient);

        listener(listeners, ChatPrivateV1.class)
                .get(new ChatPrivateV1("uuid-from", 7, "Sender", "uuid-to", 42, "hello", "survival"));

        verify(privateMessageService).deliverIncoming(any(), same(recipient));
        verify(sessionService).get("uuid-to");
    }

    @Test
    @DisplayName("private chat event is ignored for same server")
    void privateChatEvent_isIgnoredForSameServer() {
        NetworkService network = mock(NetworkService.class);
        SessionService sessionService = mock(SessionService.class);
        PrivateMessageService privateMessageService = mock(PrivateMessageService.class);
        Config config = new Config();
        config.server = "mini-pvp";

        ChatTransportHandler handler = new ChatTransportHandler(network, sessionService, privateMessageService, config);

        Map<Class<?>, Cons<?>> listeners = new HashMap<>();
        captureListeners(network, listeners);

        handler.registerListeners();

        listener(listeners, ChatPrivateV1.class)
                .get(new ChatPrivateV1("uuid-from", 7, "Sender", "uuid-to", 42, "hello", "mini-pvp"));

        verify(sessionService, never()).get(anyString());
        verifyNoInteractions(privateMessageService);
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
