package org.xcore.protocol.generated.messages.sentinel;

public enum SentinelSubnetRulesCommandV1Operation {
    ALLOW("ALLOW"),
    DENY("DENY"),
    REMOVE("REMOVE"),
    IMPORT("IMPORT"),
    RELOAD("RELOAD");

    private final String value;

    SentinelSubnetRulesCommandV1Operation(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static SentinelSubnetRulesCommandV1Operation fromValue(String value) {
        if (value == null) {
            throw new NullPointerException("value must not be null");
        }
        for (SentinelSubnetRulesCommandV1Operation candidate : values()) {
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
