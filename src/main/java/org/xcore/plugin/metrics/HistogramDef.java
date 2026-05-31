package org.xcore.plugin.metrics;

import java.util.Arrays;

public record HistogramDef(
        String name,
        String help,
        String unit,
        LabelSchema labels,
        double[] buckets
) implements MetricDef {
    public HistogramDef {
        MetricValidation.validateCommon(name, help, unit, labels);
        if (buckets == null || buckets.length == 0) {
            throw new IllegalArgumentException("Histogram buckets must not be empty");
        }
        buckets = Arrays.copyOf(buckets, buckets.length);
        double previous = Double.NEGATIVE_INFINITY;
        for (double bucket : buckets) {
            if (!Double.isFinite(bucket)) {
                throw new IllegalArgumentException("Histogram buckets must be finite");
            }
            if (bucket <= previous) {
                throw new IllegalArgumentException("Histogram buckets must be strictly increasing");
            }
            previous = bucket;
        }
    }
}
