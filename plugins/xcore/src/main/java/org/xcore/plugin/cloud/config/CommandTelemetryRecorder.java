package org.xcore.plugin.cloud.config;

import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.metrics.Counter;
import org.xcore.plugin.metrics.Histogram;
import org.xcore.plugin.metrics.MetricsService;
import org.xcore.plugin.metrics.Tags;
import org.xcore.plugin.metrics.XcoreMetrics;

final class CommandTelemetryRecorder {
    private CommandTelemetryRecorder() {
    }

    static void record(MetricsService metrics,
                       XCoreSender sender,
                       String commandName,
                       String result,
                       double durationSeconds) {
        if (commandName == null || commandName.isBlank()) {
            return;
        }

        String source = sender.isPlayer() ? "player" : "server";

        Counter counter = metrics.counter(XcoreMetrics.COMMANDS_TOTAL, Tags.of(
                "command", commandName,
                "source", source,
                "result", result
        ));
        counter.increment();

        Histogram histogram = metrics.histogram(XcoreMetrics.COMMAND_DURATION_SECONDS, Tags.of(
                "command", commandName,
                "source", source
        ));
        histogram.observe(Math.max(0.0d, durationSeconds));
    }
}
