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
import org.xcore.protocol.generated.messages.chat.ChatMessages.ServerActionV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.ServerHeartbeatV1;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.event.transport.ChatTransportHandler;
import org.xcore.plugin.event.transport.DiscordLinkTransportHandler;
import org.xcore.plugin.event.transport.MapTransportHandler;
import org.xcore.plugin.event.transport.ModerationTransportHandler;
import org.xcore.plugin.service.NetworkService;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Singleton
public class TransportService {

    static final long HOST_RESOLUTION_FAILURE_BACKOFF_MS = 300_000L;
    private static final String PUBLIC_HOST_RESOLVER_URL = "https://api.ipify.org";

    private final ChatTransportHandler chatTransportHandler;
    private final DiscordLinkTransportHandler discordLinkTransportHandler;
    private final ModerationTransportHandler moderationTransportHandler;
    private final MapTransportHandler mapTransportHandler;
    private final NetworkService network;
    private final TomlXcoreConfig config;
    private volatile String cachedPublicHost;
    private volatile long nextPublicHostResolveAttemptAtMs;

    @Inject
    public TransportService(ChatTransportHandler chatTransportHandler,
                            DiscordLinkTransportHandler discordLinkTransportHandler,
                            ModerationTransportHandler moderationTransportHandler,
                            MapTransportHandler mapTransportHandler,
                            NetworkService network,
                             TomlXcoreConfig config) {
        this.chatTransportHandler = chatTransportHandler;
        this.discordLinkTransportHandler = discordLinkTransportHandler;
        this.moderationTransportHandler = moderationTransportHandler;
        this.mapTransportHandler = mapTransportHandler;
        this.network = network;
        this.config = config;
    }

    @PostConstruct
    public void init() {
        network.registerReconnectHook(this::registerListeners);
        registerListeners();

        Events.on(EventType.ServerLoadEvent.class, event -> {
            network.post(new ServerActionV1("Server loaded", config.server.name));

            Timer.schedule(() -> {
                try {
                    network.post(new ServerHeartbeatV1(
                            config.server.name,
                            config.discord.channelId,
                            Groups.player.size(),
                            config.server.playerLimit + Groups.player.count(p -> p.admin),
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

    private void registerListeners() {
        chatTransportHandler.registerListeners();
        discordLinkTransportHandler.registerListeners();
        moderationTransportHandler.registerListeners();
        mapTransportHandler.registerListeners();
    }

    protected String resolveHostAddress() {
        String configuredHost = configuredPublicHostOverride();
        if (configuredHost != null) {
            cachedPublicHost = configuredHost;
            nextPublicHostResolveAttemptAtMs = 0L;
            return configuredHost;
        }

        String cached = cachedPublicHost;
        if (cached != null && !cached.isBlank()) {
            return cached;
        }

        long now = currentTimeMillis();
        if (now < nextPublicHostResolveAttemptAtMs) {
            return null;
        }

        try {
            HttpURLConnection connection = openPublicHostConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);

            try (InputStream stream = connection.getInputStream()) {
                String host = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
                if (!host.isBlank()) {
                    cachedPublicHost = host;
                    nextPublicHostResolveAttemptAtMs = 0L;
                    return host;
                }
            }
        } catch (Exception ex) {
            Log.warn("Failed to resolve public host via api.ipify.org: @", ex.toString());
            nextPublicHostResolveAttemptAtMs = now + hostResolutionFailureBackoffMs();
        }

        return null;
    }

    protected HttpURLConnection openPublicHostConnection() throws Exception {
        return (HttpURLConnection) new URL(PUBLIC_HOST_RESOLVER_URL).openConnection();
    }

    protected long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    protected long hostResolutionFailureBackoffMs() {
        return HOST_RESOLUTION_FAILURE_BACKOFF_MS;
    }

    private String configuredPublicHostOverride() {
        if (config.server.publicHostOverride == null) {
            return null;
        }

        String normalized = config.server.publicHostOverride.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
