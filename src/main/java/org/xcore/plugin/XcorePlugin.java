package org.xcore.plugin;

import arc.Events;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.AdminRequestCallPacket;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.mod.Plugin;
import mindustry.net.Administration;
import mindustry.net.Packets;
import org.xcore.plugin.commands.ClientCommands;
import org.xcore.plugin.commands.ServerCommands;
import org.xcore.plugin.comp.Config;
import org.xcore.plugin.comp.Database;
import org.xcore.plugin.comp.ServersConfig;
import org.xcore.plugin.features.Console;
import org.xcore.plugin.listeners.NetHandlers;
import org.xcore.plugin.listeners.PluginEvents;

import static mindustry.Vars.*;
import static org.xcore.plugin.PluginVars.config;
import static org.xcore.plugin.PluginVars.serverCommands;

@SuppressWarnings("unused")
public class XcorePlugin extends Plugin {
    public static boolean isSocketServer;
    @Override
    public void init() {
        Config.load();
        ServerCommands.register(serverCommands);
        Console.load();
        ServersConfig.load();
        if (config.isMiniPvP()) {
            Database.load();
            Timer.schedule(() -> {
                if (Groups.player.isEmpty()) return;
                Groups.player.each(player -> Call.infoPopup(player.con, Utils.getLeaderboard(), 5f, 8, 0, 2, 50, 0));
            }, 0f, 5f);

            Vars.netServer.chatFormatter = (player, message) -> player != null ? "[coral][[[cyan]" + Database.cachedPlayerData.get(player.uuid()).rating + " [sky]#[white] " + player.coloredName() + "[coral]]: [white]" + message : message;
        }
        PluginEvents.load();

        Vars.net.handleServer(AdminRequestCallPacket.class, NetHandlers::adminRequest);
        maps.setMapProvider((mode, map) -> maps.customMaps().random(map));

        info("Plugin loaded");
    }
    @Override
    public void registerClientCommands(CommandHandler handler) {
        ClientCommands.register(handler);
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        serverCommands = handler;
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