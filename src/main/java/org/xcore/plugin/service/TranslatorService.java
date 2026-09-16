package org.xcore.plugin.service;

import arc.func.Cons;
import arc.struct.StringMap;
import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.localization.TranslationProvider;
import org.xcore.plugin.localization.TranslationResult;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.session.SessionService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

@Singleton
public class TranslatorService {
    private final TomlXcoreConfig config;
    private final SessionService sessionService;
    private final ChatFormatService chatFormatService;
    private final ClientCompatibilityService clientCompatibilityService;
    private final TranslationFallbackService translationFallbackService;
    private final TranslationCacheService translationCacheService;
    private final TranslationMetricsService translationMetricsService;

    @Inject
    public TranslatorService(TomlXcoreConfig config,
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
        translationCacheService.getAsync(from, to, text, pipelineSignature)
                .exceptionally(cacheError -> {
                    Log.debug("[Translation] Cache lookup failed: @", unwrap(cacheError).getMessage());
                    return null;
                })
                .thenAccept(cachedTranslation -> {
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
                translationCacheService.putAsync(from, to, text, pipelineSignature, translatedText, "pipeline");
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
                });
    }

    private Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    public void translate(Player author, String text) {
        var message = chatFormatService.formatChat(author, text);
        Map<String, List<Player>> langToRecipients = new LinkedHashMap<>();

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

            langToRecipients.computeIfAbsent(data.data.translatorLanguage, k -> new ArrayList<>()).add(player);
        }

        for (var entry : langToRecipients.entrySet()) {
            String targetLang = entry.getKey();
            List<Player> recipients = entry.getValue();

            translate(text, "auto", targetLang, result -> {
                if (!hasMeaningfulTranslation(text, result)) {
                    for (Player player : recipients) {
                        player.sendMessage(message, author, text);
                    }
                    return;
                }

                String formattedTranslation = message + " [white]([lightgray]" + result + "[])";
                String compatText = buildCompatibilityText(text, result);
                for (Player player : recipients) {
                    player.sendMessage(formattedTranslation, author, compatText);
                }
            }, () -> {
                if (config.translation.preserveOriginalMessageOnFailure) {
                    for (Player player : recipients) {
                        player.sendMessage(message, author, text);
                    }
                }
            });
        }
    }

    private record TeamRecipient(Player player, String message, boolean foosCompatible) {}

    public void translateTeamChat(Player author, String text) {
        Map<String, List<TeamRecipient>> langToRecipients = new LinkedHashMap<>();

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

            langToRecipients.computeIfAbsent(session.data.translatorLanguage, k -> new ArrayList<>())
                    .add(new TeamRecipient(player, message, foosCompatible));
        }

        for (var entry : langToRecipients.entrySet()) {
            String targetLang = entry.getKey();
            List<TeamRecipient> recipients = entry.getValue();

            translate(text, "auto", targetLang, result -> {
                if (!hasMeaningfulTranslation(text, result)) {
                    for (var recipient : recipients) {
                        sendTeamChat(recipient.player, recipient.message, author, text, recipient.foosCompatible);
                    }
                    return;
                }

                String compatText = buildCompatibilityText(text, result);
                for (var recipient : recipients) {
                    sendTeamChat(recipient.player,
                            appendTranslation(recipient.message, result),
                            author,
                            compatText,
                            recipient.foosCompatible);
                }
            }, () -> {
                if (config.translation.preserveOriginalMessageOnFailure) {
                    for (var recipient : recipients) {
                        sendTeamChat(recipient.player, recipient.message, author, text, recipient.foosCompatible);
                    }
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
