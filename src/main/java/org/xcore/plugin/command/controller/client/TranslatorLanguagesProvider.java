package org.xcore.plugin.command.controller.client;

import arc.struct.OrderedMap;
import jakarta.inject.Singleton;

@Singleton
public class TranslatorLanguagesProvider {

    private final OrderedMap<String, String> translatorLanguages = new OrderedMap<>();

    public TranslatorLanguagesProvider() {
        translatorLanguages.putAll(
                "ca", "Català", "id", "Indonesian", "da", "Dansk", "de", "Deutsch", "et", "Eesti",
                "en", "English", "es", "Español", "eu", "Euskara", "fil", "Filipino", "fr", "Français",
                "it", "Italiano", "lt", "Lietuvių", "hu", "Magyar", "nl", "Nederlands", "pl", "Polski",
                "pt", "Português", "ro", "Română", "fi", "Suomi", "sv", "Svenska", "vi", "Tiếng Việt",
                "tk", "Türkmen dili", "tr", "Türkçe", "cs", "Čeština", "be", "Беларуская", "bg", "Български",
                "ru", "Русский", "sr", "Српски", "uk", "Українська", "th", "ไทย", "zh", "简体中文",
                "ja", "日本語", "ko", "한국어"
        );
    }

    public OrderedMap<String, String> getLanguages() {
        return translatorLanguages;
    }
}
