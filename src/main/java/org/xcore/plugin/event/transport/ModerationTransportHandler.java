package org.xcore.plugin.event.transport;

import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Groups;
import mindustry.net.Administration;
import mindustry.net.Packets;
import mindustry.server.ServerControl;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.permission.Actor;
import org.xcore.plugin.permission.RemoteConsoleScope;
import org.xcore.plugin.rating.ladder.LadderService;
import org.xcore.plugin.service.DiscordAdminAccessService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.session.SessionService;
import org.xcore.protocol.generated.messages.identity.IdentityMessages.PlayerActiveBadgeChangedCommandV1;
import org.xcore.protocol.generated.messages.identity.IdentityMessages.PlayerBadgeInventoryChangedCommandV1;
import org.xcore.protocol.generated.messages.identity.IdentityMessages.PlayerBadgeSymbolColorModeChangedCommandV1;
import org.xcore.protocol.generated.messages.identity.IdentityMessages.PlayerCustomNicknameChangedCommandV1;
import org.xcore.protocol.generated.messages.server.ServerMessages.PlayerDataCacheReloadCommandV1;
import org.xcore.protocol.generated.messages.security.SecurityMessages.PlayerPasswordResetCommandV1;
import org.xcore.protocol.generated.messages.server.ServerMessages.ServerCommandExecuteCommandV1;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordAdminAccessChangedCommandV1;
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
    private final TomlXcoreConfig config;
    private final PlayerDisplayService playerDisplayService;
    private final DiscordAdminAccessService discordAdminAccessService;
    private final LadderService ladderService;
    private final Async async;
    private final RemoteConsoleScope remoteConsole;

    @Inject
    public ModerationTransportHandler(NetworkService network,
                                      SessionService sessionService,
                                      TomlXcoreConfig config,
                                      PlayerDisplayService playerDisplayService,
                                      DiscordAdminAccessService discordAdminAccessService,
                                      LadderService ladderService,
                                      Async async,
                                      RemoteConsoleScope remoteConsole) {
        this.network = network;
        this.sessionService = sessionService;
        this.config = config;
        this.playerDisplayService = playerDisplayService;
        this.discordAdminAccessService = discordAdminAccessService;
        this.ladderService = ladderService;
        this.async = async;
        this.remoteConsole = remoteConsole;
    }

    /**
     * Registers moderation listeners.
     *
     * <p>Every listener here runs on a Redis subscriber virtual thread, never on the game
     * thread. Handlers that touch Mindustry state are marshalled through {@link Async#main}
     * explicitly. Handlers that only touch plugin-owned data are deliberately left on the
     * subscriber thread, because their database calls must not stall the game loop.
     */
    public void registerListeners() {
        // Groups.player and Player.kick are game state.
        network.subscribe(ModerationKickBannedCommandV1.class, e -> async.main(() -> {
            var target = e.target();
            Groups.player.each(
                    p -> p.uuid().equals(target.playerUuid())
                            || (target.ip() != null && target.ip().equals(p.ip())),
                    p -> p.kick(Packets.KickReason.banned)
            );
        }));

        // The repository round trips must not run on the game thread, so the handler does
        // not marshal. DiscordAdminAccessService owns that decision: it keeps its Mongo
        // work inline and queues only the game-state tail (Player.admin, the admin
        // registry, Player.name, client packets). See its javadoc.
        network.subscribe(DiscordAdminAccessChangedCommandV1.class, e -> {
            if (e.admin()) {
                if (discordAdminAccessService.applyDiscordAdminAccess(
                        e.player().playerUuid(),
                        e.discord().discordId(),
                        e.discord().discordUsername()
                )) {
                    info("Granted discord admin access: @", e.player().playerUuid());
                }
                return;
            }

            if (discordAdminAccessService.revokeDiscordAdminAccess(e.player().playerUuid())) {
                info("Revoked discord admin access: @", e.player().playerUuid());
            }
        });

        // Administration is main-thread state: the admin registry is read during auth
        // and the kick list is scanned on every connection.
        network.subscribe(ModerationPardonCommandV1.class, e -> async.main(() -> {
            Administration.PlayerInfo info = netServer.admins.getInfoOptional(e.target().playerUuid());

            if (info != null) {
                info.lastKicked = 0;
                netServer.admins.kickedIPs.remove(info.lastIP);
                info("Pardoned player: @", info.plainLastName());
            }
        }));

        network.subscribe(PlayerCustomNicknameChangedCommandV1.class, e -> async.main(() -> updatePlayerSession(
                e.playerUuid(),
                data -> data.customNickname = e.customNickname(),
                false,
                "custom nickname"
        )));

        network.subscribe(PlayerActiveBadgeChangedCommandV1.class, e -> async.main(() -> updatePlayerSession(
                e.playerUuid(),
                data -> data.activeBadge = e.activeBadge(),
                true,
                "active badge"
        )));

        network.subscribe(PlayerBadgeSymbolColorModeChangedCommandV1.class, e -> async.main(() -> updatePlayerSession(
                e.playerUuid(),
                data -> data.badgeSymbolColorMode = e.badgeSymbolColorMode(),
                true,
                "badge symbol color mode"
        )));

        network.subscribe(PlayerBadgeInventoryChangedCommandV1.class, e -> async.main(() -> updatePlayerSession(
                e.playerUuid(),
                data -> {
                    data.activeBadge = e.activeBadge();
                    data.unlockedBadges = new HashSet<>(e.unlockedBadges());
                },
                true,
                "badge inventory"
        )));

        network.subscribe(PlayerPasswordResetCommandV1.class, e -> async.main(() -> {
            updatePlayerSession(
                    e.playerUuid(),
                    data -> data.password = "",
                    false,
                    "password reset"
            );
            discordAdminAccessService.onPasswordReset(e.playerUuid());
        }));

        // The cache rebuild walks Groups.player and queries MongoDB per player, so it
        // takes its own snapshot on the game thread and installs the result back onto it.
        // Standings are plugin-owned data, so they are re-read right here on the subscriber thread.
        network.subscribe(PlayerDataCacheReloadCommandV1.class, _ -> {
            ladderService.reloadCaches();
            sessionService.reloadCacheAsync(async);
        });

        // handleCommandString runs game logic, including player and world mutation.
        // Senders older than the sourceServer field leave it out; the origin is then only known to be remote.
        network.subscribe(ServerCommandExecuteCommandV1.class, e -> {
            if (!e.targetServers().isEmpty()) {
                if (e.exclusion()) {
                    if (e.targetServers().contains(config.server.name)) return;
                } else if (!e.targetServers().contains(config.server.name)) {
                    return;
                }
            }

            async.main(() -> {
                Log.infoTag("ExecuteCommandEvent", "Executing command: " + e.command());
                String source = e.sourceServer() == null || e.sourceServer().isBlank()
                        ? Actor.RemoteConsole.UNKNOWN_SOURCE
                        : e.sourceServer();
                remoteConsole.run(source, () -> ServerControl.instance.handleCommandString(e.command()));
            });
        });
    }

    /**
     * Applies a moderation field change to a cached session.
     *
     * <p>Mutates {@code Player.name} and the administration registry through
     * {@link PlayerDisplayService#refresh}, so it must not run on a subscriber thread.
     * Callers are already inside an {@link Async#main} hop.
     */
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
