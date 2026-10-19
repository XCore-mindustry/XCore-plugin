package org.xcore.plugin.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.localization.TranslationProvider;
import org.xcore.plugin.localization.TranslationOutcome;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationSafetyServiceTest {

    @Test
    @DisplayName("prepare sanitizes control characters and protects formatting tokens")
    void prepare_sanitizesControlCharacters_andProtectsFormattingTokens() {
        TranslationSafetyService service = new TranslationSafetyService(config());
        TranslationProvider.Request request = TranslationProvider.Request.single("Hello \u0000[scarlet]world[] {player} %s", "auto", "ru");

        TranslationSafetyService.PreparationResult result = service.prepare(request, "openai-main");

        assertThat(result)
                .isInstanceOfSatisfying(TranslationSafetyService.PreparationResult.Success.class, success -> {
                    TranslationSafetyService.PreparedRequest preparedRequest = success.preparedRequest();
                    assertThat(preparedRequest.userPayload()).doesNotContain("\u0000");
                    assertThat(preparedRequest.userPayload()).contains("⟪XCORE_TOKEN_0⟫");
                    assertThat(preparedRequest.userPayload()).contains("⟪XCORE_TOKEN_1⟫");
                    assertThat(preparedRequest.userPayload()).contains("⟪XCORE_TOKEN_2⟫");
                    assertThat(preparedRequest.promptPolicy()).contains("strict JSON");
                });
    }

    @Test
    @DisplayName("validate restores protected tokens from structured output")
    void validate_restoresProtectedTokens_fromStructuredOutput() {
        TranslationSafetyService service = new TranslationSafetyService(config());
        TranslationProvider.Request request = TranslationProvider.Request.single("Hello [scarlet]world[]", "auto", "ru");

        TranslationSafetyService.PreparedRequest preparedRequest = ((TranslationSafetyService.PreparationResult.Success)
                service.prepare(request, "openai-main")).preparedRequest();

        TranslationOutcome result = service.validate(
                "openai-main",
                preparedRequest,
                "{\"translation\":\"Привет ⟪XCORE_TOKEN_0⟫мир⟪XCORE_TOKEN_1⟫\"}",
                false
        );

        assertThat(result.translations()).containsEntry("ru", "Привет [scarlet]мир[]");
    }

    @Test
    @DisplayName("validate fails when structured output is missing translation field")
    void validate_fails_whenStructuredOutputMissesTranslationField() {
        TranslationSafetyService service = new TranslationSafetyService(config());
        TranslationProvider.Request request = TranslationProvider.Request.single("hello", "auto", "ru");

        TranslationSafetyService.PreparedRequest preparedRequest = ((TranslationSafetyService.PreparationResult.Success)
                service.prepare(request, "openai-main")).preparedRequest();

        TranslationOutcome result = service.validate("openai-main", preparedRequest, "{\"text\":\"Привет\"}", false);

        assertThat(result.translations()).isEmpty();
        assertThat(result.failures().get("ru").reason()).contains("not valid translation JSON");
    }

    @Test
    @DisplayName("validate fails when protected token placement changes")
    void validate_fails_whenProtectedTokenPlacementChanges() {
        TranslationSafetyService service = new TranslationSafetyService(config());
        TranslationProvider.Request request = TranslationProvider.Request.single("Hello [scarlet]world[] and [green]friends[]", "auto", "ru");

        TranslationSafetyService.PreparedRequest preparedRequest = ((TranslationSafetyService.PreparationResult.Success)
                service.prepare(request, "openai-main")).preparedRequest();

        TranslationOutcome result = service.validate(
                "openai-main",
                preparedRequest,
                "{\"translation\":\"Привет ⟪XCORE_TOKEN_0⟫мир[]\"}",
                false
        );

        assertThat(result.failures().get("ru").reason()).contains("protected token placement");
    }
    @Test
    @DisplayName("validate reads every target and the detected language from multi-target output")
    void validate_readsEveryTarget_fromMultiTargetOutput() {
        TranslationSafetyService service = new TranslationSafetyService(config());
        TranslationProvider.Request request = new TranslationProvider.Request("hello {player}", "auto", List.of("ru", "de", "fr"));

        TranslationSafetyService.PreparedRequest preparedRequest = ((TranslationSafetyService.PreparationResult.Success)
                service.prepare(request, "openai-main")).preparedRequest();

        assertThat(preparedRequest.userPayload()).contains("\"targetLanguages\":[\"ru\",\"de\",\"fr\"]");
        assertThat(preparedRequest.promptPolicy()).contains("ru, de, fr").contains("detectedLanguage");

        TranslationOutcome result = service.validate("openai-main", preparedRequest, """
                ```json
                {"detectedLanguage":"en","translations":{"ru":"привет ⟪XCORE_TOKEN_0⟫","de":"hallo ⟪XCORE_TOKEN_0⟫"}}
                ```""", false);

        assertThat(result.detectedSourceLanguage()).isEqualTo("en");
        assertThat(result.translations())
                .containsEntry("ru", "привет {player}")
                .containsEntry("de", "hallo {player}")
                .doesNotContainKey("fr");
        assertThat(result.failures().get("fr").reason()).contains("missing translation for fr");
    }

    @Test
    @DisplayName("validate accepts plain text for a single target only when allowed")
    void validate_acceptsPlainText_onlyWhenAllowed() {
        TranslationSafetyService service = new TranslationSafetyService(config());
        TranslationSafetyService.PreparedRequest preparedRequest = ((TranslationSafetyService.PreparationResult.Success)
                service.prepare(TranslationProvider.Request.single("hello", "auto", "ru"), "openai-main")).preparedRequest();

        assertThat(service.validate("openai-main", preparedRequest, "Привет", true).translations())
                .containsEntry("ru", "Привет");
        assertThat(service.validate("openai-main", preparedRequest, "Привет", false).failures())
                .containsKey("ru");
    }

    private static TomlXcoreConfig config() {
        return new TomlXcoreConfig();
    }
}
