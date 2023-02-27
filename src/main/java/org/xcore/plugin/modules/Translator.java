package org.xcore.plugin.modules;

import arc.func.Cons;
import arc.struct.StringMap;
import arc.util.Http;
import arc.util.Strings;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.xcore.plugin.utils.Database;

import static mindustry.Vars.netServer;
import static org.xcore.plugin.PluginVars.reader;
import static org.xcore.plugin.PluginVars.translatorLanguages;

public class Translator {
    public static void init() {
        translatorLanguages.putAll(
                "ca", "Català",
                "id", "Indonesian",
                "da", "Dansk",
                "de", "Deutsch",
                "et", "Eesti",
                "en", "English",
                "es", "Español",
                "eu", "Euskara",
                "fil", "Filipino",
                "fr", "Français",
                "it", "Italiano",
                "lt", "Lietuvių",
                "hu", "Magyar",
                "nl", "Nederlands",
                "pl", "Polski",
                "pt", "Português",
                "ro", "Română",
                "fi", "Suomi",
                "sv", "Svenska",
                "vi", "Tiếng Việt",
                "tk", "Türkmen dili",
                "tr", "Türkçe",
                "cs", "Čeština",
                "be", "Беларуская",
                "bg", "Български",
                "ru", "Русский",
                "sr", "Српски",
                "uk_UA", "Українська",
                "th", "ไทย",
                "zh", "简体中文",
                "ja", "日本語",
                "ko", "한국어"
        );
    }

    public static void translate(String text, String from, String to, Cons<String> result, Runnable error) {
        Http.post("https://clients5.google.com/translate_a/t?client=dict-chrome-ex&dt=t", "tl=" + to + "&sl=" + from + "&q=" + Strings.encode(text))
                .error(throwable -> error.run())
                .submit(response -> result.get(reader.parse(response.getResultAsString()).get(0).get(0).asString()));
    }

    public static void translate(Player author, String text) {
        var cache = new StringMap();
        var message = netServer.chatFormatter.format(author, text);

        Database.cachedPlayerData.forEach(entry -> {
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
