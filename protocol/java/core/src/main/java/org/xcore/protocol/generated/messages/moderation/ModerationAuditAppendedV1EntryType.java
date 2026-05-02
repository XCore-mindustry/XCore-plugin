package org.xcore.protocol.generated.messages.moderation;

public enum ModerationAuditAppendedV1EntryType {
    BAN("ban"),
    MUTE("mute"),
    VOTE_KICK("voteKick"),
    PARDON("pardon"),
    OTHER("other");

    private final String value;

    ModerationAuditAppendedV1EntryType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ModerationAuditAppendedV1EntryType fromValue(String value) {
        if (value == null) {
            throw new NullPointerException("value must not be null");
        }
        for (ModerationAuditAppendedV1EntryType candidate : values()) {
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
