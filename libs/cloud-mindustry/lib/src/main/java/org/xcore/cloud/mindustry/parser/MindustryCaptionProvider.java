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

    private static final Map<Caption, String> DEFAULTS = Map.of(
            MindustryCaptionKeys.ARGUMENT_PARSE_FAILURE_TEAM,
            "'[white]<input>[]' is not a valid team.",
            MindustryCaptionKeys.ARGUMENT_PARSE_FAILURE_CONTENT,
            "'[white]<input>[]' is not a valid <type>.",
            MindustryCaptionKeys.SENDER_PLAYER_REQUIRED,
            "This command can only be used by an in-game player."
    );

    @Override
    public @Nullable String provide(@NonNull Caption caption, @NonNull C sender) {
        return DEFAULTS.get(caption);
    }
}
