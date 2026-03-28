package org.xcore.plugin.localization;

import arc.util.Http;
import arc.util.Strings;
import arc.util.serialization.JsonReader;
import jakarta.inject.Singleton;

@Singleton
public final class GoogleTranslationProvider implements TranslationProvider {

    private static final JsonReader READER = new JsonReader();
    private static final String ENDPOINT = "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&dt=t";

    @Override
    public String name() {
        return "google";
    }

    @Override
    public String type() {
        return "google";
    }

    @Override
    public void translate(Request request, arc.func.Cons<TranslationResult> callback) {
        Http.post(ENDPOINT,
                        "tl=" + request.targetLanguage() + "&sl=" + request.sourceLanguage() + "&q=" + Strings.encode(request.text()))
                .error(throwable -> callback.get(TranslationResult.failure(TranslationFailure.failed(name(), throwable))))
                .submit(response -> callback.get(TranslationResult.success(
                        READER.parse(response.getResultAsString()).get(0).get(0).asString())));
    }
}
