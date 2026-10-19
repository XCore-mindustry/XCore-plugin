package org.xcore.plugin.localization;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface TranslationProvider {

    String name();

    default String type() {
        return name();
    }

    /**
     * Identifies what this provider produces for a given input. It is part of the cache key,
     * so it must change whenever the output would (a different model or prompt).
     */
    default String signature() {
        return name() + ":" + type();
    }

    /**
     * Translates {@link Request#text()} into every requested target. The stage never completes
     * exceptionally for an ordinary provider failure: each target that could not be translated
     * is reported in {@link TranslationOutcome#failures()}.
     */
    CompletionStage<TranslationOutcome> translate(Request request);

    default boolean supports(String languageCode) {
        return true;
    }

    record Request(String text, String sourceLanguage, List<String> targetLanguages) {

        public Request {
            targetLanguages = List.copyOf(targetLanguages);
        }

        public static Request single(String text, String sourceLanguage, String targetLanguage) {
            return new Request(text, sourceLanguage, List.of(targetLanguage));
        }
    }
}
