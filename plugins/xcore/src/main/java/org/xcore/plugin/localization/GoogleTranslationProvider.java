package org.xcore.plugin.localization;

import arc.util.Strings;
import arc.util.serialization.JsonReader;
import arc.util.serialization.JsonValue;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The public Google endpoint takes one target per request, so a multi-target request fans out
 * into parallel calls. With {@code sl=auto} each answer is {@code [["translation","en"]]} and
 * carries the detected source language; with an explicit source it is {@code ["translation"]}.
 */
@Singleton
public final class GoogleTranslationProvider implements TranslationProvider {

    private static final String ENDPOINT = "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&dt=t";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final TranslationExecutor translationExecutor;
    private final HttpClient client;
    private final URI endpoint;

    @Inject
    public GoogleTranslationProvider(TranslationExecutor translationExecutor) {
        this(translationExecutor, URI.create(ENDPOINT));
    }

    GoogleTranslationProvider(TranslationExecutor translationExecutor, URI endpoint) {
        this.translationExecutor = translationExecutor;
        this.endpoint = endpoint;
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
    public CompletionStage<TranslationOutcome> translate(Request request) {
        List<CompletableFuture<TranslationOutcome>> parts = request.targetLanguages().stream()
                .map(target -> translationExecutor.supply(() -> translateOne(request, target)))
                .toList();

        return CompletableFuture.allOf(parts.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> parts.stream()
                        .map(CompletableFuture::join)
                        .reduce(new TranslationOutcome(null, Map.of(), Map.of()), TranslationOutcome::merge));
    }

    private TranslationOutcome translateOne(Request request, String targetLanguage) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(endpoint)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody(request, targetLanguage), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return failed(targetLanguage, TranslationFailure.unavailable(name(),
                        "translation request failed (HTTP %d)".formatted(response.statusCode())));
            }

            // JsonReader keeps parse state in instance fields, so one per request: a shared
            // instance raced across translation threads and could hand one language's
            // result to another request.
            JsonValue first = new JsonReader().parse(response.body()).get(0);
            String translatedText = first.isArray() ? first.getString(0) : first.asString();
            String detected = first.isArray() && first.size > 1 ? first.getString(1) : null;
            return new TranslationOutcome(detected, Map.of(targetLanguage, translatedText), Map.of());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failed(targetLanguage, TranslationFailure.failed(name(), exception));
        } catch (IOException | RuntimeException exception) {
            return failed(targetLanguage, TranslationFailure.failed(name(), exception));
        }
    }

    private static TranslationOutcome failed(String targetLanguage, TranslationFailure failure) {
        return TranslationOutcome.failed(List.of(targetLanguage), failure);
    }

    private String formBody(Request request, String targetLanguage) {
        return "tl=" + Strings.encode(targetLanguage)
                + "&sl=" + Strings.encode(request.sourceLanguage() == null ? "auto" : request.sourceLanguage())
                + "&q=" + Strings.encode(request.text());
    }
}
