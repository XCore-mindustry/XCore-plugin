package org.xcore.plugin.commands.controllers.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import org.xcore.plugin.infra.commands.annotation.Command;
import org.xcore.plugin.infra.commands.annotation.MinPlayTime;
import org.xcore.plugin.infra.commands.annotation.MuteCheck;
import org.xcore.plugin.infra.commands.annotation.PlayTimeLimit;
import org.xcore.plugin.infra.commands.context.ClientContext;
import org.xcore.plugin.listeners.SocketEvents;
import org.xcore.plugin.modules.Config;
import org.xcore.plugin.modules.GlobalConfig;
import org.xcore.plugin.modules.database.DatabaseService;
import org.xcore.plugin.modules.network.NetworkService;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class SocialController {

    private final DatabaseService database;
    private final NetworkService network;
    private final Config config;
    private final GlobalConfig globalConfig;
    private final TranslatorLanguagesProvider translatorLanguagesProvider;

    @Inject
    public SocialController(DatabaseService database, NetworkService network, Config config,
                            GlobalConfig globalConfig, TranslatorLanguagesProvider translatorLanguagesProvider) {
        this.database = database;
        this.network = network;
        this.config = config;
        this.globalConfig = globalConfig;
        this.translatorLanguagesProvider = translatorLanguagesProvider;
    }

    @MuteCheck
    @Command(name = "t", params = "<message...>")
    public void teamChat(ClientContext ctx) {
        Groups.player.each(
                other -> other.team() == ctx.player().team(),
                p -> p.sendMessage(ctx.format("commands-t-chat", args(
                        "color", ctx.player().team().color,
                        "name", ctx.player().coloredName(),
                        "message", ctx.args()[0]
                )), ctx.player())
        );
    }

    @MuteCheck
    @MinPlayTime(value = PlayTimeLimit.GLOBAL_CHAT, errorKey = "error-globalchat-total-playtime")
    @Command(name = "g", params = "<message...>")
    public void globalChat(ClientContext ctx) {
        String message = ctx.args()[0];

        network.post(new SocketEvents.GlobalChatEvent(
                ctx.player().coloredName(),
                message,
                config.server
        ));

        network.post(new SocketEvents.MessageEvent(
                ctx.player().plainName(),
                "[" + config.server + "] " + message.replace("`", "*"),
                "global"
        ));
    }

    @Command(name = "discord")
    public void discord(ClientContext ctx) {
        Call.openURI(ctx.player().con, globalConfig.discordUrl);
    }

    @Command(name = "tr", params = "<language/auto/off>")
    public void translator(ClientContext ctx) {
        var data = database.getCached(ctx.player().uuid());
        String input = ctx.arg(0).toLowerCase();

        if (input.equals("off")) {
            data.translatorLanguage = "off";
            ctx.send("commands-tr-off", args());
        } else if (input.equals("auto")) {
            var lang = findTranslatorLanguage(ctx.player().locale);
            data.translatorLanguage = (lang == null) ? "en" : lang;
        } else {
            var lang = findTranslatorLanguage(input);
            if (lang == null) {
                ctx.send("commands-tr-not-found", args());
                return;
            }
            data.translatorLanguage = lang;
        }

        String langName = translatorLanguagesProvider.getLanguages().get(data.translatorLanguage);
        ctx.send("commands-tr-success", args(
                "translatorLanguage", langName != null ? langName : data.translatorLanguage
        ));

        database.getPlayerDataRepository().save(data);
    }

    private String findTranslatorLanguage(String locale) {
        if (locale == null) return null;
        return translatorLanguagesProvider.getLanguages().orderedKeys().find(locale::startsWith);
    }
}