package org.xcore.plugin.localization;

import arc.func.Cons;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.service.TranslationSafetyService;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class OpenAITranslationProvider implements TranslationProvider {

    private static final String PROVIDER_TYPE = "openai";
    private static final long MAX_OUTPUT_TOKENS = 512L;
    private static final String API_MODE_RESPONSES = "responses";
    private static final String API_MODE_CHAT_COMPLETIONS = "chat_completions";
    private static final long INITIAL_RETRY_DELAY_MILLIS = 500L;
    private static final long MAX_RETRY_DELAY_MILLIS = 8_000L;
    private static final Gson GSON = new Gson();

    private final String providerId;
    private final GlobalConfig.TranslationProviderConfig providerConfig;
    private final TranslationSafetyService translationSafetyService;
    private final TranslationExecutor translationExecutor;
    private final HttpClient client;
    private final Set<String> supportedLanguages;

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
        return switch (normalizedApiMode()) {
            case API_MODE_CHAT_COMPLETIONS -> executeChatCompletion(preparedRequest);
            case API_MODE_RESPONSES -> executeResponsesRequest(preparedRequest);
            default -> throw new IllegalStateException("Unsupported OpenAI provider api mode: " + providerConfig.apiMode);
        };
    }

    private String executeResponsesRequest(TranslationSafetyService.PreparedRequest preparedRequest) {
        JsonObject response = executeRequest("/responses", buildResponsesRequest(preparedRequest));
        return extractResponseContent(response)
                .orElseThrow(() -> new IllegalStateException("OpenAI response did not contain translation output"));
    }

    private String executeChatCompletion(TranslationSafetyService.PreparedRequest preparedRequest) {
        JsonObject response = executeRequest("/chat/completions", buildChatCompletionRequest(preparedRequest));
        return extractChatCompletionContent(response)
                .map(this::configTranslationPayload)
                .orElseThrow(() -> new IllegalStateException("OpenAI chat completion did not contain translation output"));
    }

    private JsonObject buildResponsesRequest(TranslationSafetyService.PreparedRequest preparedRequest) {
        JsonObject request = new JsonObject();
        request.addProperty("model", providerConfig.model);
        request.addProperty("instructions", preparedRequest.promptPolicy());
        request.addProperty("input", preparedRequest.userPayload());
        request.addProperty("temperature", providerConfig.temperature);
        request.addProperty("max_output_tokens", MAX_OUTPUT_TOKENS);

        if (translationSafetyService.requiresStructuredOutput()) {
            JsonObject text = new JsonObject();
            text.add("format", buildResponsesStructuredFormat());
            request.add("text", text);
        }

        return request;
    }

    private JsonObject buildChatCompletionRequest(TranslationSafetyService.PreparedRequest preparedRequest) {
        JsonObject request = new JsonObject();
        request.addProperty("model", providerConfig.model);
        request.addProperty("temperature", providerConfig.temperature);
        request.addProperty("max_completion_tokens", MAX_OUTPUT_TOKENS);

        JsonArray messages = new JsonArray();
        messages.add(message("system", preparedRequest.promptPolicy()));
        messages.add(message("user", preparedRequest.userPayload()));
        request.add("messages", messages);

        return request;
    }

    private JsonObject buildResponsesStructuredFormat() {
        JsonObject format = new JsonObject();
        format.addProperty("type", "json_schema");
        format.addProperty("name", "translation");
        format.addProperty("strict", true);
        format.add("schema", buildTranslationSchema());
        return format;
    }

    private JsonObject buildTranslationSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject translationProperty = new JsonObject();
        translationProperty.addProperty("type", "string");

        JsonObject properties = new JsonObject();
        properties.add("translation", translationProperty);

        JsonArray required = new JsonArray();
        required.add("translation");

        schema.add("properties", properties);
        schema.add("required", required);
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    private JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    private JsonObject executeRequest(String path, JsonObject requestBody) {
        String requestJson = GSON.toJson(requestBody);
        int maxAttempts = Math.max(1, providerConfig.maxRetries + 1);
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<String> response = client.send(
                        buildHttpRequest(path, requestJson),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
                );

                int statusCode = response.statusCode();
                if (statusCode == 429) {
                    RateLimitFailureException failure = new RateLimitFailureException(extractErrorMessage(response.body(), statusCode));
                    if (attempt < maxAttempts) {
                        backoff(attempt, response);
                        lastFailure = failure;
                        continue;
                    }
                    throw failure;
                }

                if (statusCode >= 200 && statusCode < 300) {
                    return parseJsonObject(response.body(), "OpenAI response");
                }

                HttpFailureException failure = new HttpFailureException(statusCode, extractErrorMessage(response.body(), statusCode));
                if (isRetryableStatus(statusCode) && attempt < maxAttempts) {
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

    private HttpRequest buildHttpRequest(String path, String requestJson) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(normalizeBaseUrl(providerConfig.baseUrl) + path))
                .timeout(Duration.ofSeconds(providerConfig.timeoutSeconds))
                .header("Authorization", "Bearer " + providerConfig.apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8));

        String organization = blankToNull(providerConfig.organization);
        if (organization != null) {
            builder.header("OpenAI-Organization", organization);
        }

        String project = blankToNull(providerConfig.project);
        if (project != null) {
            builder.header("OpenAI-Project", project);
        }

        return builder.build();
    }

    private Optional<String> extractResponseContent(JsonObject response) {
        Optional<String> directOutput = getString(response, "output_text")
                .filter(content -> !content.isBlank());
        if (directOutput.isPresent()) {
            return directOutput;
        }

        JsonArray output = getArray(response, "output");
        if (output == null) {
            return Optional.empty();
        }

        for (JsonElement itemElement : output) {
            if (!itemElement.isJsonObject()) {
                continue;
            }

            JsonObject item = itemElement.getAsJsonObject();
            Optional<String> itemContent = extractTextContent(item.get("content"));
            if (itemContent.isPresent()) {
                return itemContent;
            }

            JsonObject message = getObject(item, "message");
            if (message != null) {
                Optional<String> messageContent = extractTextContent(message.get("content"));
                if (messageContent.isPresent()) {
                    return messageContent;
                }
            }
        }

        return Optional.empty();
    }

    private Optional<String> extractChatCompletionContent(JsonObject response) {
        JsonArray choices = getArray(response, "choices");
        if (choices == null || choices.isEmpty()) {
            return Optional.empty();
        }

        JsonElement firstChoice = choices.get(0);
        if (!firstChoice.isJsonObject()) {
            return Optional.empty();
        }

        JsonObject message = getObject(firstChoice.getAsJsonObject(), "message");
        if (message == null) {
            return Optional.empty();
        }

        return extractTextContent(message.get("content"));
    }

    private Optional<String> extractTextContent(JsonElement content) {
        if (content == null || content.isJsonNull()) {
            return Optional.empty();
        }

        if (content.isJsonPrimitive() && content.getAsJsonPrimitive().isString()) {
            String text = content.getAsString();
            return text == null || text.isBlank() ? Optional.empty() : Optional.of(text);
        }

        if (content.isJsonArray()) {
            for (JsonElement part : content.getAsJsonArray()) {
                Optional<String> text = extractTextPart(part);
                if (text.isPresent()) {
                    return text;
                }
            }
        }

        if (content.isJsonObject()) {
            return extractTextPart(content);
        }

        return Optional.empty();
    }

    private Optional<String> extractTextPart(JsonElement part) {
        if (part == null || !part.isJsonObject()) {
            return Optional.empty();
        }

        JsonObject partObject = part.getAsJsonObject();
        Optional<String> directText = getString(partObject, "text")
                .filter(text -> !text.isBlank());
        if (directText.isPresent()) {
            return directText;
        }

        JsonObject textObject = getObject(partObject, "text");
        if (textObject != null) {
            return getString(textObject, "value")
                    .filter(text -> !text.isBlank());
        }

        return Optional.empty();
    }

    private String configTranslationPayload(String content) {
        if (!translationSafetyService.requiresStructuredOutput()) {
            return content;
        }

        if (looksLikeStructuredTranslation(content)) {
            return content;
        }

        return "{\"translation\":%s}".formatted(toJsonString(content));
    }

    private boolean looksLikeStructuredTranslation(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }

        try {
            JsonObject root = GSON.fromJson(content, JsonObject.class);
            return root != null && root.has("translation") && !root.get("translation").isJsonNull();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String normalizedApiMode() {
        if (providerConfig.apiMode == null || providerConfig.apiMode.isBlank()) {
            return isNvidiaIntegrateApi() ? API_MODE_CHAT_COMPLETIONS : API_MODE_RESPONSES;
        }

        return switch (providerConfig.apiMode.trim().toLowerCase(Locale.ROOT)) {
            case "chat", "chat_completions", "chat-completions" -> API_MODE_CHAT_COMPLETIONS;
            case API_MODE_RESPONSES -> API_MODE_RESPONSES;
            default -> providerConfig.apiMode.trim().toLowerCase(Locale.ROOT);
        };
    }

    private boolean isNvidiaIntegrateApi() {
        String baseUrl = providerConfig.baseUrl;
        return baseUrl != null && baseUrl.contains("integrate.api.nvidia.com");
    }

    private JsonObject parseJsonObject(String rawJson, String description) {
        try {
            JsonElement element = JsonParser.parseString(rawJson);
            if (!element.isJsonObject()) {
                throw new IllegalStateException(description + " is not a JSON object");
            }
            return element.getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IllegalStateException(description + " is not valid JSON", exception);
        }
    }

    private String extractErrorMessage(String responseBody, int statusCode) {
        if (responseBody != null && !responseBody.isBlank()) {
            try {
                JsonObject root = parseJsonObject(responseBody, "OpenAI error response");
                JsonObject error = getObject(root, "error");
                if (error != null) {
                    Optional<String> message = getString(error, "message");
                    if (message.isPresent()) {
                        return message.get();
                    }
                }
            } catch (RuntimeException ignored) {
                // fall through to generic message
            }
        }

        return "HTTP " + statusCode;
    }

    private Optional<String> getString(JsonObject object, String memberName) {
        if (object == null || memberName == null || !object.has(memberName)) {
            return Optional.empty();
        }

        JsonElement element = object.get(memberName);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return Optional.empty();
        }

        return Optional.of(element.getAsString());
    }

    private JsonObject getObject(JsonObject object, String memberName) {
        if (object == null || memberName == null || !object.has(memberName)) {
            return null;
        }

        JsonElement element = object.get(memberName);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private JsonArray getArray(JsonObject object, String memberName) {
        if (object == null || memberName == null || !object.has(memberName)) {
            return null;
        }

        JsonElement element = object.get(memberName);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private boolean isRetryableStatus(int statusCode) {
        return switch (statusCode) {
            case 408, 409, 429, 500, 502, 503, 504 -> true;
            default -> false;
        };
    }

    private void backoff(int attempt, HttpResponse<String> response) {
        long delayMillis = retryDelayMillis(attempt, response);
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RequestInterruptedException(exception);
        }
    }

    private long retryDelayMillis(int attempt, HttpResponse<String> response) {
        long serverDelayMillis = parseRetryAfterMillis(response).orElse(0L);
        if (serverDelayMillis > 0L) {
            return Math.min(serverDelayMillis, MAX_RETRY_DELAY_MILLIS);
        }

        long exponentialDelayMillis = INITIAL_RETRY_DELAY_MILLIS * (1L << Math.max(0, attempt - 1));
        return Math.min(exponentialDelayMillis, MAX_RETRY_DELAY_MILLIS);
    }

    private Optional<Long> parseRetryAfterMillis(HttpResponse<String> response) {
        if (response == null) {
            return Optional.empty();
        }

        Optional<String> retryAfterHeader = response.headers().firstValue("Retry-After")
                .or(() -> response.headers().firstValue("retry-after"));
        if (retryAfterHeader.isEmpty()) {
            return Optional.empty();
        }

        String retryAfter = retryAfterHeader.get().trim();
        if (retryAfter.isEmpty()) {
            return Optional.empty();
        }

        try {
            long seconds = Long.parseLong(retryAfter);
            if (seconds <= 0L) {
                return Optional.empty();
            }
            return Optional.of(TimeUnit.SECONDS.toMillis(seconds));
        } catch (NumberFormatException ignored) {
            // Try HTTP date format next.
        }

        try {
            long delayMillis = Duration.between(
                    Instant.now(),
                    ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
            ).toMillis();
            return delayMillis > 0L ? Optional.of(delayMillis) : Optional.empty();
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }

    private String toJsonString(String value) {
        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return '"' + escaped + '"';
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api.openai.com/v1";
        }

        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
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
