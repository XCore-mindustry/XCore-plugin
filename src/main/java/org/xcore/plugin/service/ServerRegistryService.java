package org.xcore.plugin.service;

import arc.Core;
import arc.util.Log;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.gen.Groups;
import mindustry.gen.Iconc;
import mindustry.net.Administration;
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
        ALL("player-servers-cat-all", "Все", Iconc.grid),
        PVP("player-servers-cat-pvp", "PvP", Iconc.modePvp),
        SURVIVAL("player-servers-cat-survival", "Выживание", Iconc.modeSurvival),
        SPECIAL("player-servers-cat-special", "Спец", Iconc.star);

        private final String bundleKey;
        private final String fallbackName;
        private final char icon;

        Category(String bundleKey, String fallbackName, char icon) {
            this.bundleKey = bundleKey;
            this.fallbackName = fallbackName;
            this.icon = icon;
        }

        public String bundleKey() { return bundleKey; }
        public String fallbackName() { return fallbackName; }
        public char icon() { return icon; }
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
            String description,
            String currentMap,
            Integer wave,
            String mode,
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
            String filledColor = filled >= 5 ? "#e55454" : (filled >= 4 ? "#ffb86c" : "#50fa7b");
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
            // PVP
            new ServerTemplate("mini-pvp", "Mini-PvP", Category.PVP, String.valueOf(Iconc.modePvp), "ff5555", "PVP", 7001, 25),
            new ServerTemplate("hexedcore", "HexedCore", Category.PVP, String.valueOf(Iconc.admin), "bd93f9", "HEXED", 7005, 16),
            new ServerTemplate("rvsb", "RVSB", Category.PVP, String.valueOf(Iconc.planeOutline), "50fa7b", "RVSB", 7006, 16),
            new ServerTemplate("anarchy", "Anarchy PvP", Category.PVP, String.valueOf(Iconc.power), "ff5555", "ANARCHY", 7010, 32),
            new ServerTemplate("spacewar", "Space Fleet", Category.PVP, String.valueOf(Iconc.units), "ff79c6", "PVP", 7011, 20),

            // SURVIVAL
            new ServerTemplate("mini-surv", "Mini-Surv", Category.SURVIVAL, String.valueOf(Iconc.modeSurvival), "50fa7b", "SURVIVAL", 7002, 20),
            new ServerTemplate("towerdefence", "Tower Defence", Category.SURVIVAL, String.valueOf(Iconc.turret), "50fa7b", "TD", 7009, 12),
            new ServerTemplate("asthosus", "Asthosus", Category.SURVIVAL, String.valueOf(Iconc.planet), "50fa7b", "SURVIVAL", 7008, 16),
            new ServerTemplate("hardcore", "Hardcore Surv", Category.SURVIVAL, String.valueOf(Iconc.warning), "ffb86c", "HARDCORE", 7012, 16),
            new ServerTemplate("sandbox", "Creative Sandbox", Category.SURVIVAL, String.valueOf(Iconc.hammer), "8be9fd", "SANDBOX", 7013, 20),

            // SPECIAL
            new ServerTemplate("mini-attack", "Mini-Attack", Category.SPECIAL, String.valueOf(Iconc.modeAttack), "ffb86c", "ATTACK", 7003, 20),
            new ServerTemplate("siege", "The Siege", Category.SPECIAL, String.valueOf(Iconc.defense), "ffb86c", "SIEGE", 7007, 20),
            new ServerTemplate("event", "Event Hub", Category.SPECIAL, String.valueOf(Iconc.star), "bd93f9", "EVENT", 7004, 30),
            new ServerTemplate("bossrush", "Boss Rush", Category.SPECIAL, String.valueOf(Iconc.unitToxopid), "ff5555", "BOSS", 7014, 16),
            new ServerTemplate("strategy", "Grand Strategy", Category.SPECIAL, String.valueOf(Iconc.map), "f1fa8c", "RTS", 7015, 20)
    );

    private static final long HEARTBEAT_EXPIRATION_MS = 75_000L;

    private record HeartbeatEntry(
            int players,
            int maxPlayers,
            String host,
            int port,
            String description,
            String map,
            Integer wave,
            String mode,
            int tps,
            int pingMs,
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
        if (isDevEnvironment()) {
            populateDevMockServers();
            try {
                arc.util.Timer.schedule(this::populateDevMockServers, 30f, 30f);
            } catch (Throwable ignored) {
            }
        }
    }

    public boolean isDevEnvironment() {
        return (config != null && config.server != null && config.server.name != null
                && (config.server.name.toLowerCase().contains("dev") || config.server.name.equalsIgnoreCase("server")))
                || "true".equalsIgnoreCase(System.getenv("XCORE_DEV_MOCKS"))
                || Boolean.getBoolean("xcore.dev");
    }

    public void populateDevMockServers() {
        long now = System.currentTimeMillis();
        // PVP
        heartbeats.put("mini-pvp", new HeartbeatEntry(12, 25, "127.0.0.1", 7001, "Fast-paced 1v1 and 2v2 tactical duels", "Crossing", 4, "pvp", 61, 28, now));
        heartbeats.put("hexedcore", new HeartbeatEntry(8, 16, "127.0.0.1", 7005, "Battle royale: conquer hexes and expand", "Hexed Isles", 1, "hexed", 60, 32, now));
        heartbeats.put("rvsb", new HeartbeatEntry(14, 16, "127.0.0.1", 7006, "Build stations and engage fleet battles", "Orbit Omega", 1, "rvsb", 60, 35, now));
        heartbeats.put("anarchy", new HeartbeatEntry(19, 32, "127.0.0.1", 7010, "No rules, high tech, ruthless warfare", "Wasteland", 12, "anarchy", 59, 41, now));
        heartbeats.put("spacewar", new HeartbeatEntry(6, 20, "127.0.0.1", 7011, "Naval fleet combat with massive flagships", "Deep Void", 2, "pvp", 61, 30, now));

        // SURVIVAL
        heartbeats.put("mini-surv", new HeartbeatEntry(15, 20, "127.0.0.1", 7002, "Co-op classic survival with custom maps", "Glacier", 45, "survival", 60, 25, now));
        heartbeats.put("towerdefence", new HeartbeatEntry(9, 12, "127.0.0.1", 7009, "Automated maze defense against boss waves", "Spiral Core", 28, "td", 60, 33, now));
        heartbeats.put("asthosus", new HeartbeatEntry(4, 16, "127.0.0.1", 7008, "Deep exploration campaign on Serpulo", "Ruins", 15, "survival", 60, 39, now));
        heartbeats.put("hardcore", new HeartbeatEntry(7, 16, "127.0.0.1", 7012, "Permadeath waves and extreme resource limits", "Inferno", 62, "hardcore", 58, 44, now));
        heartbeats.put("sandbox", new HeartbeatEntry(5, 20, "127.0.0.1", 7013, "Unlimited building, testing, and schematics", "Testing Ground", 1, "sandbox", 62, 22, now));

        // SPECIAL
        heartbeats.put("mini-attack", new HeartbeatEntry(11, 20, "127.0.0.1", 7003, "Assault fortified enemy cores on compact maps", "Fortress", 8, "attack", 60, 29, now));
        heartbeats.put("siege", new HeartbeatEntry(13, 20, "127.0.0.1", 7007, "Coordinate defenses against legendary siege engines", "Citadel", 34, "siege", 60, 36, now));
        heartbeats.put("event", new HeartbeatEntry(24, 30, "127.0.0.1", 7004, "Weekend community tournament and minigames", "Stadium", 1, "event", 61, 26, now));
        heartbeats.put("bossrush", new HeartbeatEntry(6, 16, "127.0.0.1", 7014, "Continuous boss titan waves with mythic loot", "Titan Crater", 19, "boss", 60, 31, now));
        heartbeats.put("strategy", new HeartbeatEntry(10, 20, "127.0.0.1", 7015, "Sector capture RTS with macro resource flow", "Archipelago", 3, "rts", 60, 34, now));
    }

    public void registerListeners() {
        network.subscribe(ServerHeartbeatV1.class, this::handleHeartbeat);
    }

    public void handleHeartbeat(ServerHeartbeatV1 hb) {
        if (hb == null || hb.serverName() == null) return;
        String id = normalizeId(hb.serverName());
        int port = hb.port() != null ? hb.port() : resolveDefaultPort(id);
        String host = (hb.host() != null && !hb.host().isBlank()) ? hb.host() : "play.xcore.top";
        String desc = hb.description() != null ? hb.description().trim() : "";
        String map = (hb.map() != null && !hb.map().isBlank()) ? hb.map() : "-";
        Integer wave = hb.wave();
        String mode = (hb.mode() != null && !hb.mode().isBlank()) ? hb.mode() : "";
        int tps = (hb.tps() != null && hb.tps() > 0) ? hb.tps() : 60;

        heartbeats.put(id, new HeartbeatEntry(hb.players(), hb.maxPlayers(), host, port, desc, map, wave, mode, tps, 0, System.currentTimeMillis()));
    }

    public List<ServerStatus> snapshot() {
        long now = System.currentTimeMillis();
        if (isDevEnvironment() && heartbeats.isEmpty()) {
            populateDevMockServers();
        }
        String currentServer = normalizeId(config.server.name);
        List<ServerStatus> list = new ArrayList<>(TEMPLATES.size());

        for (ServerTemplate tmpl : TEMPLATES) {
            boolean isCurrent = tmpl.id().equalsIgnoreCase(currentServer);
            if (isCurrent) {
                int onlinePlayers = Groups.player != null ? Groups.player.size() : 0;
                int maxPlayers = config.server.playerLimit > 0 ? config.server.playerLimit : tmpl.defaultMaxPlayers();
                String desc = "";
                try {
                    if (Administration.Config.desc != null && Administration.Config.desc.string() != null && !"off".equalsIgnoreCase(Administration.Config.desc.string())) {
                        desc = Administration.Config.desc.string().trim();
                    }
                } catch (Throwable ignored) {
                }
                String mapName = (Vars.state != null && Vars.state.map != null) ? Vars.state.map.plainName() : "-";
                Integer wave = (Vars.state != null && Vars.state.rules != null && Vars.state.rules.waves) ? Vars.state.wave : null;
                String mode = (Vars.state != null && Vars.state.rules != null)
                        ? (Vars.state.rules.modeName != null && !Vars.state.rules.modeName.isEmpty() ? Vars.state.rules.modeName : Vars.state.rules.mode().name())
                        : "";
                int tps = Core.graphics != null ? Core.graphics.getFramesPerSecond() : 60;
                list.add(new ServerStatus(tmpl, onlinePlayers, maxPlayers, true, true, desc, mapName, wave, mode, tps, 0, "play.xcore.top", now));
            } else {
                HeartbeatEntry hb = heartbeats.get(tmpl.id());
                boolean isOnline = hb != null && (now - hb.receivedAtMs() < HEARTBEAT_EXPIRATION_MS);
                if (!isOnline) {
                    // Filter out offline/inactive servers so they do not clutter the browser
                    continue;
                }
                int onlinePlayers = hb.players();
                int maxPlayers = hb.maxPlayers() > 0 ? hb.maxPlayers() : tmpl.defaultMaxPlayers();
                String host = (hb.host() != null && !hb.host().isBlank()) ? hb.host() : "play.xcore.top";
                int tps = hb.tps() > 0 ? hb.tps() : 60;
                int pingMs = hb.pingMs() > 0 ? hb.pingMs() : 35;
                list.add(new ServerStatus(tmpl, onlinePlayers, maxPlayers, true, false, hb.description(), hb.map(), hb.wave(), hb.mode(), tps, pingMs, host, hb.receivedAtMs()));
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
        return snapshot().size();
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
