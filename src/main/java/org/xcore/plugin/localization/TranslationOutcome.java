package org.xcore.plugin.localization;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The result of translating one text into several target languages. Every requested target
 * appears in exactly one of {@link #translations()} and {@link #failures()}.
 *
 * @param detectedSourceLanguage the language the provider recognised the text as, or {@code null}
 */
public record TranslationOutcome(String detectedSourceLanguage,
                                 Map<String, String> translations,
                                 Map<String, TranslationFailure> failures) {

    public TranslationOutcome {
        translations = Map.copyOf(translations);
        failures = Map.copyOf(failures);
    }

    public static TranslationOutcome failed(Collection<String> targets, TranslationFailure failure) {
        Map<String, TranslationFailure> failures = new LinkedHashMap<>();
        for (String target : targets) {
            failures.put(target, failure);
        }
        return new TranslationOutcome(null, Map.of(), failures);
    }

    public boolean allSucceeded() {
        return failures.isEmpty();
    }

    /**
     * Combines two outcomes for disjoint or overlapping targets; a translation always wins over
     * a failure for the same target, and the first detected language is kept.
     */
    public TranslationOutcome merge(TranslationOutcome other) {
        Map<String, String> mergedTranslations = new LinkedHashMap<>(translations);
        mergedTranslations.putAll(other.translations);
        Map<String, TranslationFailure> mergedFailures = new LinkedHashMap<>(failures);
        mergedFailures.putAll(other.failures);
        mergedFailures.keySet().removeAll(mergedTranslations.keySet());
        String detected = detectedSourceLanguage != null ? detectedSourceLanguage : other.detectedSourceLanguage;
        return new TranslationOutcome(detected, mergedTranslations, mergedFailures);
    }
}
