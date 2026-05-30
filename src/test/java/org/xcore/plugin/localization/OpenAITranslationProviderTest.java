package org.xcore.plugin.localization;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.service.TranslationSafetyService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAITranslationProviderTest {

    private HttpServer server;
    private final TranslationExecutor translationExecutor = new TranslationExecutor();

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        translationExecutor.shutdown();
    }

    @Test
    @DisplayName("translate uses responses api without OpenAI SDK")
    void translate_usesResponsesApi_withoutOpenAiSdk() throws Exception {
        AtomicReference<String> authorizationHeader = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        String baseUrl = startServer("/v1/responses", exchange -> {
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(readRequestBody(exchange));
            writeJson(exchange, 200, "{\"output_text\":\"{\\\"translation\\\":\\\"Привет\\\"}\"}");
        });

        OpenAITranslationProvider provider = new OpenAITranslationProvider(
                "openai-main",
                providerConfig(baseUrl, "responses"),
                translationSafetyService(true),
                translationExecutor
        );

        TranslationResult result = translate(provider, new TranslationProvider.Request("Hello", "en", "ru"));

        assertThat(result).isInstanceOfSatisfying(TranslationResult.Success.class,
                success -> assertThat(success.translatedText()).isEqualTo("Привет"));
        assertThat(authorizationHeader.get()).isEqualTo("Bearer test-key");

        JsonObject requestJson = JsonParser.parseString(requestBody.get()).getAsJsonObject();
        assertThat(requestJson.get("model").getAsString()).isEqualTo("gpt-test");
        assertThat(requestJson.get("input").getAsString()).contains("\"text\":\"Hello\"");
        assertThat(requestJson.getAsJsonObject("text")
                .getAsJsonObject("format")
                .get("type").getAsString()).isEqualTo("json_schema");
    }

    @Test
    @DisplayName("translate uses chat completions api and wraps plain text for structured output")
    void translate_usesChatCompletionsApi_andWrapsPlainText() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        String baseUrl = startServer("/v1/chat/completions", exchange -> {
            requestBody.set(readRequestBody(exchange));
            writeJson(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"Привет\"}}]}");
        });

        OpenAITranslationProvider provider = new OpenAITranslationProvider(
                "openai-main",
                providerConfig(baseUrl, "chat_completions"),
                translationSafetyService(true),
                translationExecutor
        );

        TranslationResult result = translate(provider, new TranslationProvider.Request("Hello", "en", "ru"));

        assertThat(result).isInstanceOfSatisfying(TranslationResult.Success.class,
                success -> assertThat(success.translatedText()).isEqualTo("Привет"));

        JsonObject requestJson = JsonParser.parseString(requestBody.get()).getAsJsonObject();
        assertThat(requestJson.getAsJsonArray("messages")).hasSize(2);
        assertThat(requestJson.has("response_format")).isFalse();
    }

    @Test
    @DisplayName("translate fails fast when api key is missing")
    void translate_failsFast_whenApiKeyMissing() throws Exception {
        OpenAITranslationProvider provider = new OpenAITranslationProvider(
                "openai-main",
                providerConfig("http://127.0.0.1:1/v1", "responses", " "),
                translationSafetyService(true),
                translationExecutor
        );

        TranslationResult result = translate(provider, new TranslationProvider.Request("Hello", "en", "ru"));

        assertThat(result).isInstanceOfSatisfying(TranslationResult.Failure.class,
                failure -> assertThat(failure.failure().reason()).isEqualTo("provider is not configured"));
    }

    @Test
    @DisplayName("translate honors retry-after on rate limit before retrying")
    void translate_honorsRetryAfter_onRateLimit() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<Long> firstAttemptAt = new AtomicReference<>();
        AtomicReference<Long> secondAttemptAt = new AtomicReference<>();
        String baseUrl = startServer("/v1/responses", exchange -> {
            int attempt = attempts.incrementAndGet();
            long now = System.nanoTime();
            if (attempt == 1) {
                firstAttemptAt.set(now);
                exchange.getResponseHeaders().set("Retry-After", "1");
                writeJson(exchange, 429, "{\"error\":{\"message\":\"slow down\"}}");
                return;
            }

            secondAttemptAt.set(now);
            writeJson(exchange, 200, "{\"output_text\":\"{\\\"translation\\\":\\\"Привет\\\"}\"}");
        });

        OpenAITranslationProvider provider = new OpenAITranslationProvider(
                "openai-main",
                providerConfig(baseUrl, "responses", "test-key", 1),
                translationSafetyService(true),
                translationExecutor
        );

        TranslationResult result = translate(provider, new TranslationProvider.Request("Hello", "en", "ru"));

        assertThat(result).isInstanceOfSatisfying(TranslationResult.Success.class,
                success -> assertThat(success.translatedText()).isEqualTo("Привет"));
        assertThat(attempts.get()).isEqualTo(2);
        assertThat(TimeUnit.NANOSECONDS.toMillis(secondAttemptAt.get() - firstAttemptAt.get())).isGreaterThanOrEqualTo(900L);
    }

    private String startServer(String path, ThrowingHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(path, exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    private TranslationResult translate(OpenAITranslationProvider provider, TranslationProvider.Request request) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<TranslationResult> result = new AtomicReference<>();
        provider.translate(request, translationResult -> {
            result.set(translationResult);
            latch.countDown();
        });
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        return result.get();
    }

    private TranslationSafetyService translationSafetyService(boolean structuredOutputRequired) {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.translation.llm.structuredOutputRequired = structuredOutputRequired;
        return new TranslationSafetyService(config);
    }

    private TomlSecretsConfig.TranslationSection.ProviderConfig providerConfig(String baseUrl, String apiMode) {
        return providerConfig(baseUrl, apiMode, "test-key", 0);
    }

    private TomlSecretsConfig.TranslationSection.ProviderConfig providerConfig(String baseUrl, String apiMode, String apiKey) {
        return providerConfig(baseUrl, apiMode, apiKey, 0);
    }

    private TomlSecretsConfig.TranslationSection.ProviderConfig providerConfig(String baseUrl, String apiMode, String apiKey, int maxRetries) {
        TomlSecretsConfig.TranslationSection.ProviderConfig providerConfig = new TomlSecretsConfig.TranslationSection.ProviderConfig();
        providerConfig.type = "openai";
        providerConfig.apiKey = apiKey;
        providerConfig.baseUrl = baseUrl;
        providerConfig.model = "gpt-test";
        providerConfig.apiMode = apiMode;
        providerConfig.timeoutSeconds = 5;
        providerConfig.maxRetries = maxRetries;
        providerConfig.normalize();
        return providerConfig;
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private void writeJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, payload.length);
        exchange.getResponseBody().write(payload);
    }

    @FunctionalInterface
    private interface ThrowingHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
