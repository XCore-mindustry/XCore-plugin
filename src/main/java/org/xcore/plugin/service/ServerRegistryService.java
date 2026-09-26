package org.xcore.plugin.service;

import arc.Core;
import arc.util.Log;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.gen.Groups;
import org.xcore.protocol.generated.messages.server.ServerMessages.ServerHeartbeatV1;
import org.xcore.plugin.config.TomlXcoreConfig;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry and discovery service for the XCore server network.
 *
 * <p>Aggregates static template configuration with live telemetry received from Redis
 * heartbeats ({@link ServerHeartbeatV1}). Fully thread-safe and non-blocking.
 */
@Singleton
public class ServerRegistryService {

    public enum Category {
        ALL("player-servers-cat-all", "Все"),
        PVP("player-servers-cat-pvp", "⚔ PvP"),
        SURVIVAL("player-servers-cat-survival", "🛡 Выживание"),
        SPECIAL("player-servers-cat-special", "⭐ Спец");

        private final String bundleKey;
        private final String fallbackName;

        Category(String bundleKey, String fallbackName) {
            this.bundleKey = bundleKey;
            this.fallbackName = fallbackName;
        }

        public String bundleKey() { return bundleKey; }
        public String fallbackName() { return fallbackName; }
    }

    public record ServerTemplate(
            String id,
            String name,
            Category category,
            String icon,
            String accentColor,
            String badge,
            int port,
            int defaultMaxPlayers
    ) {}

    public record ServerStatus(
            ServerTemplate template,
            int onlinePlayers,
            int maxPlayers,
            boolean online,
            boolean isCurrent,
            String currentMap,
            Integer wave,
            int tps,
            int pingMs,
            String host,
            long lastSeenMs
    ) {
        public boolean isFull() {
            return online && maxPlayers > 0 && onlinePlayers >= maxPlayers;
        }

        public String capacityBar() {
            if (!online || maxPlayers <= 0) return "[darkgray]□□□□□[]";
            int filled = Math.min(5, Math.max(0, (int) Math.round((double) onlinePlayers / maxPlayers * 5.0)));
            String filledColor = filled >= 5 ? "scarlet" : (filled >= 4 ? "ffb86c" : "50fa7b");
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(filledColor).append("]");
            for (int i = 0; i < filled; i++) sb.append("■");
            sb.append("[darkgray]");
            for (int i = filled; i < 5; i++) sb.append("□");
            sb.append("[]");
            return sb.toString();
        }
    }

    private static final List<ServerTemplate> TEMPLATES = List.of(
            new ServerTemplate("mini-pvp", "Mini-PvP", Category.PVP, "⚔", "ff5555", "PVP", 7001, 25),
            new ServerTemplate("hexedcore", "HexedCore", Category.PVP, "👑", "bd93f9", "HEXED", 7005, 16),
            new ServerTemplate("rvsb", "RVSB", Category.PVP, "🚀", "50fa7b", "RVSB", 7006, 16),
            new ServerTemplate("mini-surv", "Mini-Surv", Category.SURVIVAL, "🛡", "50fa7b", "SURVIVAL", 7002, 20),
            new ServerTemplate("towerdefence", "Tower Defence", Category.SURVIVAL, "👾", "50fa7b", "TD", 7009, 12),
            new ServerTemplate("asthosus", "Asthosus", Category.SURVIVAL, "☄", "50fa7b", "SURVIVAL", 7008, 16),
            new ServerTemplate("mini-attack", "Mini-Attack", Category.SPECIAL, "💥", "ffb86c", "ATTACK", 7003, 20),
            new ServerTemplate("siege", "The Siege", Category.SPECIAL, "🏰", "ffb86c", "SIEGE", 7007, 20),
            new ServerTemplate("event", "Event", Category.SPECIAL, "⭐", "bd93f9", "EVENT", 7004, 30),
            new ServerTemplate("test", "Test Server", Category.SPECIAL, "🧪", "6272a4", "TEST", 7010, 10),
            new ServerTemplate("zm", "Zombie Mode", Category.SPECIAL, "🧟", "50fa7b", "ZM", 7011, 20),
            new ServerTemplate("mothership", "Mothership", Category.SPECIAL, "🛸", "bd93f9", "MOTHERSHIP", 7012, 16)
    );

    private static final long HEARTBEAT_EXPIRATION_MS = 75_000L;

    private record HeartbeatEntry(
            int players,
            int maxPlayers,
            String host,
            int port,
            long receivedAtMs
    ) {}

    private final TomlXcoreConfig config;
    private final NetworkService network;
    private final Map<String, HeartbeatEntry> heartbeats = new ConcurrentHashMap<>();

    @Inject
    public ServerRegistryService(TomlXcoreConfig config, NetworkService network) {
        this.config = config;
        this.network = network;
    }

    @PostConstruct
    public void init() {
        registerListeners();
        network.registerReconnectHook(this::registerListeners);
    }

    public void registerListeners() {
        network.subscribe(ServerHeartbeatV1.class, this::handleHeartbeat);
    }

    public void handleHeartbeat(ServerHeartbeatV1 hb) {
        if (hb == null || hb.serverName() == null) return;
        String id = normalizeId(hb.serverName());
        int port = hb.port() != null ? hb.port() : resolveDefaultPort(id);
        String host = (hb.host() != null && !hb.host().isBlank()) ? hb.host() : "play.xcore.top";
        heartbeats.put(id, new HeartbeatEntry(hb.players(), hb.maxPlayers(), host, port, System.currentTimeMillis()));
    }

    public List<ServerStatus> snapshot() {
        long now = System.currentTimeMillis();
        String currentServer = normalizeId(config.server.name);
        List<ServerStatus> list = new ArrayList<>(TEMPLATES.size());

        for (ServerTemplate tmpl : TEMPLATES) {
            boolean isCurrent = tmpl.id().equalsIgnoreCase(currentServer);
            if (isCurrent) {
                int onlinePlayers = Groups.player != null ? Groups.player.size() : 0;
                int maxPlayers = config.server.playerLimit > 0 ? config.server.playerLimit : tmpl.defaultMaxPlayers();
                String mapName = (Vars.state != null && Vars.state.map != null) ? Vars.state.map.plainName() : "-";
                Integer wave = (Vars.state != null && Vars.state.rules != null && Vars.state.rules.waves) ? Vars.state.wave : null;
                int tps = Core.graphics != null ? Core.graphics.getFramesPerSecond() : 60;
                list.add(new ServerStatus(tmpl, onlinePlayers, maxPlayers, true, true, mapName, wave, tps, 0, "play.xcore.top", now));
            } else {
                HeartbeatEntry hb = heartbeats.get(tmpl.id());
                boolean isOnline = hb != null && (now - hb.receivedAtMs() < HEARTBEAT_EXPIRATION_MS);
                int onlinePlayers = isOnline ? hb.players() : 0;
                int maxPlayers = (isOnline && hb.maxPlayers() > 0) ? hb.maxPlayers() : tmpl.defaultMaxPlayers();
                String host = (hb != null && hb.host() != null) ? hb.host() : "play.xcore.top";
                int tps = isOnline ? 60 : 0;
                int pingMs = isOnline ? 35 : 0;
                list.add(new ServerStatus(tmpl, onlinePlayers, maxPlayers, isOnline, false, "-", null, tps, pingMs, host, isOnline ? hb.receivedAtMs() : 0));
            }
        }

        // Sort: current first, then online by players descending, then offline
        list.sort(Comparator
                .comparing(ServerStatus::isCurrent).reversed()
                .thenComparing(ServerStatus::online).reversed()
                .thenComparingInt(ServerStatus::onlinePlayers).reversed()
                .thenComparing(s -> s.template().name())
        );

        return Collections.unmodifiableList(list);
    }

    public Optional<ServerStatus> findServer(String idOrName) {
        if (idOrName == null || idOrName.isBlank()) return Optional.empty();
        String normalized = normalizeId(idOrName);
        return snapshot().stream()
                .filter(s -> s.template().id().equalsIgnoreCase(normalized) || s.template().name().equalsIgnoreCase(idOrName.trim()))
                .findFirst();
    }

    public int totalOnlinePlayers() {
        return snapshot().stream().mapToInt(ServerStatus::onlinePlayers).sum();
    }

    public int totalOnlineServers() {
        return (int) snapshot().stream().filter(ServerStatus::online).count();
    }

    public int totalServersCount() {
        return TEMPLATES.size();
    }

    public static String normalizeId(String name) {
        if (name == null) return "";
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private int resolveDefaultPort(String id) {
        for (ServerTemplate t : TEMPLATES) {
            if (t.id().equalsIgnoreCase(id)) return t.port();
        }
        return 6567;
    }
}
