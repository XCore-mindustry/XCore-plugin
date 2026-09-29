package org.xcore.plugin.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import static mindustry.Vars.netServer;

@Singleton
public class DiscordAdminAccessService {

    public static final String SOURCE_DISCORD_ROLE = "DISCORD_ROLE";
    public static final String SOURCE_NONE = "NONE";

    private final PlayerDataRepository playerDataRepository;
    private final SessionService sessionService;
    private final PlayerDisplayService playerDisplayService;
    private final AuthStatusBroadcaster authStatusBroadcaster;
    private final Async async;

    @Inject
    public DiscordAdminAccessService(PlayerDataRepository playerDataRepository,
                                     SessionService sessionService,
                                     PlayerDisplayService playerDisplayService,
                                     AuthStatusBroadcaster authStatusBroadcaster,
                                     Async async) {
        this.playerDataRepository = playerDataRepository;
        this.sessionService = sessionService;
        this.playerDisplayService = playerDisplayService;
        this.authStatusBroadcaster = authStatusBroadcaster;
        this.async = async;
    }

    public boolean hasDiscordAdminAccess(PlayerData data) {
        return data != null && data.admin && SOURCE_DISCORD_ROLE.equals(data.adminSource);
    }

    public boolean applyDiscordAdminAccess(String playerUuid, String discordId, String discordUsername) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return false;
        }

        PlayerData data = playerDataRepository.findByUuid(playerUuid);
        if (data == null) {
            return false;
        }

        if (!playerDataRepository.updateAdminStatus(playerUuid, true, SOURCE_DISCORD_ROLE)) {
            return false;
        }

        data.admin = true;
        data.adminSource = SOURCE_DISCORD_ROLE;
        syncLinkedDiscordState(data, discordId, discordUsername);

        // The repository calls above are MongoDB round trips and must not run on the game
        // thread. Everything from here on is game state: PlayerDisplayService writes
        // Player.name and the administration registry, AuthStatusBroadcaster writes a
        // client packet, and deactivateRuntimeAdmin flips Player.admin. Applying it from a
        // Redis subscriber thread is a data race against the tick loop, so it is queued.
        // The return value does not depend on any of it, so there is nothing to wait for.
        Session session = sessionService.get(playerUuid);
        if (session != null && session.data != null) {
            async.main(() -> {
                session.data.admin = true;
                session.data.adminSource = SOURCE_DISCORD_ROLE;
                syncLinkedDiscordState(session.data, discordId, discordUsername);
                playerDisplayService.refresh(session);

                if (session.player != null) {
                    authStatusBroadcaster.pushStatus(
                            session.player,
                            true,
                            discordUsername,
                            true,
                            session.data.password != null && !session.data.password.isEmpty(),
                            session.player.admin
                    );
                }
            });
        }
        return true;
    }

    public boolean revokeDiscordAdminAccess(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return false;
        }

        PlayerData data = playerDataRepository.findByUuid(playerUuid);
        if (data == null) {
            return false;
        }

        if (!playerDataRepository.clearAdminAccess(playerUuid)) {
            return false;
        }

        data.admin = false;
        data.adminSource = SOURCE_NONE;
        data.clearDeviceTokens();
        playerDataRepository.save(data);

        Session session = sessionService.get(playerUuid);
        if (session != null && session.data != null) {
            async.main(() -> {
                session.data.admin = false;
                session.data.adminSource = SOURCE_NONE;
                session.data.clearDeviceTokens();
                deactivateRuntimeAdmin(session.player, playerUuid);
                playerDisplayService.refresh(session);

                if (session.player != null) {
                    boolean isLinked = session.data.discordId != null && !session.data.discordId.isBlank();
                    authStatusBroadcaster.pushStatus(
                            session.player,
                            isLinked,
                            session.data.discordUsername,
                            false,
                            session.data.password != null && !session.data.password.isEmpty(),
                            false
                    );
                }
            });
        } else {
            // Still game state: unAdminPlayer writes the administration registry, which the
            // game thread reads on every connection.
            async.main(() -> deactivateRuntimeAdmin(null, playerUuid));
        }

        return true;
    }

    /**
     * Revokes runtime admin rights. Callers must already be on the game thread: this
     * writes {@code Player.admin} and the administration registry with no marshalling of
     * its own, so the two call sites are responsible for where they run.
     */
    public void deactivateRuntimeAdmin(Player player, String playerUuid) {
        if (player != null) {
            player.admin(false);
        }
        netServer.admins.unAdminPlayer(playerUuid);
    }

    private void syncLinkedDiscordState(PlayerData data, String discordId, String discordUsername) {
        if (data == null) {
            return;
        }
        if (discordId != null && !discordId.isBlank()) {
            data.discordId = discordId;
        }
        if (discordUsername != null && !discordUsername.isBlank()) {
            data.discordUsername = discordUsername;
        }
    }
}
