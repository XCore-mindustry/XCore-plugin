package org.xcore.plugin;

import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import arc.util.serialization.JsonValue;
import fr.xpdustry.javelin.JavelinPlugin;
import mindustry.Vars;
import mindustry.game.Gamemode;
import mindustry.gen.AdminRequestCallPacket;
import mindustry.gen.Call;
import mindustry.maps.Map;
import mindustry.maps.Maps.MapProvider;
import mindustry.mod.Plugin;
import mindustry.net.Packets;
import org.xcore.plugin.commands.ClientCommands;
import org.xcore.plugin.commands.ServerCommands;
import org.xcore.plugin.listeners.NetEvents;
import org.xcore.plugin.listeners.PluginEvents;
import org.xcore.plugin.menus.TeamSelectMenu;
import org.xcore.plugin.modules.*;
import org.xcore.plugin.modules.discord.Bot;
import org.xcore.plugin.modules.models.BanData;

import java.util.concurrent.TimeUnit;

import static mindustry.Vars.maps;
import static mindustry.Vars.netServer;
import static org.xcore.plugin.PluginVars.*;
import static org.xcore.plugin.Utils.getAvailableMaps;
import static org.xcore.plugin.Utils.temporaryBan;

@SuppressWarnings("unused")
public class XcorePlugin extends Plugin {

    public XcorePlugin() {
        Config.init();
        GlobalConfig.init();
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

    @Override
    public void init() {
        Database.init();
        Console.init();
        TeamSelectMenu.init();
        MiniPvP.init();
        MiniHexed.init();
        PluginEvents.init();
        Translator.init();
        maps.setMapProvider(new MapProvider() {
            public int lastMapID;

            @Override
            public Map next(Gamemode mode, Map previous) {
                var allmaps = getAvailableMaps();
                return allmaps.any() ? allmaps.get(lastMapID++ % allmaps.size) : null;
            }
        });
        netServer.admins.addChatFilter(NetEvents::chat);
        Vars.net.handleServer(AdminRequestCallPacket.class, NetEvents::adminRequest);
        Vars.net.handleServer(Packets.Connect.class, NetEvents::connect);
        Vars.net.handleServer(Packets.ConnectPacket.class, NetEvents::connectPacket);

        netServer.addPacketHandler("take_ban_data", (player, content) -> {
            if (!player.admin) return;

            JsonValue json = reader.parse(content);

            String uuid = json.get("uuid").asString();
            String ip = json.get("ip").asString();
            String name = json.get("name").asString();
            String reason = json.get("reason").asString();

            boolean skipToDiscord = json.get("skip_to_discord").asBoolean();
            short duration = json.get("duration").asShort();

            if (uuid == null || uuid.isBlank()) {
                player.sendMessage("UUID cannot be blank.");
                return;
            }

            if (reason == null || reason.isBlank()) {
                reason = "unknown";
            }

            if (skipToDiscord) {
                BanData ban = new BanData(uuid, ip, name, player.name, config.server);
                if (isSocketServer) {
                    Bot.sendBanEvent(ban);
                } else {
                    JavelinPlugin.getJavelinSocket().sendEvent(ban);
                }
                return;
            }

            if (duration == 0) {
                return;
            }

            BanData ban = new BanData(uuid, ip, name, player.name, reason, config.server, Time.millis() + TimeUnit.DAYS.toMillis(duration));
            if (isSocketServer) {
                temporaryBan(ban);
            } else {
                JavelinPlugin.getJavelinSocket().sendEvent(ban);
            }
        });

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
}