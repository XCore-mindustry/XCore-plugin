package org.xcore.protocol.generated.messages.discord;

public enum DiscordAccountOperationRequestV1Action {
    LINK("link"),
    UNLINK("unlink");

    private final String value;

    DiscordAccountOperationRequestV1Action(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static DiscordAccountOperationRequestV1Action fromValue(String value) {
        if (value == null) {
            throw new NullPointerException("value must not be null");
        }
        for (DiscordAccountOperationRequestV1Action candidate : values()) {
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
