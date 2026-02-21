package org.xcore.plugin.event.socket;

import arc.util.Log;
import arc.util.Structs;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Groups;
import mindustry.net.Administration;
import mindustry.net.Packets;
import mindustry.server.ServerControl;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.discord.DiscordLogBridge;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.model.BanData;
import org.xcore.plugin.service.FindService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.session.SessionService;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.netServer;
import static org.xcore.plugin.common.PLog.info;

@Singleton
public class ModerationSocketHandler {

    private final NetworkService network;
    private final SessionService sessionService;
    private final FindService find;
    private final DiscordLogBridge discordLogBridge;
    private final Config config;

    @Inject
    public ModerationSocketHandler(NetworkService network,
                                   SessionService sessionService,
                                   FindService find,
                                   DiscordLogBridge discordLogBridge,
                                   Config config) {
        this.network = network;
        this.sessionService = sessionService;
        this.find = find;
        this.discordLogBridge = discordLogBridge;
        this.config = config;
    }

    public void registerListeners() {
        network.subscribe(SocketEvents.KickBannedPlayer.class, e -> Groups.player
                .each(p -> p.uuid().equals(e.uuid()) || p.ip().equals(e.ip()), p -> p.kick(Packets.KickReason.banned)));

        network.subscribe(SocketEvents.AdminRequestConfirmEvent.class, e -> {
            if (!e.server().equals(config.server)) return;

            var info = find.playerInfo(e.uuid());
            var player = find.playerByUuid(e.uuid());

            if (info == null) return;

            if (player != null) {
                player.admin = true;
                var session = sessionService.get(player);
                if (session != null) {
                    session.locale().send("commands-login-confirmed", args());
                }
            }

            netServer.admins.adminPlayer(e.uuid(), info.adminUsid);
            info("Confirmed admin request: @", info.plainLastName());
        });

        network.subscribe(SocketEvents.RemoveAdmin.class, e -> {
            var info = find.playerInfo(e.uuid());
            var player = find.playerByUuid(e.uuid());

            if (info == null || !info.admin) return;

            if (player != null) player.admin = false;

            netServer.admins.unAdminPlayer(e.uuid());
            info("Removed admin: @", info.plainLastName());
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
            if (sessionService.get(e.data().uuid) != null) {
                sessionService.update(e.data());
                info("Synced player data: @ (@)", e.data().nickname, e.data().uuid);
            }
        });

        network.subscribe(SocketEvents.ReloadPlayerDataCache.class, _ -> {
            sessionService.reloadCache();
            info("Reloaded player data cache.");
        });

        network.subscribe(SocketEvents.ExecuteCommand.class, e -> {
            if (e.expectServers() != null && Structs.contains(e.expectServers(), config.server)) return;

            Log.infoTag("ExecuteCommandEvent", "Executing command: " + e.command());
            ServerControl.instance.handleCommandString(e.command());
        });

        if (network.isSocketServer()) {
            network.subscribe(BanData.class, discordLogBridge::sendBan);

            network.subscribe(SocketEvents.PlayerJoinLeaveEvent.class, e ->
                    discordLogBridge.sendConnectionEvent(e.playerName(), e.server(), e.join()));

            network.subscribe(SocketEvents.ServerActionEvent.class, e ->
                    discordLogBridge.getServerLogChannel(e.server()).ifPresent(c -> discordLogBridge.sendMessage(c, e.message())));

            network.subscribe(SocketEvents.AdminRequestEvent.class, e ->
                    discordLogBridge.sendAdminRequestEvent(e.pid(), e.server()));
        }
    }
}
