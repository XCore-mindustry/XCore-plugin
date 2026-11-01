package org.xcore.cloud.mindustry.parser;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.incendo.cloud.caption.Caption;
import org.incendo.cloud.caption.CaptionProvider;

import java.util.Map;

/**
 * English defaults for {@link MindustryCaptionKeys}; {@code null} for every other caption.
 */
public final class MindustryCaptionProvider<C> implements CaptionProvider<C> {

    private static final Map<Caption, String> DEFAULTS = Map.ofEntries(
            Map.entry(MindustryCaptionKeys.ARGUMENT_PARSE_FAILURE_TEAM,
                    "'[white]<input>[]' is not a valid team."),
            Map.entry(MindustryCaptionKeys.ARGUMENT_PARSE_FAILURE_CONTENT,
                    "'[white]<input>[]' is not a valid <type>."),
            Map.entry(MindustryCaptionKeys.SENDER_PLAYER_REQUIRED,
                    "This command can only be used by an in-game player."),
            Map.entry(MindustryCaptionKeys.ARGUMENT_PARSE_FAILURE_PLAYER_INFO,
                    "No player record matches '[white]<input>[]'."),
            Map.entry(MindustryCaptionKeys.ARGUMENT_PARSE_FAILURE_PLAYER_INFO_AMBIGUOUS,
                    "'[white]<input>[]' matches <count> player records; use a UUID instead."),
            Map.entry(MindustryCaptionKeys.ARGUMENT_PARSE_FAILURE_PLAYER_INFO_IP_DENIED,
                    "Players can only be looked up by IP from the console."),
            Map.entry(MindustryCaptionKeys.HELP_TITLE,
                    "[orange]-- Commands (page <page> of <pages>) --"),
            Map.entry(MindustryCaptionKeys.HELP_TITLE_QUERY,
                    "[orange]-- <query> (page <page> of <pages>) --"),
            Map.entry(MindustryCaptionKeys.HELP_ENTRY,
                    "[orange]<syntax>"),
            Map.entry(MindustryCaptionKeys.HELP_ENTRY_DESCRIBED,
                    "[orange]<syntax>[lightgray] - <description>"),
            Map.entry(MindustryCaptionKeys.HELP_FOOTER,
                    "[lightgray]Page <page> of <pages>; continue with page <next>."),
            Map.entry(MindustryCaptionKeys.HELP_USAGE,
                    "[orange]Usage: [white]<syntax>"),
            Map.entry(MindustryCaptionKeys.HELP_DESCRIPTION,
                    "[lightgray]<description>"),
            Map.entry(MindustryCaptionKeys.HELP_ARGUMENT,
                    "[white]  <name>[lightgray] - <description>"),
            Map.entry(MindustryCaptionKeys.HELP_EMPTY,
                    "There are no commands you can use."),
            Map.entry(MindustryCaptionKeys.HELP_NO_MATCH,
                    "No command matches '[white]<query>[]'."),
            Map.entry(MindustryCaptionKeys.HELP_INVALID_PAGE,
                    "Page <page> does not exist; there are <pages>.")
    );

    @Override
    public @Nullable String provide(@NonNull Caption caption, @NonNull C sender) {
        return DEFAULTS.get(caption);
    }
}
