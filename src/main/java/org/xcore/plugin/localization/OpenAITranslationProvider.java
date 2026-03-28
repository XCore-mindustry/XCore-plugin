package org.xcore.plugin.localization;

import arc.func.Cons;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIRetryableException;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.RateLimitException;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.service.TranslationSafetyService;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class OpenAITranslationProvider implements TranslationProvider {

    private static final String PROVIDER_TYPE = "openai";
    private static final long MAX_OUTPUT_TOKENS = 512L;
    private static final String API_MODE_RESPONSES = "responses";
    private static final String API_MODE_CHAT_COMPLETIONS = "chat_completions";
    private static final Gson GSON = new Gson();

    private final String providerId;
    private final GlobalConfig.TranslationProviderConfig providerConfig;
    private final TranslationSafetyService translationSafetyService;
    private final OpenAIClient client;
    private final Set<String> supportedLanguages;

    public OpenAITranslationProvider(String providerId,
                                     GlobalConfig.TranslationProviderConfig providerConfig,
                                     TranslationSafetyService translationSafetyService) {
        this.providerId = providerId;
        this.providerConfig = providerConfig;
        this.translationSafetyService = translationSafetyService;
        this.providerConfig.normalize();
        this.supportedLanguages = this.providerConfig.supportedLanguages.stream()
                .filter(language -> language != null && !language.isBlank())
                .map(language -> language.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        this.client = OpenAIOkHttpClient.builder()
                .apiKey(this.providerConfig.apiKey == null ? "" : this.providerConfig.apiKey)
                .baseUrl(normalizeBaseUrl(this.providerConfig.baseUrl))
                .maxRetries(this.providerConfig.maxRetries)
                .timeout(Duration.ofSeconds(this.providerConfig.timeoutSeconds))
                .organization(blankToNull(this.providerConfig.organization))
                .project(blankToNull(this.providerConfig.project))
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

        CompletableFuture.runAsync(() -> callback.get(executeTranslation(request)));
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
        } catch (RateLimitException exception) {
            return TranslationResult.failure(TranslationFailure.unavailable(name(), "translation rate limit exceeded"));
        } catch (OpenAIServiceException | OpenAIIoException | OpenAIRetryableException exception) {
            return TranslationResult.failure(TranslationFailure.unavailable(name(), sanitizeFailureReason(exception)));
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
        StructuredResponse<TranslationResponse> response = client.responses().create(buildResponsesRequest(preparedRequest));
        return extractResponseContent(response);
    }

    private String executeChatCompletion(TranslationSafetyService.PreparedRequest preparedRequest) {
        ChatCompletion response = client.chat().completions().create(buildChatCompletionRequest(preparedRequest));
        return extractChatCompletionContent(response)
                .map(this::configTranslationPayload)
                .orElseThrow(() -> new IllegalStateException("OpenAI chat completion did not contain translation output"));
    }

    private StructuredResponseCreateParams<TranslationResponse> buildResponsesRequest(TranslationSafetyService.PreparedRequest preparedRequest) {
        return ResponseCreateParams.builder()
                .model(providerConfig.model)
                .instructions(preparedRequest.promptPolicy())
                .input(preparedRequest.userPayload())
                .temperature(providerConfig.temperature)
                .maxOutputTokens(MAX_OUTPUT_TOKENS)
                .text(TranslationResponse.class)
                .build();
    }

    private ChatCompletionCreateParams buildChatCompletionRequest(TranslationSafetyService.PreparedRequest preparedRequest) {
        return ChatCompletionCreateParams.builder()
                .model(providerConfig.model)
                .temperature(providerConfig.temperature)
                .maxCompletionTokens(MAX_OUTPUT_TOKENS)
                .addMessage(ChatCompletionSystemMessageParam.builder()
                        .content(preparedRequest.promptPolicy())
                        .build())
                .addMessage(ChatCompletionUserMessageParam.builder()
                        .content(preparedRequest.userPayload())
                        .build())
                .build();
    }

    private String extractResponseContent(StructuredResponse<TranslationResponse> response) {
        return response.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .map(outputText -> outputText.translation)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("OpenAI response did not contain structured translation output"));
    }

    private Optional<String> extractChatCompletionContent(ChatCompletion response) {
        return response.choices().stream()
                .map(ChatCompletion.Choice::message)
                .flatMap(message -> message.content().stream())
                .filter(content -> content != null && !content.isBlank())
                .findFirst();
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

    private String sanitizeFailureReason(Exception exception) {
        return "translation request failed (%s)".formatted(exception.getClass().getSimpleName());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public static class TranslationResponse {
        public String translation;
    }
}
