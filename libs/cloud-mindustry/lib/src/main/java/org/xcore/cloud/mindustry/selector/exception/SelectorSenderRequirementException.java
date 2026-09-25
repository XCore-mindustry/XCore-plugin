package org.xcore.cloud.mindustry.selector.exception;

import org.xcore.cloud.mindustry.selector.SelectorKind;

public class SelectorSenderRequirementException extends RuntimeException {
    private final SelectorKind kind;
    private final String senderName;

    public SelectorSenderRequirementException(SelectorKind kind, String senderName, String reason) {
        super("Sender '" + senderName + "' cannot use selector '" + kind.token() + "': " + reason);
        this.kind = kind;
        this.senderName = senderName;
    }

    public SelectorKind kind() {
        return kind;
    }

    public String senderName() {
        return senderName;
    }
}
