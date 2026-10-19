package org.xcore.plugin.translation;

/** What a recipient group gets for one chat message in one target language. */
public sealed interface ChatTranslation permits ChatTranslation.Translated, ChatTranslation.Untranslated {

    record Translated(String text) implements ChatTranslation {
    }

    record Untranslated(Reason reason) implements ChatTranslation {
    }

    enum Reason {
        /** Translation is turned off in the config. */
        DISABLED,
        /** No provider in the pipeline supports the target language. */
        UNSUPPORTED_LANGUAGE,
        /** The prefilter decided the text reads the same in every language. */
        NOT_NEEDED,
        /** The text is already in the target language. */
        SAME_LANGUAGE,
        /** The author used up their translation budget. */
        RATE_LIMITED,
        /** Too many provider calls are already in flight. */
        OVERLOADED,
        /** Every provider failed, or the pipeline timed out. */
        FAILED
    }

    static ChatTranslation untranslated(Reason reason) {
        return new Untranslated(reason);
    }
}
