package org.xcore.plugin.metrics;

public interface Counter {
    void increment();

    void add(long amount);
}
