package org.xcore.plugin.metrics;

import arc.Core;
import io.avaje.inject.PostConstruct;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Groups;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.concurrent.ScheduledTasks;
import org.xcore.plugin.event.EventSubscriptions;
import org.xcore.plugin.startup.ShutdownOrder;

import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

@Singleton
public final class MainThreadMetricSampler {
    private final TomlXcoreConfig config;
    private final Counter playerJoinsCounter;
    private final Counter playerLeavesCounter;
    private final Gauge playersOnlineGauge;
    private final Gauge waveGauge;
    private final Gauge tpsGauge;
    private final Gauge pluginUptimeGauge;
    private final long startTimeUnixMs;
    private final IntSupplier tpsSupplier;
    private final LongSupplier nowSupplier;

    private final EventSubscriptions subscriptions = new EventSubscriptions();
    private volatile boolean stopped;
    private final ScheduledTasks tasks = new ScheduledTasks(MainThreadMetricSampler.class);
    private ScheduledTasks.Handle playersOnlineTask;

    public MainThreadMetricSampler(MetricsService metricsService, TomlXcoreConfig config) {
        this(
                metricsService,
                config,
                System.currentTimeMillis(),
                () -> Core.graphics == null ? 0 : Core.graphics.getFramesPerSecond(),
                System::currentTimeMillis
        );
    }

    MainThreadMetricSampler(MetricsService metricsService,
                            TomlXcoreConfig config,
                            long startTimeUnixMs,
                            IntSupplier tpsSupplier,
                            LongSupplier nowSupplier) {
        this.config = config;
        this.playerJoinsCounter = metricsService.counter(XcoreMetrics.PLAYER_JOINS_TOTAL);
        this.playerLeavesCounter = metricsService.counter(XcoreMetrics.PLAYER_LEAVES_TOTAL);
        this.playersOnlineGauge = metricsService.gauge(XcoreMetrics.PLAYERS_ONLINE);
        this.waveGauge = metricsService.gauge(XcoreMetrics.WAVE);
        this.tpsGauge = metricsService.gauge(XcoreMetrics.TPS);
        this.pluginUptimeGauge = metricsService.gauge(XcoreMetrics.PLUGIN_UPTIME_SECONDS);
        this.startTimeUnixMs = startTimeUnixMs;
        this.tpsSupplier = tpsSupplier;
        this.nowSupplier = nowSupplier;
    }

    @PostConstruct
    public void init() {
        if (!config.telemetry.enabled) {
            return;
        }

        subscriptions.on(EventType.PlayerJoin.class, ignored -> playerJoinsCounter.increment());
        subscriptions.on(EventType.PlayerLeave.class, ignored -> playerLeavesCounter.increment());
        subscriptions.on(EventType.ServerLoadEvent.class, ignored -> startPlayersOnlineSampling());
    }

    @PreDestroy(priority = ShutdownOrder.STOP_PRODUCERS)
    public synchronized void shutdown() {
        stopped = true;
        subscriptions.close();
        tasks.close();
        playersOnlineTask = null;
    }

    private synchronized void startPlayersOnlineSampling() {
        if (stopped) return;
        samplePeriodicMetrics();
        if (playersOnlineTask != null) {
            playersOnlineTask.cancel();
        }

        float intervalSeconds = config.telemetry.sampleIntervalMs / 1000f;
        playersOnlineTask = tasks.every(intervalSeconds, intervalSeconds, this::samplePeriodicMetrics);
    }

    void samplePeriodicMetrics() {
        samplePlayersOnline();
        sampleWave();
        sampleTps();
        samplePluginUptime();
    }

    void samplePlayersOnline() {
        playersOnlineGauge.set(Groups.player == null ? 0 : Groups.player.size());
    }

    void sampleWave() {
        waveGauge.set(Vars.state == null ? 0 : Vars.state.wave);
    }

    void sampleTps() {
        tpsGauge.set(Math.max(0, tpsSupplier.getAsInt()));
    }

    void samplePluginUptime() {
        pluginUptimeGauge.set(Math.max(0d, (nowSupplier.getAsLong() - startTimeUnixMs) / 1000d));
    }
}
