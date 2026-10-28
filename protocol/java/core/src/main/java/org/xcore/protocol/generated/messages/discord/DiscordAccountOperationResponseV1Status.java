package org.xcore.protocol.generated.messages.discord;

public enum DiscordAccountOperationResponseV1Status {
    SUCCESS("success"),
    REJECTED("rejected"),
    FAILED("failed"),
    UNKNOWN("unknown");

    private final String value;

    DiscordAccountOperationResponseV1Status(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static DiscordAccountOperationResponseV1Status fromValue(String value) {
        if (value == null) {
            throw new NullPointerException("value must not be null");
        }
        for (DiscordAccountOperationResponseV1Status candidate : values()) {
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
