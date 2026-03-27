package org.xcore.plugin.localization;

import arc.struct.Seq;
import arc.struct.OrderedMap;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.service.TranslationFallbackService;

import java.util.Locale;

@Singleton
public class TranslatorLanguagesProvider {

    private final OrderedMap<String, String> translatorLanguages = new OrderedMap<>();

    public TranslatorLanguagesProvider() {
        this(null);
    }

    @Inject
    public TranslatorLanguagesProvider(TranslationFallbackService translationProvider) {
        addIfSupported(translationProvider, "ca", "Català");
        addIfSupported(translationProvider, "id", "Indonesian");
        addIfSupported(translationProvider, "da", "Dansk");
        addIfSupported(translationProvider, "de", "Deutsch");
        addIfSupported(translationProvider, "et", "Eesti");
        addIfSupported(translationProvider, "en", "English");
        addIfSupported(translationProvider, "es", "Español");
        addIfSupported(translationProvider, "eu", "Euskara");
        addIfSupported(translationProvider, "fil", "Filipino");
        addIfSupported(translationProvider, "fr", "Français");
        addIfSupported(translationProvider, "it", "Italiano");
        addIfSupported(translationProvider, "lt", "Lietuvių");
        addIfSupported(translationProvider, "hu", "Magyar");
        addIfSupported(translationProvider, "nl", "Nederlands");
        addIfSupported(translationProvider, "pl", "Polski");
        addIfSupported(translationProvider, "pt", "Português");
        addIfSupported(translationProvider, "ro", "Română");
        addIfSupported(translationProvider, "fi", "Suomi");
        addIfSupported(translationProvider, "sv", "Svenska");
        addIfSupported(translationProvider, "vi", "Tiếng Việt");
        addIfSupported(translationProvider, "tk", "Türkmen dili");
        addIfSupported(translationProvider, "tr", "Türkçe");
        addIfSupported(translationProvider, "cs", "Čeština");
        addIfSupported(translationProvider, "be", "Беларуская");
        addIfSupported(translationProvider, "bg", "Български");
        addIfSupported(translationProvider, "ru", "Русский");
        addIfSupported(translationProvider, "sr", "Српски");
        addIfSupported(translationProvider, "uk", "Українська");
        addIfSupported(translationProvider, "th", "ไทย");
        addIfSupported(translationProvider, "zh", "简体中文");
        addIfSupported(translationProvider, "ja", "日本語");
        addIfSupported(translationProvider, "ko", "한국어");
    }

    public OrderedMap<String, String> getLanguages() {
        return translatorLanguages;
    }

    public boolean hasLanguage(String code) {
        return code != null && translatorLanguages.containsKey(normalize(code));
    }

    public String findLanguageCode(String input) {
        if (input == null) {
            return null;
        }

        String normalizedInput = normalize(input);
        if (translatorLanguages.containsKey(normalizedInput)) {
            return normalizedInput;
        }

        for (var entry : translatorLanguages) {
            if (entry.value.equalsIgnoreCase(input)) {
                return entry.key;
            }
        }

        return null;
    }

    public Seq<String> languageCodes() {
        return translatorLanguages.orderedKeys();
    }

    private void addIfSupported(TranslationProvider translationProvider, String languageCode, String languageName) {
        if (translationProvider == null || translationProvider.supports(languageCode)) {
            translatorLanguages.put(languageCode, languageName);
        }
    }

    private String normalize(String input) {
        return input.trim().toLowerCase(Locale.ROOT);
    }
}
