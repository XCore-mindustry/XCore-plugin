package org.xcore.plugin;

import arc.Core;
import arc.net.Server;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Reflect;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.core.Version;
import mindustry.game.Gamemode;
import mindustry.gen.AdminRequestCallPacket;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.maps.Map;
import mindustry.maps.Maps.MapProvider;
import mindustry.mod.Plugin;
import mindustry.net.Administration;
import mindustry.net.ArcNetProvider;
import mindustry.net.NetworkIO;
import mindustry.net.Packets;
import org.xcore.plugin.commands.ClientCommands;
import org.xcore.plugin.commands.ServerCommands;
import org.xcore.plugin.listeners.NetEvents;
import org.xcore.plugin.listeners.PluginEvents;
import org.xcore.plugin.modules.*;
import org.xcore.plugin.modules.hexed.MiniHexed;
import org.xcore.plugin.modules.Config;
import org.xcore.plugin.modules.Database;
import org.xcore.plugin.modules.GlobalConfig;
import useful.Bundle;

import java.nio.ByteBuffer;

import static mindustry.Vars.*;
import static mindustry.Vars.state;
import static org.xcore.plugin.PluginVars.config;
import static org.xcore.plugin.utils.Utils.writeString;
import static org.xcore.plugin.utils.Utils.getAvailableMaps;

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
        MiniPvP.init();
        MiniHexed.init();
        LastStanding.init();
        PluginEvents.init();
        AdminModIntegration.init();
        Translator.init();
        Bundle.load(XcorePlugin.class);

        ArcNetProvider provider = Reflect.get(Vars.net, "provider");
        Server server = Reflect.get(provider, "server");

        server.setDiscoveryHandler((address, handler) -> {
            String name = Administration.Config.serverName.string();
            String description = !Administration.Config.desc.string().equals("off") ? Administration.Config.desc.string() : "";
            String map = state.map.name();

            ByteBuffer buffer = ByteBuffer.allocate(500);

            writeString(buffer, name, 100);
            writeString(buffer, map, 64);

            buffer.putInt(Core.settings.getInt("totalPlayers", Groups.player.size()));
            buffer.putInt(state.wave);
            buffer.putInt(Version.build);
            writeString(buffer, Version.type);

            buffer.put((byte) state.rules.mode().ordinal());
            buffer.putInt(config.playerLimit > 0 ? config.getNoAdminPlayerLimit() : 0);

            writeString(buffer, description, 100);
            if (state.rules.modeName != null) {
                writeString(buffer, state.rules.modeName, 50);
            }

            buffer.position(0);
            handler.respond(buffer);
        });

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