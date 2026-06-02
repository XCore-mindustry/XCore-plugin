package org.xcore.plugin.metrics;

import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.entities.EntityGroup;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.protocol.generated.shared.MetricSampleV1;
import org.xcore.plugin.config.TomlXcoreConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MainThreadMetricSamplerTest {
    private GameState previousState;
    private EntityGroup<Player> previousPlayers;

    @BeforeEach
    void setUp() {
        previousState = Vars.state;
        previousPlayers = Groups.player;
        Vars.state = new GameState();
        Groups.player = new EntityGroup<>(Player.class, false, false);
    }

    @AfterEach
    void tearDown() {
        Vars.state = previousState;
        Groups.player = previousPlayers;
    }

    @Test
    @DisplayName("samplePeriodicMetrics exports online players, wave, and plugin uptime")
    void samplePeriodicMetrics_exportsOnlinePlayersWaveAndPluginUptime() {
        Vars.state.wave = 17;
        Groups.player.add(mock(Player.class));
        Groups.player.add(mock(Player.class));

        LocalMetricRegistry registry = new LocalMetricRegistry();
        MainThreadMetricSampler sampler = new MainThreadMetricSampler(
                enabledMetricsService(registry),
                enabledConfig(),
                1_000L,
                () -> 58,
                () -> 16_000L
        );

        sampler.samplePeriodicMetrics();

        List<MetricSampleV1> samples = registry.snapshot();
        assertThat(sampleByName(samples, XcoreMetrics.PLAYERS_ONLINE.name()).value()).isEqualTo(2d);
        assertThat(sampleByName(samples, XcoreMetrics.WAVE.name()).value()).isEqualTo(17d);
        assertThat(sampleByName(samples, XcoreMetrics.TPS.name()).value()).isEqualTo(58d);
        assertThat(sampleByName(samples, XcoreMetrics.PLUGIN_UPTIME_SECONDS.name()).value()).isEqualTo(15d);
    }

    @Test
    @DisplayName("samplePeriodicMetrics falls back to zero when state or players are unavailable")
    void samplePeriodicMetrics_fallsBackToZeroWhenStateOrPlayersUnavailable() {
        Vars.state = null;
        Groups.player = null;

        LocalMetricRegistry registry = new LocalMetricRegistry();
        MainThreadMetricSampler sampler = new MainThreadMetricSampler(
                enabledMetricsService(registry),
                enabledConfig(),
                1_000L,
                () -> 0,
                () -> 500L
        );

        sampler.samplePeriodicMetrics();

        List<MetricSampleV1> samples = registry.snapshot();
        assertThat(sampleByName(samples, XcoreMetrics.PLAYERS_ONLINE.name()).value()).isEqualTo(0d);
        assertThat(sampleByName(samples, XcoreMetrics.WAVE.name()).value()).isEqualTo(0d);
        assertThat(sampleByName(samples, XcoreMetrics.TPS.name()).value()).isEqualTo(0d);
        assertThat(sampleByName(samples, XcoreMetrics.PLUGIN_UPTIME_SECONDS.name()).value()).isEqualTo(0d);
    }

    private static DefaultMetricsService enabledMetricsService(LocalMetricRegistry registry) {
        return new DefaultMetricsService(registry, enabledConfig());
    }

    private static TomlXcoreConfig enabledConfig() {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.telemetry.enabled = true;
        return config;
    }

    private static MetricSampleV1 sampleByName(List<MetricSampleV1> samples, String name) {
        return samples.stream()
                .filter(sample -> sample.name().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
