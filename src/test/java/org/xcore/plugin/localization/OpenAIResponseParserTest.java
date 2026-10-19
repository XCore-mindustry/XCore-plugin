package org.xcore.plugin.localization;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAIResponseParserTest {

    @Test
    @DisplayName("chat completions content is returned as is for validation")
    void extractTranslation_returnsChatCompletionContent() {
        OpenAIResponseParser parser = new OpenAIResponseParser();
        JsonObject response = parser.parseJsonObject(
                "{\"choices\":[{\"message\":{\"content\":\"Привет\"}}]}",
                "chat completion"
        );

        String translation = parser.extractTranslation(OpenAIRequestFactory.API_MODE_CHAT_COMPLETIONS, response);

        assertThat(translation).isEqualTo("Привет");
    }

    @Test
    @DisplayName("responses output_text is returned directly")
    void extractTranslation_returnsResponsesOutputText() {
        OpenAIResponseParser parser = new OpenAIResponseParser();
        JsonObject response = parser.parseJsonObject(
                "{\"output_text\":\"Hola\"}",
                "responses output"
        );

        String translation = parser.extractTranslation(OpenAIRequestFactory.API_MODE_RESPONSES, response);

        assertThat(translation).isEqualTo("Hola");
    }
}
