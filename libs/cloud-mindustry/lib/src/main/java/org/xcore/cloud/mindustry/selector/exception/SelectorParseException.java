package org.xcore.cloud.mindustry.selector.exception;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.exception.parsing.ParserException;

/**
 * A {@link SelectorException} raised while parsing an argument, exposed as a Cloud
 * {@link ParserException} so it travels the standard argument-parse path: the caption and
 * variables are the ones of the wrapped {@link #selectorException()}.
 */
public final class SelectorParseException extends ParserException {

    private final SelectorException selectorException;

    public SelectorParseException(
            @NonNull Class<?> argumentParser,
            @NonNull CommandContext<?> context,
            @NonNull SelectorException selectorException
    ) {
        super(selectorException, argumentParser, context, selectorException.caption(), selectorException.captionVariables());
        this.selectorException = selectorException;
    }

    public @NonNull SelectorException selectorException() {
        return selectorException;
    }
}
