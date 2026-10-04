package org.xcore.protocol.generated.shared;

public enum SeasonPrizeV1Kind {
    BADGE("badge"),
    CUSTOM("custom");

    private final String value;

    SeasonPrizeV1Kind(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static SeasonPrizeV1Kind fromValue(String value) {
        if (value == null) {
            throw new NullPointerException("value must not be null");
        }
        for (SeasonPrizeV1Kind candidate : values()) {
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
