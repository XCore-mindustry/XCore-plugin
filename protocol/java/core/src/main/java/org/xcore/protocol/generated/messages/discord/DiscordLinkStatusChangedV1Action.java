package org.xcore.protocol.generated.messages.discord;

public enum DiscordLinkStatusChangedV1Action {
    LINKED("linked"),
    UNLINKED("unlinked");

    private final String value;

    DiscordLinkStatusChangedV1Action(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static DiscordLinkStatusChangedV1Action fromValue(String value) {
        if (value == null) {
            throw new NullPointerException("value must not be null");
        }
        for (DiscordLinkStatusChangedV1Action candidate : values()) {
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
