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

    /** Variables: {@code input}. */
    public static final Caption ARGUMENT_PARSE_FAILURE_PLAYER_INFO =
            Caption.of("argument.parse.failure.player_info");
    /** Variables: {@code input}, {@code count}. */
    public static final Caption ARGUMENT_PARSE_FAILURE_PLAYER_INFO_AMBIGUOUS =
            Caption.of("argument.parse.failure.player_info.ambiguous");
    /** Variables: {@code input}. */
    public static final Caption ARGUMENT_PARSE_FAILURE_PLAYER_INFO_IP_DENIED =
            Caption.of("argument.parse.failure.player_info.ip_denied");

    /** Variables: {@code page}, {@code pages}. */
    public static final Caption HELP_TITLE = Caption.of("mindustry.help.title");
    /** Variables: {@code query}, {@code page}, {@code pages}. */
    public static final Caption HELP_TITLE_QUERY = Caption.of("mindustry.help.title.query");
    /** Variables: {@code syntax}. */
    public static final Caption HELP_ENTRY = Caption.of("mindustry.help.entry");
    /** Variables: {@code syntax}, {@code description}. */
    public static final Caption HELP_ENTRY_DESCRIBED = Caption.of("mindustry.help.entry.described");
    /** Variables: {@code page}, {@code pages}, {@code next}. Shown when more pages follow. */
    public static final Caption HELP_FOOTER = Caption.of("mindustry.help.footer");
    /** Variables: {@code syntax}. */
    public static final Caption HELP_USAGE = Caption.of("mindustry.help.usage");
    /** Variables: {@code description}. */
    public static final Caption HELP_DESCRIPTION = Caption.of("mindustry.help.description");
    /** Variables: {@code name}, {@code description}. */
    public static final Caption HELP_ARGUMENT = Caption.of("mindustry.help.argument");
    /** No variables. */
    public static final Caption HELP_EMPTY = Caption.of("mindustry.help.empty");
    /** Variables: {@code query}. */
    public static final Caption HELP_NO_MATCH = Caption.of("mindustry.help.no_match");
    /** Variables: {@code page}, {@code pages}. */
    public static final Caption HELP_INVALID_PAGE = Caption.of("mindustry.help.invalid_page");
}
