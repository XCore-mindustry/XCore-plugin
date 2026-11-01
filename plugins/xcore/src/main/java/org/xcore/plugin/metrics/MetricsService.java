package org.xcore.plugin.metrics;

public interface MetricsService {
    Counter counter(CounterDef def);

    Counter counter(CounterDef def, Tags tags);

    Gauge gauge(GaugeDef def);

    Gauge gauge(GaugeDef def, Tags tags);

    Histogram histogram(HistogramDef def);

    Histogram histogram(HistogramDef def, Tags tags);

    void increment(CounterDef def);

    void increment(CounterDef def, Tags tags);

    Timer.Sample startTimer();
}
