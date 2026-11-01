package org.xcore.protocol.generated.messages.rating;

public enum RatingSeasonRescheduleRequestV1Operation {
    EXTEND("extend"),
    SET_END("set-end"),
    END_NOW("end-now");

    private final String value;

    RatingSeasonRescheduleRequestV1Operation(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static RatingSeasonRescheduleRequestV1Operation fromValue(String value) {
        if (value == null) {
            throw new NullPointerException("value must not be null");
        }
        for (RatingSeasonRescheduleRequestV1Operation candidate : values()) {
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
