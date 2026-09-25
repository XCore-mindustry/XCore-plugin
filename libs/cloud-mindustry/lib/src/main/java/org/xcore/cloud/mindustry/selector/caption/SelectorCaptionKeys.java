package org.xcore.cloud.mindustry.selector.caption;

import org.incendo.cloud.caption.Caption;

public final class SelectorCaptionKeys {

    private SelectorCaptionKeys() {}

    public static final Caption ARGUMENT_PARSE_FAILURE_SELECTOR_SYNTAX =
            Caption.of("argument.parse.failure.selector.syntax");
    public static final Caption ARGUMENT_PARSE_FAILURE_SELECTOR_NO_SUCH_TARGET =
            Caption.of("argument.parse.failure.selector.no_such_target");
    public static final Caption ARGUMENT_PARSE_FAILURE_SELECTOR_TOO_MANY_TARGETS =
            Caption.of("argument.parse.failure.selector.too_many_targets");
    public static final Caption ARGUMENT_PARSE_FAILURE_SELECTOR_DENIED =
            Caption.of("argument.parse.failure.selector.denied");
    public static final Caption ARGUMENT_PARSE_FAILURE_SELECTOR_SENDER_REQUIRED =
            Caption.of("argument.parse.failure.selector.sender_required");
    public static final Caption ARGUMENT_PARSE_FAILURE_SELECTOR_LIMIT_EXCEEDED =
            Caption.of("argument.parse.failure.selector.limit_exceeded");
}
