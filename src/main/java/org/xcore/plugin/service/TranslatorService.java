package org.xcore.plugin.service;

import arc.func.Cons;
import arc.struct.StringMap;
import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.localization.TranslationProvider;
import org.xcore.plugin.localization.TranslationResult;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.session.SessionService;

@Singleton
public class TranslatorService {
    private final Config config;
    private final SessionService sessionService;
    private final ChatFormatService chatFormatService;
    private final TranslationFallbackService translationFallbackService;
    private final TranslationCacheService translationCacheService;
    private final TranslationMetricsService translationMetricsService;

    @Inject
    public TranslatorService(Config config,
                             SessionService sessionService,
                             ChatFormatService chatFormatService,
                             TranslationFallbackService translationFallbackService,
                             TranslationCacheService translationCacheService,
                             TranslationMetricsService translationMetricsService) {
        this.config = config;
        this.sessionService = sessionService;
        this.chatFormatService = chatFormatService;
        this.translationFallbackService = translationFallbackService;
        this.translationCacheService = translationCacheService;
        this.translationMetricsService = translationMetricsService;
    }

    public void translate(String text, String from, String to, Cons<String> result, Runnable error) {
        translationMetricsService.incrementGlobal("requests_total");

        if (to == null || to.isBlank() || !translationFallbackService.supports(to)) {
            translationMetricsService.incrementGlobal("unsupported_language_total");
            Log.debug("[Translation] Rejecting translation request for unsupported target '@'", to);
            error.run();
            return;
        }

        String pipelineSignature = translationFallbackService.pipelineSignature();
        TranslationCacheService.CachedTranslation cachedTranslation = translationCacheService.get(from, to, text, pipelineSignature);
        if (cachedTranslation != null && cachedTranslation.translatedText() != null && !cachedTranslation.translatedText().isBlank()) {
            translationMetricsService.incrementGlobal("cache_hits_total");
            Log.debug("[Translation] Cache hit for '@' -> '@' via provider '@'",
                    from, to, cachedTranslation.providerId());
            result.get(cachedTranslation.translatedText());
            return;
        }

        translationMetricsService.incrementGlobal("cache_misses_total");
        Log.debug("[Translation] Cache miss for '@' -> '@'; pipeline='@'",
                from, to, pipelineSignature);
        TranslationProvider.Request request = new TranslationProvider.Request(text, from, to);
        translationFallbackService.translate(request, translationResult -> {
            if (translationResult instanceof TranslationResult.Success(var translatedText)) {
                translationCacheService.put(from, to, text, pipelineSignature, translatedText, "pipeline");
                Log.debug("[Translation] Pipeline translation succeeded for '@' -> '@'", from, to);
                result.get(translatedText);
                return;
            }

            translationMetricsService.incrementGlobal("original_message_fallback_total");
            if (translationResult instanceof TranslationResult.Failure(var failure)) {
                PLog.err("Translation pipeline failed for '@' -> '@': @", from, to, failure.reason());
            } else {
                PLog.err("Translation pipeline failed for '@' -> '@' with unknown reason", from, to);
            }
            error.run();
        });
    }

    public void translate(Player author, String text) {
        var cache = new StringMap();
        var message = chatFormatService.formatChat(author, text);

        for (var data : sessionService.getAllCached()) {
            var player = Groups.player.find(p -> p.uuid().equals(data.data.uuid));
            if (player == null || player == author) continue;

            if (data.data.translatorLanguage.equals("off")) {
                player.sendMessage(message, author, text);
                continue;
            }

             if (!translationFallbackService.supports(data.data.translatorLanguage)) {
                translationMetricsService.incrementGlobal("unsupported_language_total");
                Log.debug("[Translation] Player '@' has unsupported translator language '@'",
                        data.data.uuid, data.data.translatorLanguage);
                player.sendMessage(message, author, text);
                continue;
            }

            if (cache.containsKey(data.data.translatorLanguage)) {
                player.sendMessage(cache.get(data.data.translatorLanguage), author, text);
            } else translate(text, "auto", data.data.translatorLanguage, result -> {
                cache.put(data.data.translatorLanguage, message + " [white]([lightgray]" + result + "[])");
                player.sendMessage(cache.get(data.data.translatorLanguage), author, text);
            }, () -> {
                if (config.translation.preserveOriginalMessageOnFailure) {
                    player.sendMessage(message, author, text);
                }
            });
        }
    }
}
