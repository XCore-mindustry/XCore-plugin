package org.xcore.cloud.mindustry.selector.exception;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.caption.Caption;
import org.incendo.cloud.caption.CaptionVariable;
import org.xcore.cloud.mindustry.selector.SelectorKind;
import org.xcore.cloud.mindustry.selector.caption.SelectorCaptionKeys;

public class SelectorSenderRequirementException extends SelectorException {
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

    @Override
    public @NonNull Caption caption() {
        return SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_SENDER_REQUIRED;
    }

    @Override
    public @NonNull CaptionVariable @NonNull [] captionVariables() {
        return new CaptionVariable[]{CaptionVariable.of("kind", kind.token())};
    }
}
