package org.xcore.protocol.generated.shared;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;

public record MetricSampleV1(
        String name,
        MetricSampleV1Type type,
        Map<String, Object> labels,
        String help,
        String unit,
        Double value,
        List<Double> buckets,
        List<Integer> counts,
        Long count,
        Double sum
) implements ProtocolPayload {
    public MetricSampleV1 {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
        labels = Objects.requireNonNull(labels, "labels must not be null");
        for (Map.Entry<String, Object> entry : labels.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "labels keys must not be null");
            Objects.requireNonNull(entry.getValue(), "labels values must not be null");
            if (!(entry.getValue() instanceof String)) {
                throw new IllegalArgumentException("labels values must be one of: string");
            }
        }
        labels = Map.copyOf(labels);
        if (help != null) {
            Objects.requireNonNull(help, "help must not be null");
            if (help.length() < 1) {
                throw new IllegalArgumentException("help must be at least 1 characters");
            }
        }
        if (unit != null) {
            Objects.requireNonNull(unit, "unit must not be null");
            if (unit.length() < 1) {
                throw new IllegalArgumentException("unit must be at least 1 characters");
            }
        }
        if (buckets != null) {
            buckets = Objects.requireNonNull(buckets, "buckets must not be null");
            buckets = List.copyOf(buckets);
            for (Double item : buckets) {
            }
        }
        if (counts != null) {
            counts = Objects.requireNonNull(counts, "counts must not be null");
            counts = List.copyOf(counts);
            for (Integer item : counts) {
            }
        }
        if (count != null) {
            if (count < 0) {
                throw new IllegalArgumentException("count must be >= 0");
            }
        }
    }

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        if (type != null) {
            payload.put("type", type.toString());
        }
        payload.put("labels", java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(labels)));
        if (help != null) {
            payload.put("help", help);
        }
        if (unit != null) {
            payload.put("unit", unit);
        }
        if (value != null) {
            payload.put("value", value);
        }
        if (buckets != null) {
            payload.put("buckets", List.copyOf(buckets));
        }
        if (counts != null) {
            payload.put("counts", List.copyOf(counts));
        }
        if (count != null) {
            payload.put("count", count);
        }
        if (sum != null) {
            payload.put("sum", sum);
        }
        return payload;
    }
}
