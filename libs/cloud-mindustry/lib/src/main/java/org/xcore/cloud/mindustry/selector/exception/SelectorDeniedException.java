package org.xcore.cloud.mindustry.selector.exception;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.incendo.cloud.caption.Caption;
import org.incendo.cloud.caption.CaptionVariable;
import org.xcore.cloud.mindustry.selector.SelectorKind;
import org.xcore.cloud.mindustry.selector.caption.SelectorCaptionKeys;

/**
 * A selector was refused. Either all selectors are denied with a reason (the
 * {@code @DenySelectors} text), or one selector kind is not accepted at this place.
 */
public class SelectorDeniedException extends SelectorException {

    private final @Nullable SelectorKind kind;

    public SelectorDeniedException(String reason) {
        super(reason);
        this.kind = null;
    }

    public SelectorDeniedException(@NonNull SelectorKind kind) {
        super("Selector '" + kind.token() + "' is not allowed here.");
        this.kind = kind;
    }

    /**
     * @return the refused kind, or {@code null} when every selector is denied
     */
    public @Nullable SelectorKind kind() {
        return kind;
    }

    @Override
    public @NonNull Caption caption() {
        return kind == null
                ? SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_DENIED
                : SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_KIND_NOT_ALLOWED;
    }

    @Override
    public @NonNull CaptionVariable @NonNull [] captionVariables() {
        return kind == null
                ? new CaptionVariable[]{CaptionVariable.of("reason", getMessage())}
                : new CaptionVariable[]{CaptionVariable.of("kind", kind.token())};
    }
}
