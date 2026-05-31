package org.xcore.plugin.metrics;

import arc.Events;
import arc.util.Timer;
import io.avaje.inject.PostConstruct;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Singleton;
import mindustry.game.EventType;
import mindustry.gen.Groups;
import org.xcore.plugin.config.TomlXcoreConfig;

@Singleton
public final class MainThreadMetricSampler {
    private final TomlXcoreConfig config;
    private final Counter playerJoinsCounter;
    private final Counter playerLeavesCounter;
    private final Gauge playersOnlineGauge;

    private Timer.Task playersOnlineTask;

    public MainThreadMetricSampler(MetricsService metricsService, TomlXcoreConfig config) {
        this.config = config;
        this.playerJoinsCounter = metricsService.counter(XcoreMetrics.PLAYER_JOINS_TOTAL);
        this.playerLeavesCounter = metricsService.counter(XcoreMetrics.PLAYER_LEAVES_TOTAL);
        this.playersOnlineGauge = metricsService.gauge(XcoreMetrics.PLAYERS_ONLINE);
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
        samplePlayersOnline();
        if (playersOnlineTask != null) {
            playersOnlineTask.cancel();
        }

        float intervalSeconds = config.telemetry.sampleIntervalMs / 1000f;
        playersOnlineTask = Timer.schedule(this::samplePlayersOnline, intervalSeconds, intervalSeconds);
    }

    private void samplePlayersOnline() {
        playersOnlineGauge.set(Groups.player.size());
    }
}
