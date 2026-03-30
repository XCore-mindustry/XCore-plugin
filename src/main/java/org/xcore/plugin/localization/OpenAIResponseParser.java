package org.xcore.plugin.localization;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.xcore.plugin.service.TranslationSafetyService;

import java.util.Optional;

final class OpenAIResponseParser {
    private static final Gson GSON = new Gson();

    private final TranslationSafetyService translationSafetyService;

    OpenAIResponseParser(TranslationSafetyService translationSafetyService) {
        this.translationSafetyService = translationSafetyService;
    }

    String extractTranslation(String apiMode, JsonObject response) {
        return switch (apiMode) {
            case OpenAIRequestFactory.API_MODE_RESPONSES -> extractResponseContent(response)
                    .orElseThrow(() -> new IllegalStateException("OpenAI response did not contain translation output"));
            case OpenAIRequestFactory.API_MODE_CHAT_COMPLETIONS -> extractChatCompletionContent(response)
                    .map(this::configTranslationPayload)
                    .orElseThrow(() -> new IllegalStateException("OpenAI chat completion did not contain translation output"));
            default -> throw new IllegalStateException("Unsupported OpenAI provider api mode: " + apiMode);
        };
    }

    JsonObject parseJsonObject(String rawJson, String description) {
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

    String extractErrorMessage(String responseBody, int statusCode) {
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

    private String toJsonString(String value) {
        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return '"' + escaped + '"';
    }
}
