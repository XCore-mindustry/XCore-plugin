package org.xcore.plugin.translation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.localization.TranslationFailure;
import org.xcore.plugin.localization.TranslationOutcome;
import org.xcore.plugin.localization.TranslationProvider;
import org.xcore.plugin.service.TranslationCacheService;
import org.xcore.plugin.service.TranslationFallbackService;
import org.xcore.plugin.service.TranslationMetricsService;
import org.xcore.plugin.translation.ChatTranslation.Reason;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatTranslationServiceTest {

    private final TomlXcoreConfig config = new TomlXcoreConfig();
    private final TranslationFallbackService pipeline = mock(TranslationFallbackService.class);
    private final TranslationCacheService redisCache = mock(TranslationCacheService.class);
    private final List<TranslationProvider.Request> requests = new ArrayList<>();
    private final List<CompletableFuture<TranslationOutcome>> pending = new ArrayList<>();
    private boolean answerImmediately = true;

    @BeforeEach
    void setUp() {
        when(pipeline.supports(anyString())).thenReturn(true);
        when(pipeline.pipelineSignature()).thenReturn("google:google");
        when(redisCache.getAsync(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(pipeline.translate(any(TranslationProvider.Request.class))).thenAnswer(invocation -> {
            TranslationProvider.Request request = invocation.getArgument(0);
            requests.add(request);
            CompletableFuture<TranslationOutcome> future = new CompletableFuture<>();
            if (answerImmediately) {
                future.complete(outcome(request, "en"));
            } else {
                pending.add(future);
            }
            return future;
        });
    }

    private ChatTranslationService service() {
        config.translation.normalize();
        return new ChatTranslationService(config, pipeline, redisCache, new TranslationMemoryCache(config),
                new TranslationPrefilter(config), new TranslationAdmission(config), mock(TranslationMetricsService.class));
    }

    /** Translates every target to {@code [target] text}. */
    private static TranslationOutcome outcome(TranslationProvider.Request request, String detected) {
        Map<String, String> translations = new LinkedHashMap<>();
        request.targetLanguages().forEach(target -> translations.put(target, "[" + target + "] " + request.text()));
        return new TranslationOutcome(detected, translations, Map.of());
    }

    private static ChatTranslation result(Map<String, CompletableFuture<ChatTranslation>> results, String target) {
        assertThat(results.get(target)).isDone();
        return results.get(target).join();
    }

    @Test
    @DisplayName("all targets of one message go to the pipeline in a single request")
    void translate_sendsAllTargetsInOneRequest() {
        var results = service().translate("author", "hello there", List.of("ru", "de", "ru"));

        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst().targetLanguages()).containsExactly("ru", "de");
        assertThat(result(results, "ru")).isEqualTo(new ChatTranslation.Translated("[ru] hello there"));
        assertThat(result(results, "de")).isEqualTo(new ChatTranslation.Translated("[de] hello there"));
    }

    @Test
    @DisplayName("colour tags are stripped before translation and text without letters is skipped")
    void translate_cleansAndPrefiltersText() {
        ChatTranslationService service = service();

        var colored = service.translate("author", "[scarlet]hello[] there", List.of("ru"));
        var numbers = service.translate("author", "123 456 !!", List.of("ru"));
        var universal = service.translate("author", "GG!", List.of("ru"));

        assertThat(requests).extracting(TranslationProvider.Request::text).containsExactly("hello there");
        assertThat(result(colored, "ru")).isEqualTo(new ChatTranslation.Translated("[ru] hello there"));
        assertThat(result(numbers, "ru")).isEqualTo(new ChatTranslation.Untranslated(Reason.NOT_NEEDED));
        assertThat(result(universal, "ru")).isEqualTo(new ChatTranslation.Untranslated(Reason.NOT_NEEDED));
    }

    @Test
    @DisplayName("a repeated message is served from memory without another pipeline call")
    void translate_usesMemoryCache() {
        ChatTranslationService service = service();

        service.translate("author", "hello there", List.of("ru"));
        var second = service.translate("other", "hello there", List.of("ru"));

        assertThat(requests).hasSize(1);
        assertThat(result(second, "ru")).isEqualTo(new ChatTranslation.Translated("[ru] hello there"));
    }

    @Test
    @DisplayName("the detected source language is skipped for later messages with the same text")
    void translate_skipsDetectedSourceLanguage() {
        ChatTranslationService service = service();

        service.translate("author", "hello there", List.of("ru"));
        var second = service.translate("author", "hello there", List.of("en-US", "de"));

        assertThat(result(second, "en-US")).isEqualTo(new ChatTranslation.Untranslated(Reason.SAME_LANGUAGE));
        assertThat(requests).hasSize(2);
        assertThat(requests.get(1).targetLanguages()).containsExactly("de");
    }

    @Test
    @DisplayName("a translation that equals the original is reported as same language")
    void translate_reportsUnchangedTranslationAsSameLanguage() {
        when(pipeline.translate(any(TranslationProvider.Request.class))).thenReturn(CompletableFuture.completedFuture(
                new TranslationOutcome(null, Map.of("ru", "  hello   there "), Map.of())));

        var results = service().translate("author", "hello there", List.of("ru"));

        assertThat(result(results, "ru")).isEqualTo(new ChatTranslation.Untranslated(Reason.SAME_LANGUAGE));
    }

    @Test
    @DisplayName("concurrent identical messages share one request")
    void translate_sharesInFlightRequest() {
        answerImmediately = false;
        ChatTranslationService service = service();

        var first = service.translate("author", "hello there", List.of("ru"));
        var second = service.translate("other", "hello there", List.of("ru"));

        assertThat(requests).hasSize(1);
        assertThat(first.get("ru")).isNotDone();
        pending.getFirst().complete(outcome(requests.getFirst(), "en"));

        assertThat(result(first, "ru")).isEqualTo(new ChatTranslation.Translated("[ru] hello there"));
        assertThat(result(second, "ru")).isEqualTo(result(first, "ru"));
        assertThat(service.inFlightCount()).isZero();
    }

    @Test
    @DisplayName("an author over the budget gets untranslated messages")
    void translate_rateLimitsAuthor() {
        config.translation.limits.authorBurst = 1;
        config.translation.limits.authorRefillPerMinute = 1;
        ChatTranslationService service = service();

        service.translate("spammer", "hello there", List.of("ru"));
        var second = service.translate("spammer", "another message", List.of("ru"));
        var otherAuthor = service.translate("calm", "another message", List.of("ru"));

        assertThat(result(second, "ru")).isEqualTo(new ChatTranslation.Untranslated(Reason.RATE_LIMITED));
        assertThat(result(otherAuthor, "ru")).isInstanceOf(ChatTranslation.Translated.class);
    }

    @Test
    @DisplayName("requests beyond the concurrency cap are rejected, not queued")
    void translate_rejectsWhenOverloaded() {
        config.translation.limits.maxConcurrentRequests = 1;
        answerImmediately = false;
        ChatTranslationService service = service();

        var first = service.translate("a", "hello there", List.of("ru"));
        var second = service.translate("b", "another message", List.of("ru"));

        assertThat(result(second, "ru")).isEqualTo(new ChatTranslation.Untranslated(Reason.OVERLOADED));
        pending.getFirst().complete(outcome(requests.getFirst(), "en"));
        assertThat(result(first, "ru")).isInstanceOf(ChatTranslation.Translated.class);

        var third = service.translate("b", "third message", List.of("ru"));
        assertThat(third.get("ru")).isNotDone();
    }

    @Test
    @DisplayName("provider failures and a throwing pipeline complete as failed, never hang")
    void translate_completesFailures() {
        when(pipeline.translate(any(TranslationProvider.Request.class))).thenReturn(CompletableFuture.completedFuture(
                TranslationOutcome.failed(List.of("ru"), TranslationFailure.unavailable("google", "down"))));
        ChatTranslationService service = service();

        var failed = service.translate("a", "hello there", List.of("ru"));

        when(pipeline.translate(any(TranslationProvider.Request.class))).thenThrow(new IllegalStateException("boom"));
        var thrown = service.translate("a", "another message", List.of("ru"));

        assertThat(result(failed, "ru")).isEqualTo(new ChatTranslation.Untranslated(Reason.FAILED));
        assertThat(result(thrown, "ru")).isEqualTo(new ChatTranslation.Untranslated(Reason.FAILED));
        assertThat(service.inFlightCount()).isZero();

        var afterFailures = service.translate("a", "third message", List.of("ru"));
        assertThat(afterFailures.get("ru")).isDone();
    }

    @Test
    @DisplayName("a Redis hit skips the pipeline and fills the memory cache")
    void translate_usesRedisCache() {
        when(redisCache.getAsync("auto", "ru", "hello there", "google:google")).thenReturn(CompletableFuture.completedFuture(
                new TranslationCacheService.CachedTranslation("привет", "pipeline", 0L)));
        ChatTranslationService service = service();

        var first = service.translate("a", "hello there", List.of("ru"));
        service.translate("a", "hello there", List.of("ru"));

        assertThat(result(first, "ru")).isEqualTo(new ChatTranslation.Translated("привет"));
        verify(pipeline, never()).translate(any(TranslationProvider.Request.class));
        verify(redisCache, org.mockito.Mockito.times(1)).getAsync("auto", "ru", "hello there", "google:google");
    }

    @Test
    @DisplayName("unsupported targets and disabled translation complete immediately")
    void translate_completesUnsupportedAndDisabled() {
        when(pipeline.supports("xx")).thenReturn(false);
        ChatTranslationService service = service();

        assertThat(result(service.translate("a", "hello there", List.of("xx")), "xx"))
                .isEqualTo(new ChatTranslation.Untranslated(Reason.UNSUPPORTED_LANGUAGE));

        config.translation.enabled = false;
        assertThat(result(service.translate("a", "hello there", List.of("ru")), "ru"))
                .isEqualTo(new ChatTranslation.Untranslated(Reason.DISABLED));
    }

    @Test
    @DisplayName("language codes compare by their primary subtag")
    void primaryLanguage_comparesPrimarySubtag() {
        assertThat(ChatTranslationService.sameLanguage("zh-CN", "zh")).isTrue();
        assertThat(ChatTranslationService.sameLanguage("uk_UA", "UK")).isTrue();
        assertThat(ChatTranslationService.sameLanguage("en", "ru")).isFalse();
    }
}
