package org.xcore.plugin.modules;

import arc.func.Cons;
import arc.struct.StringMap;
import arc.util.Http;
import arc.util.Strings;
import arc.util.serialization.JsonReader;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.xcore.plugin.modules.database.DatabaseService;

import static mindustry.Vars.netServer;

@Singleton
public class TranslatorService {
    private static final JsonReader reader = new JsonReader();
    private final DatabaseService database;

    @Inject
    public TranslatorService(DatabaseService database) {
        this.database = database;
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

        database.cachedPlayerData.forEach(entry -> {
            var data = entry.value;
            var player = Groups.player.find(p -> p.uuid().equals(data.uuid));
            if (player == null || player == author) return;

            if (data.translatorLanguage.equals("off")) {
                player.sendMessage(message, author, text);
                return;
            }

            if (cache.containsKey(data.translatorLanguage)) {
                player.sendMessage(cache.get(data.translatorLanguage), author, text);
            } else translate(text, "auto", data.translatorLanguage, result -> {
                cache.put(data.translatorLanguage, message + " [white]([lightgray]" + result + "[])");
                player.sendMessage(cache.get(data.translatorLanguage), author, text);
            }, () -> player.sendMessage(message, author, text));
        });
    }
}
