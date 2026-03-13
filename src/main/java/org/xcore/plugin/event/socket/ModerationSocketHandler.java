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
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.FindService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.service.DiscordAdminAccessService;
import org.xcore.plugin.session.SessionService;

import java.util.HashSet;
import java.util.function.Consumer;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.netServer;
import static org.xcore.plugin.common.PLog.info;

@Singleton
public class ModerationSocketHandler {

    private final NetworkService network;
    private final SessionService sessionService;
    private final FindService find;
    private final Config config;
    private final PlayerDisplayService playerDisplayService;
    private final DiscordAdminAccessService discordAdminAccessService;

    @Inject
    public ModerationSocketHandler(NetworkService network,
                                   SessionService sessionService,
                                   FindService find,
                                   Config config,
                                   PlayerDisplayService playerDisplayService,
                                   DiscordAdminAccessService discordAdminAccessService) {
        this.network = network;
        this.sessionService = sessionService;
        this.find = find;
        this.config = config;
        this.playerDisplayService = playerDisplayService;
        this.discordAdminAccessService = discordAdminAccessService;
    }

    public void registerListeners() {
        network.subscribe(SocketEvents.KickBannedPlayer.class, e -> Groups.player
                .each(p -> p.uuid().equals(e.uuid()) || p.ip().equals(e.ip()), p -> p.kick(Packets.KickReason.banned)));

        network.subscribe(SocketEvents.DiscordAdminAccessChanged.class, e -> {
            if (e.admin()) {
                if (discordAdminAccessService.applyDiscordAdminAccess(e.playerUuid(), e.discordId(), e.discordUsername())) {
                    info("Granted discord admin access: @", e.playerUuid());
                }
                return;
            }

            if (discordAdminAccessService.revokeDiscordAdminAccess(e.playerUuid())) {
                info("Revoked discord admin access: @", e.playerUuid());
            }
        });

        network.subscribe(SocketEvents.PardonPlayer.class, e -> {
            Administration.PlayerInfo info = netServer.admins.getInfoOptional(e.uuid());

            if (info != null) {
                info.lastKicked = 0;
                netServer.admins.kickedIPs.remove(info.lastIP);
                info("Pardoned player: @", info.plainLastName());
            }
        });

        network.subscribe(SocketEvents.PlayerCustomNicknameChanged.class, e -> updatePlayerSession(
                e.uuid(),
                data -> data.customNickname = e.customNickname(),
                false,
                "custom nickname"
        ));

        network.subscribe(SocketEvents.PlayerActiveBadgeChanged.class, e -> updatePlayerSession(
                e.uuid(),
                data -> data.activeBadge = e.activeBadge(),
                true,
                "active badge"
        ));

        network.subscribe(SocketEvents.PlayerBadgeInventoryChanged.class, e -> updatePlayerSession(
                e.uuid(),
                data -> {
                    data.activeBadge = e.activeBadge();
                    data.unlockedBadges = e.unlockedBadges() == null ? new HashSet<>() : new HashSet<>(e.unlockedBadges());
                },
                true,
                "badge inventory"
        ));

        network.subscribe(SocketEvents.PlayerPasswordReset.class, e -> updatePlayerSession(
                e.uuid(),
                data -> data.password = "",
                false,
                "password reset"
        ));

        network.subscribe(SocketEvents.ReloadPlayerDataCache.class, _ -> {
            sessionService.reloadCache();
            info("Reloaded player data cache.");
        });

        network.subscribe(SocketEvents.ExecuteCommand.class, e -> {
            if (e.expectServers() != null) {
                if (e.isExclusion()) {
                    if (Structs.contains(e.expectServers(), config.server)) return;
                } else if (e.expectServers().length > 0 && !Structs.contains(e.expectServers(), config.server)) {
                    return;
                }
            }

            Log.infoTag("ExecuteCommandEvent", "Executing command: " + e.command());
            ServerControl.instance.handleCommandString(e.command());
        });
    }

    private void updatePlayerSession(String uuid,
                                     Consumer<PlayerData> updater,
                                     boolean refreshDisplay,
                                     String label) {
        var session = sessionService.get(uuid);
        if (session == null || session.data == null) {
            return;
        }

        updater.accept(session.data);
        if (refreshDisplay) {
            playerDisplayService.refresh(session);
        }
        info("Synced player @ for @", label, uuid);
    }
}
