package org.xcore.cloud.mindustry.selector.caption;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.incendo.cloud.caption.Caption;
import org.incendo.cloud.caption.CaptionProvider;

import java.util.Map;

/**
 * English defaults for the selector captions. Returns {@code null} for every other caption so
 * providers registered before it (Cloud's standard captions among them) still answer.
 */
public final class SelectorCaptionProvider<C> implements CaptionProvider<C> {

    private static final Map<Caption, String> DEFAULTS = Map.of(
            SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_SYNTAX,
            "Invalid selector syntax: [lightgray]<reason>[] in '[white]<input>[]'.",
            SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_NO_SUCH_TARGET,
            "No targets matched selector '[white]<input>[]'.",
            SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_TOO_MANY_TARGETS,
            "Multiple targets matched '[white]<input>[]', but only one target was expected.",
            SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_DENIED,
            "Target selectors are not permitted: [lightgray]<reason>",
            SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_KIND_NOT_ALLOWED,
            "Selector '[white]<kind>[]' is not allowed here.",
            SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_SENDER_REQUIRED,
            "Selector '[white]<kind>[]' requires an in-game player sender.",
            SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_LIMIT_EXCEEDED,
            "Selector matched <count> entities, exceeding limit of <limit>."
    );

    @Override
    public @Nullable String provide(@NonNull Caption caption, @NonNull C sender) {
        return DEFAULTS.get(caption);
    }
}
