package org.xcore.plugin;

import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.mod.Plugin;
import org.xcore.plugin.commands.ClientCommands;
import org.xcore.plugin.commands.ServerCommands;
import org.xcore.plugin.comp.Config;
import org.xcore.plugin.comp.Database;
import org.xcore.plugin.comp.ServersConfig;
import org.xcore.plugin.features.Console;

import static org.xcore.plugin.PluginVars.config;

@SuppressWarnings("unused")
public class XcorePlugin extends Plugin {
    public static boolean isSocketServer;
    @Override
    public void init() {
        Config.load();
        Console.load();
        ServersConfig.load();
        if (config.isMiniPvP()) {
            Database.load();
            Timer.schedule(() -> {
                if (Groups.player.isEmpty()) return;

                var builder = new StringBuilder();
                var sorted = Database.cachedPlayerData.copy().values().toSeq().sort().reverse();
                sorted.truncate(10);

                builder.append("[blue]Leaderboard\n\n");
                for (int i = 0; i < sorted.size; i++) {
                    var data = sorted.get(i);
                    if (data.wins != 0) {
                        builder.append("[orange]").append(i + 1)
                                .append(". ")
                                .append(data.nickname)
                                .append(":[cyan] ")
                                .append(data.wins).append(" []wins\n");
                    }
                }
                Groups.player.each(player -> Call.infoPopup(player.con, builder.toString(), 5f, 8, 0, 2, 50, 0));
            }, 0f, 5f);
            Vars.netServer.chatFormatter = (player, message) -> player != null ? "[coral][[[cyan]" + Database.cachedPlayerData.get(player.uuid()).wins + " [sky]#[white] " + player.coloredName() + "[coral]]: [white]" + message : message;
        }
        Listeners.load();
        info("Plugin loaded");
    }
    @Override
    public void registerClientCommands(CommandHandler handler) {
        ClientCommands.register(handler);
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        ServerCommands.register(handler);
    }

    public static void info(String text, Object... values) {
        Log.infoTag("XCore", Strings.format(text, values));
    }
    public static void err(String text, Object... values) {
        Log.errTag("XCore", Strings.format(text, values));
    }
    public static void discord(String text, Object... values) {
        Log.infoTag("Discord", Strings.format(text, values));
    }
    public static void sendMessageFromDiscord(String authorName, String message) {
        discord("@: @", authorName, message);
        Call.sendMessage(Strings.format("[blue][Discord][] @: @", authorName, message));
    }
}
