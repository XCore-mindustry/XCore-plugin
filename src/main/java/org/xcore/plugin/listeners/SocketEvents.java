package org.xcore.plugin.listeners;

import arc.util.Http;
import arc.util.Log;
import arc.util.Strings;
import com.ospx.sock.EventBus.Request;
import com.ospx.sock.EventBus.Response;
import lombok.AllArgsConstructor;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.maps.Map;
import mindustry.net.Administration;
import mindustry.net.Packets;
import org.xcore.plugin.XcorePlugin;
import org.xcore.plugin.commands.DiscordCommands;
import org.xcore.plugin.modules.discord.Bot;
import org.xcore.plugin.utils.Find;
import org.xcore.plugin.utils.NetSock;
import org.xcore.plugin.utils.Utils;
import org.xcore.plugin.utils.models.BanData;
import org.xcore.plugin.utils.models.PlayerData;

import java.util.concurrent.atomic.AtomicInteger;

import static mindustry.Vars.*;
import static org.xcore.plugin.PluginVars.config;
import static org.xcore.plugin.PluginVars.database;
import static org.xcore.plugin.XcorePlugin.info;
import static useful.Bundle.send;

public class SocketEvents {
    public static void init() {
        if (NetSock.isSocketServer()) {
            Bot.connect();
            DiscordCommands.init();

            NetSock.subscribe(MessageEvent.class,
                    e -> Bot.sendMessageEvent(e.authorName(), e.message(), e.server()));
            NetSock.subscribe(ServerActionEvent.class,
                    e -> Bot.getServerLogChannel(e.server()).ifPresent(c -> c.createMessage(e.message()).subscribe()));
            NetSock.subscribe(PlayerJoinLeaveEvent.class,
                    e -> Bot.sendJoinLeaveEventMessage(e.playerName(), e.server(), e.join()));
            NetSock.subscribe(AdminRequestEvent.class, e -> Bot.sendAdminRequestEvent(e.pid(), e.server()));
            NetSock.subscribe(BanData.class, Bot::sendBan);

        } else {
            NetSock.subscribe(DiscordMessageEvent.class, e -> {
                if (!e.server().equals(config.server))
                    return;

                XcorePlugin.sendMessageFromDiscord(e.authorName(), e.message());
            });
        }

        NetSock.subscribe(MapsListRequest.class, request -> {
            if (!request.server.equals(config.server))
                return;

            NetSock.respond(request, new MapsListResponse(maps.customMaps().map(Map::plainName).toArray(String.class)));
        });

        NetSock.subscribe(MapRemoveRequest.class, request -> {
            if (!request.server.equals(config.server))
                return;

            var map = Utils.findMap(request.map);
            if (map != null) {
                maps.removeMap(map);
                maps.reload();
            }

            NetSock.respond(request, new MapRemoveResponse(map == null ?
                    "Map not found" :
                    "Succcesfully removed map " + map.plainName()
            ));
            if (map != null) info("Removed map @", map.plainName());
        });

        NetSock.subscribe(RemoveAdmin.class, e -> {
            var info = Find.playerInfo(e.uuid);
            var player = Find.playerByUuid(e.uuid);

            if (info == null || !info.admin)
                return;
            if (player != null) player.admin = false;

            netServer.admins.unAdminPlayer(e.uuid);
            info("Removed admin: @", info.plainLastName());
        });

        NetSock.subscribe(AdminRequestConfirmEvent.class, e -> {
            if (!e.server.equals(config.server))
                return;

            var info = Find.playerInfo(e.uuid);
            var player = Find.playerByUuid(e.uuid);

            if (info == null)
                return;
            if (player != null) {
                player.admin = true;
                PlayerData data = database.getCached(e.uuid);
                data.getAdminData().adminConfirmed = true;
                send(player, "commands.login.confirmed");
            }

            netServer.admins.adminPlayer(e.uuid, info.adminUsid);
            info("Confirmed admin request: @", info.plainLastName());
        });
        NetSock.subscribe(GlobalChatEvent.class, e -> {
            Call.sendMessage(Strings.format("[royal][[[orange]GLOBAL [lightgray](from [accent]@[])[] @[]]: [white]@", e.server, e.authorName, e.message));
            Log.infoTag("GLOBAL-" + e.server, Strings.stripColors(e.authorName) + ": " + e.message);
        });
        NetSock.subscribe(KickBannedPlayer.class, e -> Groups.player
                .each(p -> p.uuid().equals(e.uuid) || p.ip().equals(e.ip), p -> p.kick(Packets.KickReason.banned)));
        NetSock.subscribe(PardonPlayer.class, e -> {
            Administration.PlayerInfo info = netServer.admins.getInfoOptional(e.uuid());

            if (info != null) {
                info.lastKicked = 0;
                netServer.admins.kickedIPs.remove(info.lastIP);
                info("Pardoned player: @", info.plainLastName());
            }
        });
        NetSock.subscribe(SyncPlayerData.class, e -> {
            if (database.cachedPlayerData.containsKey(e.data().uuid)) {
                database.setCached(e.data());
                info("Synced player data: @ (@)", e.data().nickname, e.data().uuid);
            }
        });
        NetSock.subscribe(ReloadPlayerDataCache.class, e -> {
            database.reloadCache();
            info("Reloaded player data cache.");
        });

        NetSock.subscribe(LoadMaps.class, e -> {
            if (!config.server.equals(e.server))
                return;

            AtomicInteger counter = new AtomicInteger();
            for (String url : e.urls) {
                Http.get(url)
                        .error(Log::err)
                        .submit(result -> {
                            var split = url.split("/");
                            var fileName = split[split.length - 1];
                            customMapDirectory.child(fileName).writeBytes(result.getResult());

                            if (counter.incrementAndGet() == e.urls.length) {
                                maps.reload();
                                info("Loaded @ maps.", e.urls.length);
                            }
                        });
            }
        });

        NetSock.post(new ServerActionEvent("Server loaded", config.server));
    }

    public record MessageEvent(String authorName, String message, String server) {
    }

    public record ServerActionEvent(String message, String server) {
    }

    public record PlayerJoinLeaveEvent(String playerName, String server, Boolean join) {
    }

    public record GlobalChatEvent(String authorName, String message, String server) {
    }

    public record DiscordMessageEvent(String authorName, String message, String server) {
    }

    public record AdminRequestEvent(int pid, String server) {
    }

    public record AdminRequestConfirmEvent(String uuid, String server) {
    }

    public record KickBannedPlayer(String uuid, String ip) {
    }

    public record SyncPlayerData(PlayerData data) {
    }

    public static class ReloadPlayerDataCache {
    }

    public record LoadMaps(String[] urls, String server) {
    }

    public record PardonPlayer(String uuid) {
    }

    public record RemoveAdmin(String uuid) {
    }

    @AllArgsConstructor
    public static class MapsListRequest extends Request<MapsListResponse> {
        public String server;
    }

    @AllArgsConstructor
    public static class MapsListResponse extends Response {
        public String[] maps;
    }

    @AllArgsConstructor
    public static class MapRemoveRequest extends Request<MapRemoveResponse> {
        public String server, map;
    }

    @AllArgsConstructor
    public static class MapRemoveResponse extends Response {
        public String result;
    }
}