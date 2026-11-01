package org.xcore.protocol.generated.envelopes;

public enum EnvelopeBaseV1Message_kind {
    EVENT("event"),
    COMMAND("command"),
    RPC_REQUEST("rpc_request"),
    RPC_RESPONSE("rpc_response"),
    DLQ("dlq");

    private final String value;

    EnvelopeBaseV1Message_kind(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static EnvelopeBaseV1Message_kind fromValue(String value) {
        if (value == null) {
            throw new NullPointerException("value must not be null");
        }
        for (EnvelopeBaseV1Message_kind candidate : values()) {
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
