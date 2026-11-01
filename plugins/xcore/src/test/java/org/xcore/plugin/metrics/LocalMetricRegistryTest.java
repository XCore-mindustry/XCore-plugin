package org.xcore.plugin.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.protocol.generated.shared.MetricSampleV1Type;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalMetricRegistryTest {

    @Test
    @DisplayName("snapshot exports counter, gauge, and cumulative histogram samples")
    void snapshot_exportsCounterGaugeAndHistogramSamples() {
        LocalMetricRegistry registry = new LocalMetricRegistry();

        Counter counter = registry.counter(XcoreMetrics.PLAYER_JOINS_TOTAL, Tags.empty());
        Gauge gauge = registry.gauge(XcoreMetrics.PLAYERS_ONLINE, Tags.empty());
        Histogram histogram = registry.histogram(new HistogramDef(
                "xcore_command_duration_seconds",
                "Command duration",
                "seconds",
                LabelSchema.empty(),
                new double[]{0.1d, 0.5d, 1.0d}
        ), Tags.empty());

        counter.add(3);
        gauge.set(12);
        histogram.observe(0.05d);
        histogram.observe(0.4d);
        histogram.observe(0.8d);
        histogram.observe(4.2d);

        var samples = registry.snapshot();

        assertThat(samples).hasSize(3);

        var joinSample = samples.stream().filter(sample -> sample.name().equals(XcoreMetrics.PLAYER_JOINS_TOTAL.name())).findFirst().orElseThrow();
        assertThat(joinSample.type()).isEqualTo(MetricSampleV1Type.COUNTER);
        assertThat(joinSample.value()).isEqualTo(3d);

        var onlineSample = samples.stream().filter(sample -> sample.name().equals(XcoreMetrics.PLAYERS_ONLINE.name())).findFirst().orElseThrow();
        assertThat(onlineSample.type()).isEqualTo(MetricSampleV1Type.GAUGE);
        assertThat(onlineSample.value()).isEqualTo(12d);

        var histogramSample = samples.stream().filter(sample -> sample.name().equals("xcore_command_duration_seconds")).findFirst().orElseThrow();
        assertThat(histogramSample.type()).isEqualTo(MetricSampleV1Type.HISTOGRAM);
        assertThat(histogramSample.buckets()).containsExactly(0.1d, 0.5d, 1.0d);
        assertThat(histogramSample.counts()).containsExactly(1, 2, 3);
        assertThat(histogramSample.count()).isEqualTo(4L);
        assertThat(histogramSample.sum()).isEqualTo(5.45d);
    }

    @Test
    @DisplayName("label schema rejects reserved server label")
    void labelSchema_rejectsReservedServerLabel() {
        assertThatThrownBy(() -> LabelSchema.of("server"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved label");
    }
}
