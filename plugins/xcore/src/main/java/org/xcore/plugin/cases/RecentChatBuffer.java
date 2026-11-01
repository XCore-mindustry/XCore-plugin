package org.xcore.plugin.cases;

import io.avaje.inject.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.concurrent.GameThread;
import org.xcore.plugin.model.PlayerPids;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.startup.ShutdownOrder;
import org.xcore.protocol.generated.shared.CaseChatLineV1;
import org.xcore.protocol.generated.shared.CaseChatLineV1Role;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

@Singleton
public class RecentChatBuffer {
    static final int CAPACITY = 200;
    static final long RETENTION_MILLIS = 15 * 60_000L;
    private final CasesFeature feature;
    private final SessionService sessions;
    private final LongSupplier clock;
    private final ArrayDeque<Line> lines = new ArrayDeque<>();
    private volatile boolean stopped;
    private long nextOrder;

    @Inject
    public RecentChatBuffer(CasesFeature feature, SessionService sessions) {
        this(feature, sessions, System::currentTimeMillis);
    }

    RecentChatBuffer(CasesFeature feature, SessionService sessions, LongSupplier clock) {
        this.feature = feature;
        this.sessions = sessions;
        this.clock = clock;
    }

    public synchronized void record(Player player, String text, boolean team) {
        GameThread.require("RecentChatBuffer.record");
        if (!active()) return;
        var session = sessions.get(player);
        append(player.uuid(), session != null && session.data != null ? session.data.pid : null,
                player.plainName(), text, team);
    }

    synchronized void append(String uuid, Integer pid, String name, String text, boolean team) {
        GameThread.require("RecentChatBuffer.append");
        if (!active() || text == null || text.stripLeading().startsWith("/")) return;
        prune();
        if (lines.size() == CAPACITY) lines.removeFirst();
        lines.addLast(new Line(clock.getAsLong(), nextOrder++, uuid, PlayerPids.orNull(pid),
                CaseText.name(name), CaseText.plain(text, 256), team));
    }

    public synchronized List<CaseChatLineV1> excerpt(String targetUuid, String reporterUuid, int limit, int windowSeconds) {
        GameThread.require("RecentChatBuffer.excerpt");
        if (!active()) return List.of();
        prune();
        int count = Math.clamp(limit, 0, 30);
        long since = clock.getAsLong() - Math.clamp(windowSeconds, 0, 900) * 1000L;
        var recent = lines.stream().filter(line -> line.at() >= since).toList().reversed();
        var selected = new ArrayList<Line>();
        for (Line line : recent) {
            if (Objects.equals(line.uuid(), targetUuid) && selected.size() < Math.min(count, 20)) selected.add(line);
        }
        for (Line line : recent) {
            if (!Objects.equals(line.uuid(), targetUuid) && selected.size() < count) selected.add(line);
        }
        selected.sort(Comparator.comparingLong(Line::at).thenComparingLong(Line::order));
        return selected.stream().map(line -> new CaseChatLineV1(line.at(), line.name(), line.pid(),
                Objects.equals(line.uuid(), targetUuid) ? CaseChatLineV1Role.TARGET
                        : Objects.equals(line.uuid(), reporterUuid) ? CaseChatLineV1Role.REPORTER : CaseChatLineV1Role.OTHER,
                line.text(), line.team())).toList();
    }

    private boolean active() {
        if (stopped || !feature.enabled()) {
            lines.clear();
            return false;
        }
        return true;
    }

    private void prune() {
        long since = clock.getAsLong() - RETENTION_MILLIS;
        while (!lines.isEmpty() && lines.peekFirst().at() < since) lines.removeFirst();
    }

    @PreDestroy(priority = ShutdownOrder.STOP_PRODUCERS)
    public synchronized void stop() {
        stopped = true;
        lines.clear();
    }

    private record Line(long at, long order, String uuid, Integer pid, String name, String text, boolean team) {}
}
