package org.xcore.protocol.generated.shared;

public enum MapUploadResultV1Status {
    INSTALLED("installed"),
    DOWNLOAD_FAILED("download_failed"),
    INVALID_MAP("invalid_map"),
    SUPERSEDED("superseded"),
    METADATA_FAILED("metadata_failed"),
    REJECTED("rejected");

    private final String value;

    MapUploadResultV1Status(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static MapUploadResultV1Status fromValue(String value) {
        if (value == null) {
            throw new NullPointerException("value must not be null");
        }
        for (MapUploadResultV1Status candidate : values()) {
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
