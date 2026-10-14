package org.xcore.plugin.cases;

import io.avaje.inject.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.concurrent.GameThread;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.model.PlayerPids;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.startup.ShutdownOrder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.LongSupplier;

@Singleton
public class RecentPlayers {
    static final int CAPACITY = 512;
    private final CasesFeature feature;
    private final TomlSecretsConfig secrets;
    private final LongSupplier clock;
    private final LinkedHashMap<String, LeftPlayer> players = new LinkedHashMap<>();
    private volatile boolean stopped;

    @Inject
    public RecentPlayers(CasesFeature feature, TomlSecretsConfig secrets) {
        this(feature, secrets, System::currentTimeMillis);
    }

    RecentPlayers(CasesFeature feature, TomlSecretsConfig secrets, LongSupplier clock) {
        this.feature = feature;
        this.secrets = secrets;
        this.clock = clock;
    }

    /** Call before logout removes the active session, while staff permissions still apply. */
    public synchronized void left(Session session, boolean staff) {
        GameThread.require("RecentPlayers.left");
        if (!active() || session == null || session.data == null || session.player == null) return;
        remember(session.data.uuid, session.data.pid, session.player.plainName(), session.player.ip(), staff);
    }

    synchronized void remember(String uuid, int pid, String name, String address, boolean staff) {
        GameThread.require("RecentPlayers.remember");
        if (!active()) return;
        prune();
        players.remove(uuid);
        if (players.size() == CAPACITY) players.remove(players.keySet().iterator().next());
        players.put(uuid, new LeftPlayer(new ReportTarget(uuid, PlayerPids.orNull(pid),
                CaseText.name(name), false, clock.getAsLong()), address, staff));
    }

    public synchronized void joined(String uuid) {
        GameThread.require("RecentPlayers.joined");
        players.remove(uuid);
    }

    synchronized List<LeftPlayer> snapshot() {
        GameThread.require("RecentPlayers.snapshot");
        if (!active()) return List.of();
        prune();
        return List.copyOf(players.values());
    }

    synchronized LeftPlayer find(String uuid) {
        return snapshot().stream().filter(value -> value.target().uuid().equals(uuid)).findFirst().orElse(null);
    }

    private boolean active() {
        if (stopped || !feature.enabled()) {
            players.clear();
            return false;
        }
        return true;
    }

    private void prune() {
        long since = clock.getAsLong() - secrets.cases.reports.recentLeaverMinutes * 60_000L;
        players.values().removeIf(value -> value.target().leftAt() <= since);
    }

    @PreDestroy(priority = ShutdownOrder.STOP_PRODUCERS)
    public synchronized void stop() {
        stopped = true;
        players.clear();
    }

    record LeftPlayer(ReportTarget target, String address, boolean staff) {}
}
