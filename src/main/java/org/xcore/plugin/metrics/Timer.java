package org.xcore.plugin.metrics;

public interface Timer {
    interface Sample {
        void stop(Histogram histogram);
    }
}
