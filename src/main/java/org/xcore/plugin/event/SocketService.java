package org.xcore.plugin.event;

import arc.Events;
import arc.util.Log;
import arc.util.Timer;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.core.Version;
import mindustry.game.EventType;
import mindustry.gen.Groups;
import mindustry.net.Administration;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.event.socket.ChatSocketHandler;
import org.xcore.plugin.event.socket.DiscordLinkSocketHandler;
import org.xcore.plugin.event.socket.MapSocketHandler;
import org.xcore.plugin.event.socket.ModerationSocketHandler;
import org.xcore.plugin.service.NetworkService;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Singleton
public class SocketService {

    private final ChatSocketHandler chatSocketHandler;
    private final DiscordLinkSocketHandler discordLinkSocketHandler;
    private final ModerationSocketHandler moderationSocketHandler;
    private final MapSocketHandler mapSocketHandler;
    private final NetworkService network;
    private final Config config;
    private volatile String cachedPublicHost;

    @Inject
    public SocketService(ChatSocketHandler chatSocketHandler,
                         DiscordLinkSocketHandler discordLinkSocketHandler,
                          ModerationSocketHandler moderationSocketHandler,
                          MapSocketHandler mapSocketHandler,
                          NetworkService network,
                          Config config) {
        this.chatSocketHandler = chatSocketHandler;
        this.discordLinkSocketHandler = discordLinkSocketHandler;
        this.moderationSocketHandler = moderationSocketHandler;
        this.mapSocketHandler = mapSocketHandler;
        this.network = network;
        this.config = config;
    }

    @PostConstruct
    public void init() {
        chatSocketHandler.registerListeners();
        discordLinkSocketHandler.registerListeners();
        moderationSocketHandler.registerListeners();
        mapSocketHandler.registerListeners();

        Events.on(EventType.ServerLoadEvent.class, event -> {
            network.post(new SocketEvents.ServerActionEvent("Server loaded", config.server));

            Timer.schedule(() -> {
                try {
                    network.post(new SocketEvents.ServerHeartbeatEvent(
                            config.server,
                            config.discordChannelId,
                            Groups.player.size(),
                            config.getNoAdminPlayerLimit(),
                            Version.buildString(),
                            resolveHostAddress(),
                            Administration.Config.port.num()
                    ));
                } catch (Exception ex) {
                    Log.err("Failed to publish heartbeat", ex);
                }
            }, 10f, 30f);
        });
    }

    private String resolveHostAddress() {
        String cached = cachedPublicHost;
        if (cached != null && !cached.isBlank()) {
            return cached;
        }

        try {
            HttpURLConnection connection = (HttpURLConnection) new URL("https://api.ipify.org").openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);

            try (InputStream stream = connection.getInputStream()) {
                String host = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
                if (!host.isBlank()) {
                    cachedPublicHost = host;
                    return host;
                }
            }
        } catch (Exception ex) {
            Log.warn("Failed to resolve public host via api.ipify.org: @", ex.toString());
        }

        try {
            return cachedPublicHost;
        } catch (Exception ignored) {
            return null;
        }
    }
}
