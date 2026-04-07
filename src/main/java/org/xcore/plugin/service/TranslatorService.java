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
    private final ClientCompatibilityService clientCompatibilityService;
    private final TranslationFallbackService translationFallbackService;
    private final TranslationCacheService translationCacheService;
    private final TranslationMetricsService translationMetricsService;

    @Inject
    public TranslatorService(Config config,
                             SessionService sessionService,
                             ChatFormatService chatFormatService,
                             ClientCompatibilityService clientCompatibilityService,
                             TranslationFallbackService translationFallbackService,
                             TranslationCacheService translationCacheService,
                             TranslationMetricsService translationMetricsService) {
        this.config = config;
        this.sessionService = sessionService;
        this.chatFormatService = chatFormatService;
        this.clientCompatibilityService = clientCompatibilityService;
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

        for (var data : sessionService.getAllCachedSnapshot()) {
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
                String translatedText = extractTranslatedText(cache.get(data.data.translatorLanguage));
                if (!hasMeaningfulTranslation(text, translatedText)) {
                    player.sendMessage(message, author, text);
                    continue;
                }

                player.sendMessage(cache.get(data.data.translatorLanguage), author,
                        buildCompatibilityText(text, translatedText));
            } else translate(text, "auto", data.data.translatorLanguage, result -> {
                if (!hasMeaningfulTranslation(text, result)) {
                    player.sendMessage(message, author, text);
                    return;
                }

                cache.put(data.data.translatorLanguage, message + " [white]([lightgray]" + result + "[])");
                player.sendMessage(cache.get(data.data.translatorLanguage), author,
                        buildCompatibilityText(text, result));
            }, () -> {
                if (config.translation.preserveOriginalMessageOnFailure) {
                    player.sendMessage(message, author, text);
                }
            });
        }
    }

    public void translateTeamChat(Player author, String text) {
        var cache = new StringMap();

        for (var session : sessionService.findByTeam(author.team())) {
            var player = session.player;
            if (player == null) continue;

            var message = chatFormatService.formatTeamChat(author, session.locale(), text);
            boolean foosCompatible = clientCompatibilityService.isLikelyFoosClient(player);
            if (player == author || session.data.translatorLanguage.equals("off")) {
                sendTeamChat(player, message, author, text, foosCompatible);
                continue;
            }

            if (!translationFallbackService.supports(session.data.translatorLanguage)) {
                translationMetricsService.incrementGlobal("unsupported_language_total");
                Log.debug("[Translation] Player '@' has unsupported translator language '@'",
                        session.data.uuid, session.data.translatorLanguage);
                sendTeamChat(player, message, author, text, foosCompatible);
                continue;
            }

            if (cache.containsKey(session.data.translatorLanguage)) {
                String translatedText = cache.get(session.data.translatorLanguage);
                if (!hasMeaningfulTranslation(text, translatedText)) {
                    sendTeamChat(player, message, author, text, foosCompatible);
                    continue;
                }

                sendTeamChat(player,
                        appendTranslation(message, translatedText),
                        author,
                        buildCompatibilityText(text, translatedText),
                        foosCompatible);
            } else translate(text, "auto", session.data.translatorLanguage, result -> {
                if (!hasMeaningfulTranslation(text, result)) {
                    sendTeamChat(player, message, author, text, foosCompatible);
                    return;
                }

                cache.put(session.data.translatorLanguage, result);
                sendTeamChat(player,
                        appendTranslation(message, result),
                        author,
                        buildCompatibilityText(text, result),
                        foosCompatible);
            }, () -> {
                if (config.translation.preserveOriginalMessageOnFailure) {
                    sendTeamChat(player, message, author, text, foosCompatible);
                }
            });
        }
    }

    private String appendTranslation(String message, String translatedText) {
        return message + " [white]([lightgray]" + translatedText + "[])";
    }

    private String buildCompatibilityText(String originalText, String translatedText) {
        if (!hasMeaningfulTranslation(originalText, translatedText)) {
            return originalText;
        }

        return originalText + " (" + translatedText + ")";
    }

    private boolean hasMeaningfulTranslation(String originalText, String translatedText) {
        String normalizedOriginal = normalizeForComparison(originalText);
        String normalizedTranslation = normalizeForComparison(translatedText);

        if (normalizedTranslation.isEmpty()) {
            return false;
        }

        return !normalizedTranslation.equalsIgnoreCase(normalizedOriginal);
    }

    private String normalizeForComparison(String text) {
        if (text == null) {
            return "";
        }

        return text.trim().replaceAll("\\s+", " ");
    }

    private String extractTranslatedText(String formattedMessage) {
        if (formattedMessage == null) {
            return "";
        }

        int suffixStart = formattedMessage.lastIndexOf(" [white]([lightgray]");
        if (suffixStart < 0) {
            return "";
        }

        int translatedStart = suffixStart + " [white]([lightgray]".length();
        int translatedEnd = formattedMessage.indexOf("[])", translatedStart);
        if (translatedEnd < 0 || translatedEnd <= translatedStart) {
            return "";
        }

        return formattedMessage.substring(translatedStart, translatedEnd);
    }

    private void sendTeamChat(Player player,
                              String formattedMessage,
                              Player author,
                              String rawMessage,
                              boolean foosCompatible) {
        if (foosCompatible) {
            player.sendMessage(formattedMessage, author, rawMessage);
            return;
        }

        player.sendMessage(formattedMessage, author);
    }
}
