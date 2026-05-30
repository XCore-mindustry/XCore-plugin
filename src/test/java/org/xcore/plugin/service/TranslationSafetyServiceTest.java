package org.xcore.plugin.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.localization.TranslationProvider;
import org.xcore.plugin.localization.TranslationResult;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationSafetyServiceTest {

    @Test
    @DisplayName("prepare sanitizes control characters and protects formatting tokens")
    void prepare_sanitizesControlCharacters_andProtectsFormattingTokens() {
        TranslationSafetyService service = new TranslationSafetyService(config());
        TranslationProvider.Request request = new TranslationProvider.Request(
                "Hello \u0000[scarlet]world[] {player} %s",
                "auto",
                "ru"
        );

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
        TranslationProvider.Request request = new TranslationProvider.Request(
                "Hello [scarlet]world[]",
                "auto",
                "ru"
        );

        TranslationSafetyService.PreparedRequest preparedRequest = ((TranslationSafetyService.PreparationResult.Success)
                service.prepare(request, "openai-main")).preparedRequest();

        TranslationResult result = service.validate(
                "openai-main",
                preparedRequest,
                "{\"translation\":\"Привет ⟪XCORE_TOKEN_0⟫мир⟪XCORE_TOKEN_1⟫\"}"
        );

        assertThat(result)
                .isInstanceOfSatisfying(TranslationResult.Success.class,
                        success -> assertThat(success.translatedText()).isEqualTo("Привет [scarlet]мир[]"));
    }

    @Test
    @DisplayName("validate fails when structured output is missing translation field")
    void validate_fails_whenStructuredOutputMissesTranslationField() {
        TranslationSafetyService service = new TranslationSafetyService(config());
        TranslationProvider.Request request = new TranslationProvider.Request("hello", "auto", "ru");

        TranslationSafetyService.PreparedRequest preparedRequest = ((TranslationSafetyService.PreparationResult.Success)
                service.prepare(request, "openai-main")).preparedRequest();

        TranslationResult result = service.validate("openai-main", preparedRequest, "{\"text\":\"Привет\"}");

        assertThat(result)
                .isInstanceOfSatisfying(TranslationResult.Failure.class,
                        failure -> assertThat(failure.failure().reason()).contains("missing translation field"));
    }

    @Test
    @DisplayName("validate fails when protected token placement changes")
    void validate_fails_whenProtectedTokenPlacementChanges() {
        TranslationSafetyService service = new TranslationSafetyService(config());
        TranslationProvider.Request request = new TranslationProvider.Request(
                "Hello [scarlet]world[] and [green]friends[]",
                "auto",
                "ru"
        );

        TranslationSafetyService.PreparedRequest preparedRequest = ((TranslationSafetyService.PreparationResult.Success)
                service.prepare(request, "openai-main")).preparedRequest();

        TranslationResult result = service.validate(
                "openai-main",
                preparedRequest,
                "{\"translation\":\"Привет ⟪XCORE_TOKEN_0⟫мир[]\"}"
        );

        assertThat(result)
                .isInstanceOfSatisfying(TranslationResult.Failure.class,
                        failure -> assertThat(failure.failure().reason()).contains("protected token placement"));
    }
    private static TomlXcoreConfig config() {
        return new TomlXcoreConfig();
    }
}
