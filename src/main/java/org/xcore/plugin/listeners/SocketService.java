package org.xcore.plugin.listeners;

import arc.util.Http;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Structs;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.maps.Map;
import mindustry.net.Administration;
import mindustry.net.Packets;
import mindustry.server.ServerControl;
import org.xcore.plugin.XcorePlugin;
import org.xcore.plugin.modules.Config;
import org.xcore.plugin.modules.bundles.BundleService;
import org.xcore.plugin.modules.database.DatabaseService;
import org.xcore.plugin.modules.discord.DiscordService;
import org.xcore.plugin.modules.maps.MapService;
import org.xcore.plugin.modules.network.NetworkService;
import org.xcore.plugin.utils.FindService;
import org.xcore.plugin.utils.models.BanData;

import java.util.concurrent.atomic.AtomicInteger;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.*;
import static org.xcore.plugin.XcorePlugin.info;

@Singleton
public class SocketService {

    private final DatabaseService database;
    private final NetworkService network;
    private final Config config;
    private final FindService find;
    private final DiscordService discordService;
    private final BundleService bundleService;
    private final MapService mapService;

    @Inject
    public SocketService(DatabaseService database, NetworkService network, Config config,
                         FindService find, DiscordService discordService, BundleService bundleService,
                         MapService mapService
    ) {
        this.database = database;
        this.network = network;
        this.config = config;
        this.find = find;
        this.discordService = discordService;
        this.bundleService = bundleService;
        this.mapService = mapService;
    }

    @PostConstruct
    public void init() {
        if (network.isSocketServer()) {
            network.subscribe(SocketEvents.MessageEvent.class, e ->
                    discordService.sendMessageEvent(e.authorName(), e.message(), e.server()));

            network.subscribe(SocketEvents.ServerActionEvent.class, e ->
                    discordService.getServerLogChannel(e.server()).ifPresent(c -> discordService.sendMessage(c, e.message())));

            network.subscribe(SocketEvents.PlayerJoinLeaveEvent.class, e ->
                    discordService.sendConnectionEvent(e.playerName(), e.server(), e.join()));

            network.subscribe(SocketEvents.AdminRequestEvent.class, e ->
                    discordService.sendAdminRequestEvent(e.pid(), e.server()));

            network.subscribe(BanData.class, discordService::sendBan);

        } else {
            network.subscribe(SocketEvents.DiscordMessageEvent.class, e -> {
                if (!e.server().equals(config.server)) return;
                XcorePlugin.sendMessageFromDiscord(e.authorName(), e.message());
            });
        }

        network.subscribe(SocketEvents.MapsListRequest.class, request -> {
            if (!request.server.equals(config.server)) return;
            network.respond(request, new SocketEvents.MapsListResponse(
                    maps.customMaps().map(Map::plainName).toArray(String.class)));
        });

        network.subscribe(SocketEvents.MapRemoveRequest.class, request -> {
            if (!request.server.equals(config.server)) return;

            var map = mapService.findMap(request.map);
            if (map != null) {
                maps.removeMap(map);
                maps.reload();
            }

            network.respond(request, new SocketEvents.MapRemoveResponse(
                    map == null ? "Map not found" : "Successfully removed map " + map.plainName()));

            if (map != null) info("Removed map @", map.plainName());
        });

        network.subscribe(SocketEvents.RemoveAdmin.class, e -> {
            var info = find.playerInfo(e.uuid());
            var player = find.playerByUuid(e.uuid());

            if (info == null || !info.admin) return;

            if (player != null) player.admin = false;

            netServer.admins.unAdminPlayer(e.uuid());
            info("Removed admin: @", info.plainLastName());
        });

        network.subscribe(SocketEvents.AdminRequestConfirmEvent.class, e -> {
            if (!e.server().equals(config.server)) return;

            var info = find.playerInfo(e.uuid());
            var player = find.playerByUuid(e.uuid());

            if (info == null) return;

            if (player != null) {
                player.admin = true;
                bundleService.send(player, "commands-login-confirmed", args());
            }

            netServer.admins.adminPlayer(e.uuid(), info.adminUsid);
            info("Confirmed admin request: @", info.plainLastName());
        });

        network.subscribe(SocketEvents.GlobalChatEvent.class, e -> {
            Call.sendMessage(Strings.format("[royal][[[orange]GLOBAL [lightgray](from [accent]@[])[] @[]]: [white]@", e.server(), e.authorName(), e.message()));
            Log.infoTag("GLOBAL-" + e.server(), Strings.stripColors(e.authorName()) + ": " + e.message());
        });

        network.subscribe(SocketEvents.KickBannedPlayer.class, e -> Groups.player
                .each(p -> p.uuid().equals(e.uuid()) || p.ip().equals(e.ip()), p -> p.kick(Packets.KickReason.banned)));

        network.subscribe(SocketEvents.ExecuteCommand.class, e -> {
            if (e.expectServers() != null && Structs.contains(e.expectServers(), config.server)) return;

            Log.infoTag("ExecuteCommandEvent", "Executing command: " + e.command());
            ServerControl.instance.handleCommandString(e.command());
        });

        network.subscribe(SocketEvents.PardonPlayer.class, e -> {
            Administration.PlayerInfo info = netServer.admins.getInfoOptional(e.uuid());

            if (info != null) {
                info.lastKicked = 0;
                netServer.admins.kickedIPs.remove(info.lastIP);
                info("Pardoned player: @", info.plainLastName());
            }
        });

        network.subscribe(SocketEvents.SyncPlayerData.class, e -> {
            if (database.cachedPlayerData.containsKey(e.data().uuid)) {
                database.setCached(e.data());
                info("Synced player data: @ (@)", e.data().nickname, e.data().uuid);
            }
        });

        network.subscribe(SocketEvents.ReloadPlayerDataCache.class, e -> {
            database.reloadCache();
            info("Reloaded player data cache.");
        });

        network.subscribe(SocketEvents.LoadMapsV2.class, e -> {
            if (!config.server.equals(e.server())) return;

            AtomicInteger counter = new AtomicInteger();
            for (SocketEvents.FileURL file : e.urls()) {
                Http.get(file.url())
                        .error(Log::err)
                        .submit(result -> {
                            customMapDirectory.child(file.filename()).writeBytes(result.getResult());

                            if (counter.incrementAndGet() == e.urls().length) {
                                maps.reload();
                                info("Loaded @ maps.", e.urls().length);
                            }
                        });
            }
        });

        network.post(new SocketEvents.ServerActionEvent("Server loaded", config.server));
    }
}
