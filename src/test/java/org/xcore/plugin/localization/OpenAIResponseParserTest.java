package org.xcore.plugin.localization;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.service.TranslationSafetyService;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAIResponseParserTest {

    @Test
    @DisplayName("chat completions plain text is wrapped when structured output is required")
    void extractTranslation_wrapsChatCompletionTextForStructuredOutput() {
        OpenAIResponseParser parser = new OpenAIResponseParser(translationSafetyService(true));
        JsonObject response = parser.parseJsonObject(
                "{\"choices\":[{\"message\":{\"content\":\"Привет\"}}]}",
                "chat completion"
        );

        String translation = parser.extractTranslation(OpenAIRequestFactory.API_MODE_CHAT_COMPLETIONS, response);

        assertThat(translation).isEqualTo("{\"translation\":\"Привет\"}");
    }

    @Test
    @DisplayName("responses output_text is returned directly")
    void extractTranslation_returnsResponsesOutputText() {
        OpenAIResponseParser parser = new OpenAIResponseParser(translationSafetyService(false));
        JsonObject response = parser.parseJsonObject(
                "{\"output_text\":\"Hola\"}",
                "responses output"
        );

        String translation = parser.extractTranslation(OpenAIRequestFactory.API_MODE_RESPONSES, response);

        assertThat(translation).isEqualTo("Hola");
    }

    private TranslationSafetyService translationSafetyService(boolean structuredOutputRequired) {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.translation.llm.structuredOutputRequired = structuredOutputRequired;
        return new TranslationSafetyService(config);
    }
}
