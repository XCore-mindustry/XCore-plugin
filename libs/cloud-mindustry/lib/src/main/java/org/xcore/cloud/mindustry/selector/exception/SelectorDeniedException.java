package org.xcore.cloud.mindustry.selector.exception;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.caption.Caption;
import org.incendo.cloud.caption.CaptionVariable;
import org.xcore.cloud.mindustry.selector.caption.SelectorCaptionKeys;

public class SelectorDeniedException extends SelectorException {
    public SelectorDeniedException(String message) {
        super(message);
    }

    @Override
    public @NonNull Caption caption() {
        return SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_DENIED;
    }

    @Override
    public @NonNull CaptionVariable @NonNull [] captionVariables() {
        return new CaptionVariable[]{CaptionVariable.of("reason", getMessage())};
    }
}
