package org.xcore.plugin.localization;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.service.TranslationSafetyService;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

public final class OpenAITranslationProvider implements TranslationProvider {

    private static final String PROVIDER_TYPE = "openai";
    static final long BASE_OUTPUT_TOKENS = 64L;
    static final long OUTPUT_TOKENS_PER_TARGET = 384L;
    private static final Gson GSON = new Gson();

    private final String providerId;
    private final TomlSecretsConfig.TranslationSection.ProviderConfig providerConfig;
    private final TranslationSafetyService translationSafetyService;
    private final TranslationExecutor translationExecutor;
    private final HttpClient client;
    private final Set<String> supportedLanguages;
    private final OpenAIRequestFactory requestFactory;
    private final OpenAIResponseParser responseParser;
    private final OpenAIRetryPolicy retryPolicy;

    public OpenAITranslationProvider(String providerId,
                                     TomlSecretsConfig.TranslationSection.ProviderConfig providerConfig,
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
        this.responseParser = new OpenAIResponseParser();
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

    /** Model, API mode and prompt all change the output, so all of them invalidate cached translations. */
    @Override
    public String signature() {
        return String.join(":", providerId, PROVIDER_TYPE, providerConfig.model,
                requestFactory.normalizedApiMode(), "p" + TranslationSafetyService.PROMPT_VERSION);
    }

    @Override
    public CompletionStage<TranslationOutcome> translate(Request request) {
        if (providerConfig.apiKey == null || providerConfig.apiKey.isBlank()) {
            return CompletableFuture.completedFuture(TranslationOutcome.failed(request.targetLanguages(),
                    TranslationFailure.unavailable(name(), "provider is not configured")));
        }

        return translationExecutor.supply(() -> executeTranslation(request));
    }

    @Override
    public boolean supports(String languageCode) {
        if (languageCode == null || languageCode.isBlank() || supportedLanguages.isEmpty()) {
            return true;
        }

        return supportedLanguages.contains(languageCode.trim().toLowerCase(Locale.ROOT));
    }

    private TranslationOutcome executeTranslation(Request request) {
        try {
            TranslationSafetyService.PreparationResult preparationResult = translationSafetyService.prepare(request, name());
            if (preparationResult instanceof TranslationSafetyService.PreparationResult.Failure(var failure)) {
                return TranslationOutcome.failed(request.targetLanguages(), failure);
            }

            TranslationSafetyService.PreparedRequest preparedRequest =
                    ((TranslationSafetyService.PreparationResult.Success) preparationResult).preparedRequest();

            String rawOutput = executeConfiguredRequest(preparedRequest);
            boolean allowPlainText = !translationSafetyService.requiresStructuredOutput()
                    || OpenAIRequestFactory.API_MODE_CHAT_COMPLETIONS.equals(requestFactory.normalizedApiMode());
            return translationSafetyService.validate(name(), preparedRequest, rawOutput, allowPlainText);
        } catch (RateLimitFailureException exception) {
            return TranslationOutcome.failed(request.targetLanguages(),
                    TranslationFailure.unavailable(name(), "translation rate limit exceeded"));
        } catch (RuntimeException exception) {
            return TranslationOutcome.failed(request.targetLanguages(),
                    TranslationFailure.unavailable(name(), sanitizeFailureReason(exception)));
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
