package org.xcore.protocol.generated.shared;

public enum ActorRefV1ActorType {
    PLAYER("player"),
    DISCORD("discord"),
    SERVER("server"),
    SYSTEM("system"),
    UNKNOWN("unknown");

    private final String value;

    ActorRefV1ActorType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ActorRefV1ActorType fromValue(String value) {
        if (value == null) {
            throw new NullPointerException("value must not be null");
        }
        for (ActorRefV1ActorType candidate : values()) {
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
