package org.xcore.protocol.generated.shared;

public enum MetricSampleV1Type {
    COUNTER("counter"),
    GAUGE("gauge"),
    HISTOGRAM("histogram"),
    INFO("info");

    private final String value;

    MetricSampleV1Type(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static MetricSampleV1Type fromValue(String value) {
        if (value == null) {
            throw new NullPointerException("value must not be null");
        }
        for (MetricSampleV1Type candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown enum value: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
