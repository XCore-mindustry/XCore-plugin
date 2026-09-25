package org.xcore.cloud.mindustry.selector.caption;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.caption.Caption;
import org.incendo.cloud.caption.CaptionProvider;

public final class SelectorCaptionProvider<C> implements CaptionProvider<C> {

    @Override
    public @NonNull String provide(@NonNull Caption caption, @NonNull C sender) {
        if (caption.equals(SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_SYNTAX)) {
            return "[scarlet]Invalid selector syntax: [lightgray]<reason>[scarlet] in '[white]<input>[scarlet]'.";
        }
        if (caption.equals(SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_NO_SUCH_TARGET)) {
            return "[scarlet]No targets matched selector '[white]<input>[scarlet]'.";
        }
        if (caption.equals(SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_TOO_MANY_TARGETS)) {
            return "[scarlet]Multiple targets matched '[white]<input>[scarlet]', but only one target was expected.";
        }
        if (caption.equals(SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_DENIED)) {
            return "[scarlet]Target selectors are not permitted: [lightgray]<reason>";
        }
        if (caption.equals(SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_SENDER_REQUIRED)) {
            return "[scarlet]Selector '[white]<kind>[scarlet]' requires an in-game player sender.";
        }
        if (caption.equals(SelectorCaptionKeys.ARGUMENT_PARSE_FAILURE_SELECTOR_LIMIT_EXCEEDED)) {
            return "[scarlet]Selector matched <count> entities, exceeding limit of <limit>.";
        }
        return "";
    }
}
