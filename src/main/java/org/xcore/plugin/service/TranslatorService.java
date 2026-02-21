package org.xcore.plugin.service;

import arc.func.Cons;
import arc.struct.StringMap;
import arc.util.Http;
import arc.util.Strings;
import arc.util.serialization.JsonReader;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.xcore.plugin.session.SessionService;

import static mindustry.Vars.netServer;

@Singleton
public class TranslatorService {
    private static final JsonReader reader = new JsonReader();
    private final SessionService sessionService;

    @Inject
    public TranslatorService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public static void translate(String text, String from, String to, Cons<String> result, Runnable error) {
        Http.post("https://clients5.google.com/translate_a/t?client=dict-chrome-ex&dt=t",
                        "tl=" + to + "&sl=" + from + "&q=" + Strings.encode(text))
                .error(throwable -> error.run())
                .submit(response -> result.get(reader.parse(response.getResultAsString()).get(0).get(0).asString()));
    }

    public void translate(Player author, String text) {
        var cache = new StringMap();
        var message = netServer.chatFormatter.format(author, text);

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
