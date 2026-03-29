package org.xcore.plugin.localization;

import arc.util.Strings;
import arc.util.serialization.JsonReader;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Singleton
public final class GoogleTranslationProvider implements TranslationProvider {

    private static final JsonReader READER = new JsonReader();
    private static final String ENDPOINT = "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&dt=t";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final TranslationExecutor translationExecutor;
    private final HttpClient client;

    @Inject
    public GoogleTranslationProvider(TranslationExecutor translationExecutor) {
        this.translationExecutor = translationExecutor;
        this.client = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
    }

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
        translationExecutor.execute(() -> callback.get(executeTranslation(request)));
    }

    private TranslationResult executeTranslation(Request request) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody(request), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return TranslationResult.failure(TranslationFailure.unavailable(name(), "translation request failed (HTTP %d)".formatted(response.statusCode())));
            }

            String translatedText = READER.parse(response.body()).get(0).get(0).asString();
            return TranslationResult.success(translatedText);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return TranslationResult.failure(TranslationFailure.failed(name(), exception));
        } catch (IOException | RuntimeException exception) {
            return TranslationResult.failure(TranslationFailure.failed(name(), exception));
        }
    }

    private String formBody(Request request) {
        return "tl=" + request.targetLanguage()
                + "&sl=" + request.sourceLanguage()
                + "&q=" + Strings.encode(request.text());
    }
}
