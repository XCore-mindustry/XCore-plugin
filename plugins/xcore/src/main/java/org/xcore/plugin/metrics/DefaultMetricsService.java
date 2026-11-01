package org.xcore.plugin.metrics;

import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

@Singleton
public final class DefaultMetricsService implements MetricsService {
    private static final Counter NOOP_COUNTER = new Counter() {
        @Override
        public void increment() {
        }

        @Override
        public void add(long amount) {
        }
    };
    private static final Gauge NOOP_GAUGE = new Gauge() {
        @Override
        public void set(long value) {
        }

        @Override
        public void set(double value) {
        }
    };
    private static final Histogram NOOP_HISTOGRAM = value -> {
    };
    private static final Timer.Sample NOOP_TIMER = histogram -> {
    };

    private final LocalMetricRegistry registry;
    private final boolean enabled;

    public DefaultMetricsService(LocalMetricRegistry registry, org.xcore.plugin.config.TomlXcoreConfig config) {
        this.registry = registry;
        this.enabled = config.telemetry.enabled;
    }

    @Override
    public Counter counter(CounterDef def) {
        return counter(def, Tags.empty());
    }

    @Override
    public Counter counter(CounterDef def, Tags tags) {
        return enabled ? registry.counter(def, tags) : NOOP_COUNTER;
    }

    @Override
    public Gauge gauge(GaugeDef def) {
        return gauge(def, Tags.empty());
    }

    @Override
    public Gauge gauge(GaugeDef def, Tags tags) {
        return enabled ? registry.gauge(def, tags) : NOOP_GAUGE;
    }

    @Override
    public Histogram histogram(HistogramDef def) {
        return histogram(def, Tags.empty());
    }

    @Override
    public Histogram histogram(HistogramDef def, Tags tags) {
        return enabled ? registry.histogram(def, tags) : NOOP_HISTOGRAM;
    }

    @Override
    public void increment(CounterDef def) {
        increment(def, Tags.empty());
    }

    @Override
    public void increment(CounterDef def, Tags tags) {
        counter(def, tags).increment();
    }

    @Override
    public Timer.Sample startTimer() {
        if (!enabled) {
            return NOOP_TIMER;
        }

        long startedAt = System.nanoTime();
        return histogram -> {
            if (histogram != null) {
                histogram.observe((System.nanoTime() - startedAt) / 1_000_000_000d);
            }
        };
    }
}
