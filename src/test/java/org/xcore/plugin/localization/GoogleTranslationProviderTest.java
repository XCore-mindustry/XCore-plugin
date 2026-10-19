package org.xcore.plugin.localization;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleTranslationProviderTest {

    private final TranslationExecutor translationExecutor = new TranslationExecutor();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        translationExecutor.shutdown();
    }

    @Test
    @DisplayName("fans out one request per target and reads the detected source language")
    void translate_fansOutAndReadsDetectedLanguage() throws Exception {
        GoogleTranslationProvider provider = provider(form ->
                "[[\"" + form.get("tl") + ":" + form.get("q") + "\",\"en\"]]");

        TranslationOutcome outcome = provider.translate(new TranslationProvider.Request("hi there", "auto", List.of("ru", "de")))
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(outcome.detectedSourceLanguage()).isEqualTo("en");
        assertThat(outcome.translations())
                .containsEntry("ru", "ru:hi there")
                .containsEntry("de", "de:hi there");
        assertThat(outcome.failures()).isEmpty();
    }

    @Test
    @DisplayName("reads the plain answer shape returned for an explicit source language")
    void translate_readsExplicitSourceShape() throws Exception {
        GoogleTranslationProvider provider = provider(form -> "[\"привет\"]");

        TranslationOutcome outcome = provider.translate(TranslationProvider.Request.single("hello", "en", "ru"))
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(outcome.translations()).containsEntry("ru", "привет");
        assertThat(outcome.detectedSourceLanguage()).isNull();
    }

    @Test
    @DisplayName("an HTTP error fails only that target")
    void translate_failsTargetOnHttpError() throws Exception {
        GoogleTranslationProvider provider = provider(form -> "de".equals(form.get("tl")) ? null : "[[\"ok\",\"en\"]]");

        TranslationOutcome outcome = provider.translate(new TranslationProvider.Request("hello", "auto", List.of("ru", "de")))
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(outcome.translations()).containsOnlyKeys("ru");
        assertThat(outcome.failures().get("de").reason()).contains("HTTP 500");
    }

    @Test
    @DisplayName("concurrent requests never receive each other's translation")
    void translate_concurrentRequestsKeepTheirOwnResults() throws Exception {
        GoogleTranslationProvider provider = provider(form ->
                "[[\"" + form.get("tl") + ":" + form.get("q") + "\",\"en\"]]");

        List<CompletableFuture<TranslationOutcome>> futures = new ArrayList<>();
        List<String> targets = List.of("ru", "de", "fr", "es", "pl", "uk", "cs", "be");
        for (int i = 0; i < 40; i++) {
            futures.add(provider.translate(new TranslationProvider.Request("message " + i, "auto", targets)).toCompletableFuture());
        }

        for (int i = 0; i < futures.size(); i++) {
            TranslationOutcome outcome = futures.get(i).get(10, TimeUnit.SECONDS);
            for (String target : targets) {
                assertThat(outcome.translations()).containsEntry(target, target + ":message " + i);
            }
        }
    }

    /** Answers with {@code responder}'s body, or HTTP 500 when it returns {@code null}. */
    private GoogleTranslationProvider provider(java.util.function.Function<Map<String, String>, String> responder) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.createContext("/translate", exchange -> {
            try {
                Map<String, String> form = parseForm(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                String body = responder.apply(form);
                byte[] bytes = (body == null ? "error" : body).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(body == null ? 500 : 200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally {
                exchange.close();
            }
        });
        server.start();
        return new GoogleTranslationProvider(translationExecutor,
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/translate"));
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> form = new HashMap<>();
        for (String pair : body.split("&")) {
            int separator = pair.indexOf('=');
            form.put(URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8));
        }
        return form;
    }
}
