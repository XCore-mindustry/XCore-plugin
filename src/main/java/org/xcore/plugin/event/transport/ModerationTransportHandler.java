package org.xcore.plugin.event.transport;

import arc.util.Log;
import arc.util.Structs;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Groups;
import mindustry.net.Administration;
import mindustry.net.Packets;
import mindustry.server.ServerControl;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.event.TransportEvents;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.DiscordAdminAccessService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.session.SessionService;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationKickBannedCommandV1;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationPardonCommandV1;

import java.util.HashSet;
import java.util.function.Consumer;

import static mindustry.Vars.netServer;
import static org.xcore.plugin.common.PLog.info;

@Singleton
public class ModerationTransportHandler {

    private final NetworkService network;
    private final SessionService sessionService;
    private final Config config;
    private final PlayerDisplayService playerDisplayService;
    private final DiscordAdminAccessService discordAdminAccessService;

    @Inject
    public ModerationTransportHandler(NetworkService network,
                                      SessionService sessionService,
                                      Config config,
                                      PlayerDisplayService playerDisplayService,
                                      DiscordAdminAccessService discordAdminAccessService) {
        this.network = network;
        this.sessionService = sessionService;
        this.config = config;
        this.playerDisplayService = playerDisplayService;
        this.discordAdminAccessService = discordAdminAccessService;
    }

    public void registerListeners() {
        network.subscribe(ModerationKickBannedCommandV1.class, e -> Groups.player.each(
                p -> {
                    var target = e.target();
                    return p.uuid().equals(target.playerUuid())
                            || (target.ip() != null && target.ip().equals(p.ip()));
                },
                p -> p.kick(Packets.KickReason.banned)
        ));

        network.subscribe(TransportEvents.DiscordAdminAccessChanged.class, e -> {
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

        network.subscribe(ModerationPardonCommandV1.class, e -> {
            Administration.PlayerInfo info = netServer.admins.getInfoOptional(e.target().playerUuid());

            if (info != null) {
                info.lastKicked = 0;
                netServer.admins.kickedIPs.remove(info.lastIP);
                info("Pardoned player: @", info.plainLastName());
            }
        });

        network.subscribe(TransportEvents.PlayerCustomNicknameChanged.class, e -> updatePlayerSession(
                e.uuid(),
                data -> data.customNickname = e.customNickname(),
                false,
                "custom nickname"
        ));

        network.subscribe(TransportEvents.PlayerActiveBadgeChanged.class, e -> updatePlayerSession(
                e.uuid(),
                data -> data.activeBadge = e.activeBadge(),
                true,
                "active badge"
        ));

        network.subscribe(TransportEvents.PlayerBadgeSymbolColorModeChanged.class, e -> updatePlayerSession(
                e.uuid(),
                data -> data.badgeSymbolColorMode = e.badgeSymbolColorMode(),
                true,
                "badge symbol color mode"
        ));

        network.subscribe(TransportEvents.PlayerBadgeInventoryChanged.class, e -> updatePlayerSession(
                e.uuid(),
                data -> {
                    data.activeBadge = e.activeBadge();
                    data.unlockedBadges = e.unlockedBadges() == null ? new HashSet<>() : new HashSet<>(e.unlockedBadges());
                },
                true,
                "badge inventory"
        ));

        network.subscribe(TransportEvents.PlayerPasswordReset.class, e -> updatePlayerSession(
                e.uuid(),
                data -> data.password = "",
                false,
                "password reset"
        ));

        network.subscribe(TransportEvents.ReloadPlayerDataCache.class, _ -> {
            sessionService.reloadCache();
            info("Reloaded player data cache.");
        });

        network.subscribe(TransportEvents.ExecuteCommand.class, e -> {
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
