package org.xcore.plugin.service;

import arc.func.Cons;
import arc.struct.StringMap;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.xcore.plugin.localization.TranslationResult;
import org.xcore.plugin.session.SessionService;

@Singleton
public class TranslatorService {
    private final SessionService sessionService;
    private final ChatFormatService chatFormatService;
    private final TranslationFallbackService translationFallbackService;

    @Inject
    public TranslatorService(SessionService sessionService,
                             ChatFormatService chatFormatService,
                             TranslationFallbackService translationFallbackService) {
        this.sessionService = sessionService;
        this.chatFormatService = chatFormatService;
        this.translationFallbackService = translationFallbackService;
    }

    public void translate(String text, String from, String to, Cons<String> result, Runnable error) {
        translationFallbackService.translate(new org.xcore.plugin.localization.TranslationProvider.Request(text, from, to), translationResult -> {
            if (translationResult instanceof TranslationResult.Success(var translatedText)) {
                result.get(translatedText);
                return;
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

            if (cache.containsKey(data.data.translatorLanguage)) {
                player.sendMessage(cache.get(data.data.translatorLanguage), author, text);
            } else translate(text, "auto", data.data.translatorLanguage, result -> {
                cache.put(data.data.translatorLanguage, message + " [white]([lightgray]" + result + "[])");
                player.sendMessage(cache.get(data.data.translatorLanguage), author, text);
            }, () -> player.sendMessage(message, author, text));
        }
    }
}
