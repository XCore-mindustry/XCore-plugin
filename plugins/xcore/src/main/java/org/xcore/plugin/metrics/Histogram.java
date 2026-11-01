package org.xcore.plugin.metrics;

public interface Histogram {
    void observe(double value);
}
