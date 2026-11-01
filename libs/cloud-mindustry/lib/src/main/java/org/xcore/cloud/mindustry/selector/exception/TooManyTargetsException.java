package org.xcore.cloud.mindustry.selector.exception;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.caption.Caption;
import org.incendo.cloud.caption.CaptionVariable;
import org.xcore.cloud.mindustry.selector.caption.SelectorCaptionKeys;

public class TooManyTargetsException extends SelectorException {
    private final String selectorInput;

    public TooManyTargetsException(String selectorInput, String message) {
        super(message);
        this.selectorInput = selectorInput;
    }

    public String selectorInput() {
        return selectorInput;
    }

    @Override
    public @NonNull Caption caption() {
        return SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_TOO_MANY_TARGETS;
    }

    @Override
    public @NonNull CaptionVariable @NonNull [] captionVariables() {
        return new CaptionVariable[]{CaptionVariable.of("input", selectorInput)};
    }
}
