package org.xcore.plugin.metrics;

import java.util.Objects;
import java.util.regex.Pattern;

public sealed interface MetricDef permits CounterDef, GaugeDef, HistogramDef {
    String name();

    String help();

    String unit();

    LabelSchema labels();
}

final class MetricValidation {
    private static final Pattern METRIC_NAME_PATTERN = Pattern.compile("[a-zA-Z_:][a-zA-Z0-9_:]*");

    private MetricValidation() {
    }

    static void validateCommon(String name, String help, String unit, LabelSchema labels) {
        if (name == null || !METRIC_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Metric name must be Prometheus-compatible: " + name);
        }
        if (help == null || help.isBlank()) {
            throw new IllegalArgumentException("Metric help must not be blank");
        }
        Objects.requireNonNull(labels, "labels must not be null");
        if (unit != null && unit.isBlank()) {
            throw new IllegalArgumentException("Metric unit must not be blank when provided");
        }
    }
}
