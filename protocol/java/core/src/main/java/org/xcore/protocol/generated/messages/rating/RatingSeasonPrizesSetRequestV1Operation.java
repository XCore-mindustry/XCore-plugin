package org.xcore.protocol.generated.messages.rating;

public enum RatingSeasonPrizesSetRequestV1Operation {
    ADD("add"),
    REMOVE("remove");

    private final String value;

    RatingSeasonPrizesSetRequestV1Operation(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static RatingSeasonPrizesSetRequestV1Operation fromValue(String value) {
        if (value == null) {
            throw new NullPointerException("value must not be null");
        }
        for (RatingSeasonPrizesSetRequestV1Operation candidate : values()) {
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
