package org.xcore.cloud.mindustry.selector.exception;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.caption.Caption;
import org.incendo.cloud.caption.CaptionVariable;
import org.xcore.cloud.mindustry.selector.caption.SelectorCaptionKeys;

public class SelectorLimitExceededException extends SelectorException {
    private final int count;
    private final int limit;

    public SelectorLimitExceededException(int count, int limit) {
        super("Selector matched " + count + " entities, exceeding maximum limit of " + limit);
        this.count = count;
        this.limit = limit;
    }

    public int count() {
        return count;
    }

    public int limit() {
        return limit;
    }

    @Override
    public @NonNull Caption caption() {
        return SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_LIMIT_EXCEEDED;
    }

    @Override
    public @NonNull CaptionVariable @NonNull [] captionVariables() {
        return new CaptionVariable[]{
                CaptionVariable.of("count", String.valueOf(count)),
                CaptionVariable.of("limit", String.valueOf(limit))
        };
    }
}
