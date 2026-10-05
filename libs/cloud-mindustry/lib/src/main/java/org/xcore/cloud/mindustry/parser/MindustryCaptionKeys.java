package org.xcore.cloud.mindustry.parser;

import org.incendo.cloud.caption.Caption;

public final class MindustryCaptionKeys {

    private MindustryCaptionKeys() {}

    /** Variables: {@code input}. */
    public static final Caption ARGUMENT_PARSE_FAILURE_TEAM =
            Caption.of("argument.parse.failure.team");
    /** Variables: {@code input}, {@code type} (the content type name, e.g. {@code unit}). */
    public static final Caption ARGUMENT_PARSE_FAILURE_CONTENT =
            Caption.of("argument.parse.failure.content");
}
