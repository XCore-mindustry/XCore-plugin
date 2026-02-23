package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.cloud.annotation.PlayTimeLimit;
import org.xcore.plugin.cloud.annotation.RequiresMuteCheck;
import org.xcore.plugin.cloud.annotation.RequiresPlayTime;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.localization.TranslatorLanguagesProvider;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.service.NetworkService;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class SocialController implements CloudClientController {

    private final PlayerDataRepository playerDataRepository;
    private final SessionService sessionService;
    private final NetworkService network;
    private final Config config;
    private final GlobalConfig globalConfig;
    private final TranslatorLanguagesProvider translatorLanguagesProvider;

    @Inject
    public SocialController(PlayerDataRepository playerDataRepository,
                            SessionService sessionService,
                            NetworkService network,
                            Config config,
                            GlobalConfig globalConfig,
                            TranslatorLanguagesProvider translatorLanguagesProvider) {
        this.playerDataRepository = playerDataRepository;
        this.sessionService = sessionService;
        this.network = network;
        this.config = config;
        this.globalConfig = globalConfig;
        this.translatorLanguagesProvider = translatorLanguagesProvider;
    }

    @RequiresMuteCheck
    @Command("t <message>")
    public void teamChat(XCoreSender sender, @Argument("message") @Greedy String message) {

        Player author = sender.player();

        Groups.player.each(
                other -> other.team() == author.team(),
                p -> {
                    Session session = sessionService.get(p.uuid());
                    if (session == null || session.data == null) return;
                    String formatted = session.locale().format(
                            "commands-t-chat", args(
                                    "color", author.team().color,
                                    "name", author.coloredName(),
                                    "message", message
                            ));
                    p.sendMessage(formatted, author);
                }
        );
    }

    @RequiresMuteCheck
    @RequiresPlayTime(PlayTimeLimit.GLOBAL_CHAT)
    @Command("g <message>")
    public void globalChat(XCoreSender sender, @Argument("message") @Greedy String message) {
        Session session = sessionService.get(sender.player().uuid());
        if (session == null || session.data == null) return;

        network.post(new SocketEvents.GlobalChatEvent(
                session.player.coloredName(),
                message,
                config.server
        ));

        network.post(new SocketEvents.MessageEvent(
                session.player.plainName(),
                "[" + config.server + "] " + message.replace("`", "*"),
                "global"
        ));
    }

    @Command("discord")
    public void discord(XCoreSender sender) {
        Call.openURI(sender.player().con, globalConfig.discordUrl);
    }

    @Command("tr <language>")
    public void translator(XCoreSender sender, @Argument(value = "language", parserName = "language") String language) {
        Session session = sessionService.get(sender.player().uuid());
        if (session == null || session.data == null) return;
        PlayerData data = session.data;
        Localization local = session.locale();

        String input = language.toLowerCase();

        if (input.equals("off")) {
            data.translatorLanguage = "off";
            local.send("commands-tr-off", args());
            playerDataRepository.save(data);
            return;
        }

        if (input.equals("auto")) {
            var lang = findTranslatorLanguage(session.player.locale);
            data.translatorLanguage = (lang == null) ? "en" : lang;
        } else {
            data.translatorLanguage = input;
        }

        String langName = translatorLanguagesProvider.getLanguages().get(data.translatorLanguage);
        local.send("commands-tr-success", args(
                "translatorLanguage", langName != null ? langName : data.translatorLanguage
        ));

        playerDataRepository.save(data);
    }

    private String findTranslatorLanguage(String locale) {
        if (locale == null) return null;
        return translatorLanguagesProvider.getLanguages().orderedKeys().find(locale::startsWith);
    }
}
