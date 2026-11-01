package org.xcore.plugin.localization;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.service.TranslationSafetyService;

import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

final class OpenAIRequestFactory {
    static final String API_MODE_RESPONSES = "responses";
    static final String API_MODE_CHAT_COMPLETIONS = "chat_completions";

    private final TomlSecretsConfig.TranslationSection.ProviderConfig providerConfig;
    private final TranslationSafetyService translationSafetyService;

    OpenAIRequestFactory(TomlSecretsConfig.TranslationSection.ProviderConfig providerConfig,
                         TranslationSafetyService translationSafetyService) {
        this.providerConfig = providerConfig;
        this.translationSafetyService = translationSafetyService;
    }

    String normalizedApiMode() {
        if (providerConfig.apiMode == null || providerConfig.apiMode.isBlank()) {
            return isNvidiaIntegrateApi() ? API_MODE_CHAT_COMPLETIONS : API_MODE_RESPONSES;
        }

        return switch (providerConfig.apiMode.trim().toLowerCase(Locale.ROOT)) {
            case "chat", "chat_completions", "chat-completions" -> API_MODE_CHAT_COMPLETIONS;
            case API_MODE_RESPONSES -> API_MODE_RESPONSES;
            default -> providerConfig.apiMode.trim().toLowerCase(Locale.ROOT);
        };
    }

    JsonObject buildRequestBody(String apiMode, TranslationSafetyService.PreparedRequest preparedRequest) {
        return switch (apiMode) {
            case API_MODE_RESPONSES -> buildResponsesRequest(preparedRequest);
            case API_MODE_CHAT_COMPLETIONS -> buildChatCompletionRequest(preparedRequest);
            default -> throw new IllegalStateException("Unsupported OpenAI provider api mode: " + providerConfig.apiMode);
        };
    }

    HttpRequest buildHttpRequest(String path, String requestJson) {
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

    private JsonObject buildResponsesRequest(TranslationSafetyService.PreparedRequest preparedRequest) {
        JsonObject request = new JsonObject();
        request.addProperty("model", providerConfig.model);
        request.addProperty("instructions", preparedRequest.promptPolicy());
        request.addProperty("input", preparedRequest.userPayload());
        request.addProperty("temperature", providerConfig.temperature);
        request.addProperty("max_output_tokens", maxOutputTokens(preparedRequest));

        if (translationSafetyService.requiresStructuredOutput()) {
            JsonObject text = new JsonObject();
            text.add("format", buildResponsesStructuredFormat(preparedRequest));
            request.add("text", text);
        }

        return request;
    }

    private JsonObject buildChatCompletionRequest(TranslationSafetyService.PreparedRequest preparedRequest) {
        JsonObject request = new JsonObject();
        request.addProperty("model", providerConfig.model);
        request.addProperty("temperature", providerConfig.temperature);
        request.addProperty("max_completion_tokens", maxOutputTokens(preparedRequest));

        JsonArray messages = new JsonArray();
        messages.add(message("system", preparedRequest.promptPolicy()));
        messages.add(message("user", preparedRequest.userPayload()));
        request.add("messages", messages);

        return request;
    }

    private long maxOutputTokens(TranslationSafetyService.PreparedRequest preparedRequest) {
        return OpenAITranslationProvider.BASE_OUTPUT_TOKENS
                + OpenAITranslationProvider.OUTPUT_TOKENS_PER_TARGET * preparedRequest.targetLanguages().size();
    }

    private JsonObject buildResponsesStructuredFormat(TranslationSafetyService.PreparedRequest preparedRequest) {
        JsonObject format = new JsonObject();
        format.addProperty("type", "json_schema");
        format.addProperty("name", "translations");
        format.addProperty("strict", true);
        format.add("schema", buildTranslationSchema(preparedRequest));
        return format;
    }

    private JsonObject buildTranslationSchema(TranslationSafetyService.PreparedRequest preparedRequest) {
        JsonObject translationProperties = new JsonObject();
        JsonArray requiredTargets = new JsonArray();
        for (String target : preparedRequest.targetLanguages()) {
            translationProperties.add(target, stringSchema());
            requiredTargets.add(target);
        }

        JsonObject translations = objectSchema(translationProperties, requiredTargets);

        JsonObject properties = new JsonObject();
        properties.add("detectedLanguage", stringSchema());
        properties.add("translations", translations);

        JsonArray required = new JsonArray();
        required.add("detectedLanguage");
        required.add("translations");

        return objectSchema(properties, required);
    }

    private static JsonObject objectSchema(JsonObject properties, JsonArray required) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    private static JsonObject stringSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "string");
        return schema;
    }

    private JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    private boolean isNvidiaIntegrateApi() {
        String baseUrl = providerConfig.baseUrl;
        return baseUrl != null && baseUrl.contains("integrate.api.nvidia.com");
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api.openai.com/v1";
        }

        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
