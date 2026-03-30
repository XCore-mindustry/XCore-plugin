package org.xcore.plugin.localization;

import arc.func.Cons;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.service.TranslationSafetyService;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class OpenAITranslationProvider implements TranslationProvider {

    private static final String PROVIDER_TYPE = "openai";
    static final long MAX_OUTPUT_TOKENS = 512L;
    private static final Gson GSON = new Gson();

    private final String providerId;
    private final GlobalConfig.TranslationProviderConfig providerConfig;
    private final TranslationSafetyService translationSafetyService;
    private final TranslationExecutor translationExecutor;
    private final HttpClient client;
    private final Set<String> supportedLanguages;
    private final OpenAIRequestFactory requestFactory;
    private final OpenAIResponseParser responseParser;
    private final OpenAIRetryPolicy retryPolicy;

    public OpenAITranslationProvider(String providerId,
                                     GlobalConfig.TranslationProviderConfig providerConfig,
                                     TranslationSafetyService translationSafetyService,
                                     TranslationExecutor translationExecutor) {
        this.providerId = providerId;
        this.providerConfig = providerConfig;
        this.translationSafetyService = translationSafetyService;
        this.translationExecutor = translationExecutor;
        this.providerConfig.normalize();
        this.supportedLanguages = this.providerConfig.supportedLanguages.stream()
                .filter(language -> language != null && !language.isBlank())
                .map(language -> language.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(this.providerConfig.timeoutSeconds))
                .build();
        this.requestFactory = new OpenAIRequestFactory(this.providerConfig, translationSafetyService);
        this.responseParser = new OpenAIResponseParser(translationSafetyService);
        this.retryPolicy = new OpenAIRetryPolicy();
    }

    @Override
    public String name() {
        return providerId;
    }

    @Override
    public String type() {
        return PROVIDER_TYPE;
    }

    @Override
    public void translate(Request request, Cons<TranslationResult> callback) {
        if (request.text() == null || request.text().isBlank()) {
            callback.get(TranslationResult.success(""));
            return;
        }

        if (providerConfig.apiKey == null || providerConfig.apiKey.isBlank()) {
            callback.get(TranslationResult.failure(TranslationFailure.unavailable(name(), "provider is not configured")));
            return;
        }

        translationExecutor.execute(() -> callback.get(executeTranslation(request)));
    }

    @Override
    public boolean supports(String languageCode) {
        if (languageCode == null || languageCode.isBlank() || supportedLanguages.isEmpty()) {
            return true;
        }

        return supportedLanguages.contains(languageCode.trim().toLowerCase(Locale.ROOT));
    }

    private TranslationResult executeTranslation(Request request) {
        try {
            TranslationSafetyService.PreparationResult preparationResult = translationSafetyService.prepare(request, name());
            if (preparationResult instanceof TranslationSafetyService.PreparationResult.Failure(var failure)) {
                return TranslationResult.failure(failure);
            }

            TranslationSafetyService.PreparedRequest preparedRequest =
                    ((TranslationSafetyService.PreparationResult.Success) preparationResult).preparedRequest();

            String translatedText = executeConfiguredRequest(preparedRequest);
            return translationSafetyService.validate(name(), preparedRequest, translatedText);
        } catch (RateLimitFailureException exception) {
            return TranslationResult.failure(TranslationFailure.unavailable(name(), "translation rate limit exceeded"));
        } catch (RuntimeException exception) {
            return TranslationResult.failure(TranslationFailure.unavailable(name(), sanitizeFailureReason(exception)));
        }
    }

    private String executeConfiguredRequest(TranslationSafetyService.PreparedRequest preparedRequest) {
        String apiMode = requestFactory.normalizedApiMode();
        String path = switch (apiMode) {
            case OpenAIRequestFactory.API_MODE_CHAT_COMPLETIONS -> "/chat/completions";
            case OpenAIRequestFactory.API_MODE_RESPONSES -> "/responses";
            default -> throw new IllegalStateException("Unsupported OpenAI provider api mode: " + providerConfig.apiMode);
        };
        JsonObject response = executeRequest(path, requestFactory.buildRequestBody(apiMode, preparedRequest));
        return responseParser.extractTranslation(apiMode, response);
    }

    private JsonObject executeRequest(String path, JsonObject requestBody) {
        String requestJson = GSON.toJson(requestBody);
        int maxAttempts = Math.max(1, providerConfig.maxRetries + 1);
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<String> response = client.send(
                        requestFactory.buildHttpRequest(path, requestJson),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                );

                int statusCode = response.statusCode();
                if (statusCode == 429) {
                    RateLimitFailureException failure = new RateLimitFailureException(responseParser.extractErrorMessage(response.body(), statusCode));
                    if (attempt < maxAttempts) {
                        backoff(attempt, response);
                        lastFailure = failure;
                        continue;
                    }
                    throw failure;
                }

                if (statusCode >= 200 && statusCode < 300) {
                    return responseParser.parseJsonObject(response.body(), "OpenAI response");
                }

                HttpFailureException failure = new HttpFailureException(statusCode, responseParser.extractErrorMessage(response.body(), statusCode));
                if (retryPolicy.isRetryableStatus(statusCode) && attempt < maxAttempts) {
                    backoff(attempt, response);
                    lastFailure = failure;
                    continue;
                }
                throw failure;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RequestInterruptedException(exception);
            } catch (IOException exception) {
                TransportFailureException failure = new TransportFailureException(exception);
                if (attempt < maxAttempts) {
                    backoff(attempt, null);
                    lastFailure = failure;
                    continue;
                }
                throw failure;
            }
        }

        throw lastFailure == null ? new IllegalStateException("OpenAI request failed without details") : lastFailure;
    }

    private void backoff(int attempt, HttpResponse<String> response) {
        try {
            retryPolicy.backoff(attempt, response);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RequestInterruptedException(exception);
        }
    }

    private String sanitizeFailureReason(RuntimeException exception) {
        if (exception instanceof HttpFailureException httpFailureException) {
            return "translation request failed (HTTP %d)".formatted(httpFailureException.statusCode());
        }

        if (exception instanceof TransportFailureException) {
            return "translation request failed (io)";
        }

        if (exception instanceof RequestInterruptedException) {
            return "translation request failed (interrupted)";
        }

        return "translation request failed (%s)".formatted(exception.getClass().getSimpleName());
    }
    private static final class HttpFailureException extends RuntimeException {
        private final int statusCode;

        private HttpFailureException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        private int statusCode() {
            return statusCode;
        }
    }

    private static final class RateLimitFailureException extends RuntimeException {
        private RateLimitFailureException(String message) {
            super(message);
        }
    }

    private static final class TransportFailureException extends RuntimeException {
        private TransportFailureException(IOException cause) {
            super(cause);
        }
    }

    private static final class RequestInterruptedException extends RuntimeException {
        private RequestInterruptedException(InterruptedException cause) {
            super(cause);
        }
    }
}
