package org.xcore.plugin.event;

import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.event.socket.ChatSocketHandler;
import org.xcore.plugin.event.socket.MapSocketHandler;
import org.xcore.plugin.event.socket.ModerationSocketHandler;
import org.xcore.plugin.service.NetworkService;

@Singleton
public class SocketService {

    private final ChatSocketHandler chatSocketHandler;
    private final ModerationSocketHandler moderationSocketHandler;
    private final MapSocketHandler mapSocketHandler;
    private final NetworkService network;
    private final Config config;

    @Inject
    public SocketService(ChatSocketHandler chatSocketHandler,
                         ModerationSocketHandler moderationSocketHandler,
                         MapSocketHandler mapSocketHandler,
                         NetworkService network,
                         Config config) {
        this.chatSocketHandler = chatSocketHandler;
        this.moderationSocketHandler = moderationSocketHandler;
        this.mapSocketHandler = mapSocketHandler;
        this.network = network;
        this.config = config;
    }

    @PostConstruct
    public void init() {
        chatSocketHandler.registerListeners();
        moderationSocketHandler.registerListeners();
        mapSocketHandler.registerListeners();

        network.post(new SocketEvents.ServerActionEvent("Server loaded", config.server));
    }
}
