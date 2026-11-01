package org.xcore.plugin.service;

import arc.Core;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.core.Version;
import mindustry.gen.Groups;
import mindustry.net.Administration;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.GameThread;
import org.xcore.plugin.config.TomlXcoreConfig;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import static mindustry.Vars.state;
import static org.xcore.plugin.common.PacketUtils.writeString;

@Singleton
public class ServerDiscoveryService {

    private final TomlXcoreConfig config;
    private final Async async;

    @Inject
    public ServerDiscoveryService(TomlXcoreConfig config, Async async) {
        this(config, async, REFRESH_INTERVAL_NANOS);
    }

    /** Test seam: the interval is what staleness is measured against, so a test can set it
     * to zero rather than sleeping through a real one. */
    ServerDiscoveryService(TomlXcoreConfig config, Async async, long refreshIntervalNanos) {
        this.config = config;
        this.async = async;
        this.refreshIntervalNanos = refreshIntervalNanos;
    }

    /**
     * How long a cached snapshot is served before a query triggers a refresh. A server list
     * showing a player count or wave up to this far behind is indistinguishable from one
     * taken a moment earlier, and it bounds how much game-thread work a flood can cause.
     */
    private static final long REFRESH_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(500);

    private final long refreshIntervalNanos;

    /**
     * Sampled state, read only on the game thread. Volatile rather than atomic because
     * DiscoverySnapshot is immutable: a reader either sees a whole snapshot or none, and
     * there is exactly one writer.
     */
    private volatile DiscoverySnapshot cached;
    private volatile boolean refreshPending;

    /**
     * Discovery is served on an ArcNet UDP thread. The fields a snapshot needs belong to the
     * running game — the current map can be swapped by a reload while a query is in flight,
     * and {@code Groups.player.count} walks a list the tick loop is mutating — so they are
     * sampled on the game thread.
     *
     * <p>A query is answered from the cached snapshot and never waits for the game thread.
     * Posting a task per query would make the discovery endpoint a lever on the tick loop:
     * discovery is unauthenticated UDP, so anyone could queue thousands of tasks per second,
     * each walking the full player list. Sampling periodically instead means the work is
     * bounded by elapsed time rather than by request volume, and a flood costs the game
     * thread one task per interval.
     *
     * <p>The first query after startup has nothing to answer from and takes the original
     * path: it samples, then replies. That is once per server lifetime.
     *
     * <p>Staleness costs a stale number, never a torn one. A snapshot is always a
     * consistent capture of a moment, because it is assembled on the game thread and
     * published whole.
     */
    public void handleDiscovery(ByteBuffer buffer, Runnable respond) {
        DiscoverySnapshot snapshot = cached;
        if (snapshot == null) {
            refreshThenRespond(buffer, respond);
            return;
        }

        // Answer from what we already have, then arrange for the next query to see fresher
        // data. Refreshing after responding rather than before means the querier is never
        // made to wait for the game loop.
        writeSnapshot(buffer, snapshot);
        respond.run();

        if (isStale(snapshot)) {
            requestRefresh();
        }
    }

    private void refreshThenRespond(ByteBuffer buffer, Runnable respond) {
        async.main(() -> {
            DiscoverySnapshot snapshot = capture();
            cached = snapshot;
            writeSnapshot(buffer, snapshot);
            respond.run();
        });
    }

    /**
     * Schedules at most one refresh per interval regardless of how many queries arrive. The
     * in-flight flag is cleared inside the task rather than before it, so a refresh that
     * fails to schedule cannot wedge the cache permanently.
     */
    private void requestRefresh() {
        if (refreshPending) {
            return;
        }
        refreshPending = true;
        try {
            async.main(() -> {
                try {
                    cached = capture();
                } finally {
                    refreshPending = false;
                }
            });
        } catch (RuntimeException | Error ex) {
            refreshPending = false;
            throw ex;
        }
    }

    private boolean isStale(DiscoverySnapshot snapshot) {
        return System.nanoTime() - snapshot.capturedAtNanos() >= refreshIntervalNanos;
    }

    private DiscoverySnapshot capture() {
        GameThread.report("ServerDiscoveryService.capture");
        var map = state.map;
        return new DiscoverySnapshot(
                Administration.Config.serverName.string(),
                Administration.Config.desc.string().equals("off") ? "" : Administration.Config.desc.string(),
                // state.map is null until the world finishes loading on a dedicated server.
                map == null ? "" : map.name(),
                Core.settings.getInt("totalPlayers", Groups.player.size()),
                state.wave,
                state.rules.mode().ordinal(),
                config.server.playerLimit > 0 ? config.server.playerLimit + Groups.player.count(player -> player.admin) : 0,
                state.rules.modeName,
                System.nanoTime()
        );
    }

    private void writeSnapshot(ByteBuffer buffer, DiscoverySnapshot snapshot) {
        writeString(buffer, snapshot.serverName(), 100);
        writeString(buffer, snapshot.map(), 64);

        buffer.putInt(snapshot.totalPlayers());
        buffer.putInt(snapshot.wave());
        buffer.putInt(Version.build);
        writeString(buffer, Version.type);

        buffer.put((byte) snapshot.mode());
        buffer.putInt(snapshot.playerLimit());

        writeString(buffer, snapshot.description(), 200);
        if (snapshot.modeName != null) {
            writeString(buffer, snapshot.modeName, 50);
        }

        buffer.position(0);
    }

    private record DiscoverySnapshot(
            String serverName,
            String description,
            String map,
            int totalPlayers,
            int wave,
            int mode,
            int playerLimit,
            String modeName,
            long capturedAtNanos
    ) {
    }
}
