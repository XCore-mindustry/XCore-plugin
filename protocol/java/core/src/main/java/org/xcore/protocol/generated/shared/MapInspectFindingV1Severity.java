package org.xcore.protocol.generated.shared;

public enum MapInspectFindingV1Severity {
    ERROR("error"),
    WARNING("warning"),
    INFO("info");

    private final String value;

    MapInspectFindingV1Severity(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static MapInspectFindingV1Severity fromValue(String value) {
        if (value == null) {
            throw new NullPointerException("value must not be null");
        }
        for (MapInspectFindingV1Severity candidate : values()) {
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
