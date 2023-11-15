package org.xcore.plugin;

import arc.Core;
import arc.net.Server;
import arc.util.*;
import mindustry.Vars;
import mindustry.core.Version;
import mindustry.gen.AdminRequestCallPacket;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.mod.Plugin;
import mindustry.net.Administration;
import mindustry.net.ArcNetProvider;
import mindustry.net.Packets;
import org.xcore.plugin.commands.ClientCommands;
import org.xcore.plugin.commands.ServerCommands;
import org.xcore.plugin.listeners.NetEvents;
import org.xcore.plugin.listeners.PluginEvents;
import org.xcore.plugin.listeners.SocketEvents;
import org.xcore.plugin.modules.*;
import org.xcore.plugin.modules.hexed.MiniHexed;
import org.xcore.plugin.utils.Find;
import org.xcore.plugin.utils.NetSock;
import org.xcore.plugin.utils.database.Database;
import useful.Bundle;

import java.nio.ByteBuffer;
import java.time.Duration;

import static mindustry.Vars.netServer;
import static mindustry.Vars.state;
import static org.xcore.plugin.PluginVars.*;
import static org.xcore.plugin.utils.Utils.writeString;
import static useful.Bundle.send;

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
        NetSock.init();
        Database.init();
        PluginEvents.init();
        SocketEvents.init();
        AdminModIntegration.init();
        Translator.init();
        MiniPvP.init();
        MiniHexed.init();
        LastStanding.init();
        Bundle.load(XcorePlugin.class);

        ArcNetProvider provider = Reflect.get(Vars.net, "provider");
        Server server = Reflect.get(provider, "server");
        server.setConnectFilter(NetEvents::connectFilter);
        final String[] footer = {""};

        server.setDiscoveryHandler((address, handler) -> {
            String name = Administration.Config.serverName.string();
            String description = Administration.Config.desc.string().equals("off") ? footer[0] : Administration.Config.desc.string() + footer[0];

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

            writeString(buffer, description, 200);
            if (state.rules.modeName != null) {
                writeString(buffer, state.rules.modeName, 50);
            }

            buffer.position(0);
            handler.respond(buffer);
        });
        netServer.admins.addChatFilter(NetEvents::chat);
        Vars.net.handleServer(AdminRequestCallPacket.class, NetEvents::adminRequest);
        Vars.net.handleServer(Packets.Connect.class, NetEvents::connect);
        Vars.net.handleServer(Packets.ConnectPacket.class, NetEvents::connectPacket);

        Timer.schedule(() -> {
            footer[0] = config.gameStartedTimer
                    ? "\n[green]Game started [accent]" + Duration.ofMillis(Time.millis() - gameStarted).toMinutes() + "[] minutes ago."
                    : "";
            database.cachedPlayerData.each((uuid, data) -> {
                var player = Find.playerByUuid(uuid);

                if (player == null) return;

                data.totalPlayTime++;
                switch (data.totalPlayTime) {
                    case votekickPlayTime -> send(player, "notification.votekick-playtime", votekickPlayTime);
                    case globalChatPlayTime -> send(player, "notification.global-chat-playtime", globalChatPlayTime);
                }

                data.save();
            });
        }, 0, 60);

        Console.init();
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
