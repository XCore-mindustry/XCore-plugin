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
    /** No variables. Shown when a player-only command is run by anyone but an in-game player. */
    public static final Caption SENDER_PLAYER_REQUIRED =
            Caption.of("mindustry.sender.player_required");
}
