package org.xcore.plugin.localization;

import arc.func.Cons;

public interface TranslationProvider {

    String name();

    default String type() {
        return name();
    }

    void translate(Request request, Cons<TranslationResult> callback);

    default boolean supports(String languageCode) {
        return true;
    }

    record Request(String text, String sourceLanguage, String targetLanguage) {
    }
}
