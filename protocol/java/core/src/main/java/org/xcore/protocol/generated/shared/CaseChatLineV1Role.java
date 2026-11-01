package org.xcore.protocol.generated.shared;

public enum CaseChatLineV1Role {
    TARGET("target"),
    REPORTER("reporter"),
    OTHER("other");

    private final String value;

    CaseChatLineV1Role(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static CaseChatLineV1Role fromValue(String value) {
        if (value == null) {
            throw new NullPointerException("value must not be null");
        }
        for (CaseChatLineV1Role candidate : values()) {
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
