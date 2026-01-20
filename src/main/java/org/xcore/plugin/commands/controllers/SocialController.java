package org.xcore.plugin.commands.controllers;

import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.xcore.plugin.infra.commands.annotation.*;
import org.xcore.plugin.infra.commands.context.CommandContext;
import org.xcore.plugin.listeners.SocketEvents;
import org.xcore.plugin.utils.NetSock;

import static com.ospx.flubundle.Bundle.*;
import static org.xcore.plugin.PluginVars.*;
import static org.xcore.plugin.utils.Find.findTranslatorLanguage;

@SuppressWarnings("unused")
public class SocialController {

    @MuteCheck
    @Command(name = "t", params = "<message...>")
    public void teamChat(CommandContext<Player> ctx) {
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
    @MinPlayTime(minutes = 240, errorKey = "error-globalchat-total-playtime")
    @Command(name = "g", params = "<message...>")
    public void globalChat(CommandContext<Player> ctx) {
        String message = ctx.args()[0];

        NetSock.post(new SocketEvents.GlobalChatEvent(
                ctx.player().coloredName(),
                message,
                config.server
        ));

        NetSock.post(new SocketEvents.MessageEvent(
                ctx.player().plainName(),
                "[" + config.server + "] " + message.replace("`", "*"),
                "global"
        ));
    }

    @Command(name = "discord")
    public void discord(CommandContext<Player> ctx) {
        Call.openURI(ctx.player().con, discordUrl);
    }

    @Command(name = "tr", params = "<language/auto/off>")
    public void translator(CommandContext<Player> ctx) {
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

        ctx.send("commands-tr-success", args(
                "translatorLanguage", translatorLanguages.get(data.translatorLanguage)
        ));

        data.save();
    }
}