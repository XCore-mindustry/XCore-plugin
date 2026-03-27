package org.xcore.plugin.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.localization.TranslationFailure;
import org.xcore.plugin.localization.TranslationProvider;
import org.xcore.plugin.localization.TranslationResult;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationFallbackServiceTest {

    @Test
    @DisplayName("translate returns primary provider result when first provider succeeds")
    void translate_returnsPrimaryProviderResult_whenFirstProviderSucceeds() {
        StubProvider primary = new StubProvider(
                "primary",
                true,
                TranslationResult.success("translated-primary")
        );
        StubProvider secondary = new StubProvider(
                "secondary",
                true,
                TranslationResult.success("translated-secondary")
        );
        TranslationFallbackService service = new TranslationFallbackService(List.of(primary, secondary));
        AtomicReference<TranslationResult> result = new AtomicReference<>();

        service.translate(new TranslationProvider.Request("hello", "auto", "ru"), result::set);

        assertThat(result.get())
                .isInstanceOfSatisfying(TranslationResult.Success.class,
                        success -> assertThat(success.translatedText()).isEqualTo("translated-primary"));
        assertThat(primary.calls).isEqualTo(1);
        assertThat(secondary.calls).isZero();
    }

    @Test
    @DisplayName("translate falls back to secondary provider when primary fails")
    void translate_fallsBackToSecondaryProvider_whenPrimaryFails() {
        StubProvider primary = new StubProvider(
                "primary",
                true,
                TranslationResult.failure(TranslationFailure.unavailable("primary", "timeout"))
        );
        StubProvider secondary = new StubProvider(
                "secondary",
                true,
                TranslationResult.success("translated-secondary")
        );
        TranslationFallbackService service = new TranslationFallbackService(List.of(primary, secondary));
        AtomicReference<TranslationResult> result = new AtomicReference<>();

        service.translate(new TranslationProvider.Request("hello", "auto", "ru"), result::set);

        assertThat(result.get())
                .isInstanceOfSatisfying(TranslationResult.Success.class,
                        success -> assertThat(success.translatedText()).isEqualTo("translated-secondary"));
        assertThat(primary.calls).isEqualTo(1);
        assertThat(secondary.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("translate returns fallback failure when all providers fail")
    void translate_returnsFallbackFailure_whenAllProvidersFail() {
        StubProvider primary = new StubProvider(
                "primary",
                true,
                TranslationResult.failure(TranslationFailure.unavailable("primary", "timeout"))
        );
        StubProvider secondary = new StubProvider(
                "secondary",
                true,
                TranslationResult.failure(TranslationFailure.unavailable("secondary", "unavailable"))
        );
        TranslationFallbackService service = new TranslationFallbackService(List.of(primary, secondary));
        AtomicReference<TranslationResult> result = new AtomicReference<>();

        service.translate(new TranslationProvider.Request("hello", "auto", "ru"), result::set);

        assertThat(result.get())
                .isInstanceOfSatisfying(TranslationResult.Failure.class, failure -> {
                    assertThat(failure.failure().providerName()).isEqualTo("fallback");
                    assertThat(failure.failure().reason()).isEqualTo("all translation providers failed");
                });
        assertThat(primary.calls).isEqualTo(1);
        assertThat(secondary.calls).isEqualTo(1);
    }

    private static final class StubProvider implements TranslationProvider {
        private final String name;
        private final boolean supported;
        private final TranslationResult result;
        private int calls;

        private StubProvider(String name, boolean supported, TranslationResult result) {
            this.name = name;
            this.supported = supported;
            this.result = result;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void translate(Request request, arc.func.Cons<TranslationResult> callback) {
            calls++;
            callback.get(result);
        }

        @Override
        public boolean supports(String languageCode) {
            return supported;
        }
    }
}
