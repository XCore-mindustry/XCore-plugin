package org.xcore.protocol.generated.envelopes;

public enum RpcResponseEnvelopeV1Status {
    OK("ok"),
    ERROR("error");

    private final String value;

    RpcResponseEnvelopeV1Status(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static RpcResponseEnvelopeV1Status fromValue(String value) {
        if (value == null) {
            throw new NullPointerException("value must not be null");
        }
        for (RpcResponseEnvelopeV1Status candidate : values()) {
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
