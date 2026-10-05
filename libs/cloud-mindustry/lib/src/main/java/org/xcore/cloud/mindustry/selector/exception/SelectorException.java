package org.xcore.cloud.mindustry.selector.exception;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.caption.Caption;
import org.incendo.cloud.caption.CaptionVariable;

/**
 * Base type of every target selector failure.
 * <p>
 * Each failure carries a {@link Caption} and its variables, so it is rendered through the
 * manager's caption registry and can be localized like any other Cloud message. The
 * exception message stays an English fallback for logs.
 */
public abstract class SelectorException extends RuntimeException {

    protected SelectorException(String message) {
        super(message);
    }

    public abstract @NonNull Caption caption();

    public abstract @NonNull CaptionVariable @NonNull [] captionVariables();
}
