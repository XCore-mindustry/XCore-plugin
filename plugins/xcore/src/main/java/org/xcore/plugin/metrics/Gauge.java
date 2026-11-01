package org.xcore.plugin.metrics;

public interface Gauge {
    void set(long value);

    void set(double value);
}
