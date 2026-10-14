package org.xcore.protocol.generated.messages.maps;

public enum MapsInspectResponseV1Status {
    OK("ok"),
    DOWNLOAD_FAILED("download_failed"),
    INVALID_MAP("invalid_map");

    private final String value;

    MapsInspectResponseV1Status(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static MapsInspectResponseV1Status fromValue(String value) {
        if (value == null) {
            throw new NullPointerException("value must not be null");
        }
        for (MapsInspectResponseV1Status candidate : values()) {
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
