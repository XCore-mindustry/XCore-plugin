package org.xcore.plugin.metrics;

import arc.Events;
import arc.util.Timer;
import io.avaje.inject.PostConstruct;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Groups;
import org.xcore.plugin.config.TomlXcoreConfig;

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
    private final LongSupplier nowSupplier;

    private Timer.Task playersOnlineTask;

    public MainThreadMetricSampler(MetricsService metricsService, TomlXcoreConfig config) {
        this(metricsService, config, System.currentTimeMillis(), System::currentTimeMillis);
    }

    MainThreadMetricSampler(MetricsService metricsService,
                            TomlXcoreConfig config,
                            long startTimeUnixMs,
                            LongSupplier nowSupplier) {
        this.config = config;
        this.playerJoinsCounter = metricsService.counter(XcoreMetrics.PLAYER_JOINS_TOTAL);
        this.playerLeavesCounter = metricsService.counter(XcoreMetrics.PLAYER_LEAVES_TOTAL);
        this.playersOnlineGauge = metricsService.gauge(XcoreMetrics.PLAYERS_ONLINE);
        this.waveGauge = metricsService.gauge(XcoreMetrics.WAVE);
        this.tpsGauge = metricsService.gauge(XcoreMetrics.TPS);
        this.pluginUptimeGauge = metricsService.gauge(XcoreMetrics.PLUGIN_UPTIME_SECONDS);
        this.startTimeUnixMs = startTimeUnixMs;
        this.nowSupplier = nowSupplier;
    }

    @PostConstruct
    public void init() {
        if (!config.telemetry.enabled) {
            return;
        }

        Events.on(EventType.PlayerJoin.class, ignored -> playerJoinsCounter.increment());
        Events.on(EventType.PlayerLeave.class, ignored -> playerLeavesCounter.increment());
        Events.on(EventType.ServerLoadEvent.class, ignored -> startPlayersOnlineSampling());
    }

    @PreDestroy
    public void shutdown() {
        if (playersOnlineTask != null) {
            playersOnlineTask.cancel();
            playersOnlineTask = null;
        }
    }

    private void startPlayersOnlineSampling() {
        samplePeriodicMetrics();
        if (playersOnlineTask != null) {
            playersOnlineTask.cancel();
        }

        float intervalSeconds = config.telemetry.sampleIntervalMs / 1000f;
        playersOnlineTask = Timer.schedule(this::samplePeriodicMetrics, intervalSeconds, intervalSeconds);
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
        tpsGauge.set(Vars.state == null ? 0 : Math.max(0, Vars.state.serverTps));
    }

    void samplePluginUptime() {
        pluginUptimeGauge.set(Math.max(0d, (nowSupplier.getAsLong() - startTimeUnixMs) / 1000d));
    }
}
