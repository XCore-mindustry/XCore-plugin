package org.xcore.protocol.generated.messages.rating;

public enum RatingPrizeGrantUpdateRequestV1Status {
    DELIVERED("delivered");

    private final String value;

    RatingPrizeGrantUpdateRequestV1Status(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static RatingPrizeGrantUpdateRequestV1Status fromValue(String value) {
        if (value == null) {
            throw new NullPointerException("value must not be null");
        }
        for (RatingPrizeGrantUpdateRequestV1Status candidate : values()) {
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
