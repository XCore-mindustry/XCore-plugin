package org.xcore.plugin.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.localization.TranslationFailure;
import org.xcore.plugin.localization.TranslationOutcome;
import org.xcore.plugin.localization.TranslationProvider;
import org.xcore.plugin.localization.TranslationProviderPipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TranslationFallbackServiceTest {

    @Test
    @DisplayName("translate returns primary provider result when first provider succeeds")
    void translate_returnsPrimaryProviderResult_whenFirstProviderSucceeds() {
        StubProvider primary = new StubProvider("primary", Set.of(), Set.of());
        StubProvider secondary = new StubProvider("secondary", Set.of(), Set.of());
        TranslationFallbackService service = service(primary, secondary);

        TranslationOutcome outcome = translate(service, "ru");

        assertThat(outcome.translations()).containsEntry("ru", "primary:ru");
        assertThat(outcome.failures()).isEmpty();
        assertThat(primary.requests).containsExactly(List.of("ru"));
        assertThat(secondary.requests).isEmpty();
    }

    @Test
    @DisplayName("translate falls back per target: only failed targets reach the next provider")
    void translate_fallsBackPerTarget() {
        StubProvider primary = new StubProvider("primary", Set.of(), Set.of("de"));
        StubProvider secondary = new StubProvider("secondary", Set.of(), Set.of());
        TranslationFallbackService service = service(primary, secondary);

        TranslationOutcome outcome = translate(service, "ru", "de");

        assertThat(outcome.translations())
                .containsEntry("ru", "primary:ru")
                .containsEntry("de", "secondary:de");
        assertThat(outcome.failures()).isEmpty();
        assertThat(secondary.requests).containsExactly(List.of("de"));
    }

    @Test
    @DisplayName("translate returns fallback failure when all providers fail")
    void translate_returnsFallbackFailure_whenAllProvidersFail() {
        StubProvider primary = new StubProvider("primary", Set.of(), Set.of("ru"));
        StubProvider secondary = new StubProvider("secondary", Set.of(), Set.of("ru"));
        TranslationFallbackService service = service(primary, secondary);

        TranslationOutcome outcome = translate(service, "ru");

        assertThat(outcome.translations()).isEmpty();
        assertThat(outcome.failures().get("ru").reason()).isEqualTo("all translation providers failed");
    }

    @Test
    @DisplayName("translate returns unavailable failure when no providers are configured")
    void translate_returnsUnavailableFailure_whenNoProvidersAreConfigured() {
        TranslationFallbackService service = service();

        TranslationOutcome outcome = translate(service, "ru");

        assertThat(outcome.failures().get("ru").reason()).contains("no providers configured");
    }

    @Test
    @DisplayName("translate skips unsupported provider before using supported fallback")
    void translate_skipsUnsupportedProvider_beforeUsingSupportedFallback() {
        StubProvider primary = new StubProvider("primary", Set.of("ru"), Set.of());
        StubProvider secondary = new StubProvider("secondary", Set.of(), Set.of());
        TranslationFallbackService service = service(primary, secondary);

        TranslationOutcome outcome = translate(service, "ru", "de");

        assertThat(outcome.translations())
                .containsEntry("ru", "secondary:ru")
                .containsEntry("de", "primary:de");
        assertThat(primary.requests).containsExactly(List.of("de"));
        assertThat(secondary.requests).containsExactly(List.of("ru"));
    }

    @Test
    @DisplayName("a provider that throws fails its targets and the pipeline moves on")
    void translate_treatsThrowingProviderAsFailure() {
        TranslationProvider throwing = new TranslationProvider() {
            @Override
            public String name() {
                return "throwing";
            }

            @Override
            public CompletionStage<TranslationOutcome> translate(Request request) {
                throw new IllegalStateException("boom");
            }
        };
        StubProvider secondary = new StubProvider("secondary", Set.of(), Set.of());
        TranslationFallbackService service = service(throwing, secondary);

        TranslationOutcome outcome = translate(service, "ru");

        assertThat(outcome.translations()).containsEntry("ru", "secondary:ru");
    }

    @Test
    @DisplayName("pipeline signature reflects configured provider order")
    void pipelineSignature_reflectsConfiguredProviderOrder() {
        TranslationFallbackService service = service(
                new StubProvider("nvidia-mistral-small", Set.of(), Set.of()),
                new StubProvider("google", Set.of(), Set.of()));

        assertThat(service.pipelineSignature()).isEqualTo("nvidia-mistral-small:nvidia-mistral-small,google:google");
    }

    private static TranslationFallbackService service(TranslationProvider... providers) {
        return new TranslationFallbackService(
                new TranslationProviderPipeline(List.of(providers)),
                mock(TranslationMetricsService.class)
        );
    }

    private static TranslationOutcome translate(TranslationFallbackService service, String... targets) {
        return service.translate(new TranslationProvider.Request("hello", "auto", List.of(targets)))
                .toCompletableFuture()
                .join();
    }

    /** Translates every target to {@code name:target} except the ones it is told to fail. */
    private static final class StubProvider implements TranslationProvider {
        private final String name;
        private final Set<String> unsupported;
        private final Set<String> failing;
        private final List<List<String>> requests = new ArrayList<>();

        private StubProvider(String name, Set<String> unsupported, Set<String> failing) {
            this.name = name;
            this.unsupported = unsupported;
            this.failing = failing;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public CompletionStage<TranslationOutcome> translate(Request request) {
            requests.add(request.targetLanguages());
            Map<String, String> translations = new LinkedHashMap<>();
            Map<String, TranslationFailure> failures = new LinkedHashMap<>();
            for (String target : request.targetLanguages()) {
                if (failing.contains(target)) {
                    failures.put(target, TranslationFailure.unavailable(name, "failed"));
                } else {
                    translations.put(target, name + ":" + target);
                }
            }
            return CompletableFuture.completedFuture(new TranslationOutcome(null, translations, failures));
        }

        @Override
        public boolean supports(String languageCode) {
            return !unsupported.contains(languageCode);
        }
    }
}
