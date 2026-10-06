package org.xcore.plugin.event.transport;

import arc.util.Timer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.permission.Actor;
import org.xcore.plugin.permission.PermissionSessions;
import org.xcore.plugin.permission.StaffCredentials;
import org.xcore.plugin.permission.grant.PermissionGrants;
import org.xcore.plugin.permission.role.PermissionRoles;
import org.xcore.plugin.service.NetworkService;
import org.xcore.protocol.generated.messages.security.SecurityMessages.SecurityPermissionsChangedV1;
import org.xcore.protocol.generated.messages.security.SecurityMessages.SecurityStaffResetPasswordRequestV1;
import org.xcore.protocol.generated.messages.security.SecurityMessages.SecurityStaffResetPasswordResponseV1;
import org.xcore.protocol.generated.messages.security.SecurityMessages.SecurityStaffSyncRequestV1;
import org.xcore.protocol.generated.messages.security.SecurityMessages.SecurityStaffSyncResponseV1;

import static mindustry.Vars.netServer;

/**
 * The network side of roles: tells the other servers when somebody's grants change, listens
 * for the same from them, and answers the bot's requests to sync Discord roles and to reset a
 * staff password.
 *
 * <p>Grants live in the shared database, so any server can answer for any player; the caller
 * names the server it picked. Requests arrive on a redis-sub thread, where blocking storage
 * work is fine. Both requests are safe to repeat: a sync that changes nothing says so, and a
 * password that is already gone stays gone.</p>
 */
@Singleton
public class SecurityTransportHandler {
    static final String NOT_FOUND = "NOT_FOUND";
    static final String UNAVAILABLE = "UNAVAILABLE";

    static final Actor DISCORD_SYNC = new Actor.SystemActor("discord-sync");
    static final Actor BOT = new Actor.SystemActor("bot");

    private final NetworkService network;
    private final PermissionRoles roles;
    private final PermissionGrants grants;
    private final PermissionSessions sessions;
    private final StaffCredentials credentials;
    private final PlayerDataRepository players;

    @Inject
    public SecurityTransportHandler(NetworkService network,
                                    PermissionRoles roles,
                                    PermissionGrants grants,
                                    PermissionSessions sessions,
                                    StaffCredentials credentials,
                                    PlayerDataRepository players) {
        this.network = network;
        this.roles = roles;
        this.grants = grants;
        this.sessions = sessions;
        this.credentials = credentials;
        this.players = players;

        grants.onChanged(this::announce);
    }

    public void registerListeners() {
        network.subscribe(SecurityPermissionsChangedV1.class, e -> sessions.onChanged(e.playerUuid(), e.revision()));
        network.subscribe(SecurityStaffSyncRequestV1.class, this::sync);
        network.subscribe(SecurityStaffResetPasswordRequestV1.class, this::resetPassword);
    }

    /**
     * Starts the periodic reread that catches a change whose announcement was lost. Game
     * thread, once the server has loaded.
     */
    public void start() {
        if (!roles.enabled()) {
            return;
        }
        Timer.schedule(sessions::refreshAll, PermissionSessions.REFRESH_SECONDS, PermissionSessions.REFRESH_SECONDS);
        Timer.schedule(sessions::reapplyAll, PermissionSessions.REAPPLY_SECONDS, PermissionSessions.REAPPLY_SECONDS);

        // The game's own admin list gives nothing with roles on. Listing it once lets the
        // operator hand out roles to those who are meant to keep their rights.
        var nativeAdmins = netServer.admins.getAdmins();
        if (nativeAdmins.size > 0) {
            PLog.infoTag("Permissions", "The game's admin list has @ entries; they @:", nativeAdmins.size,
                    roles.trustNativeAdmins() ? "hold 'admin' on this server (trust_native_admins)" : "give no rights, use 'perm user <player> role add'");
            nativeAdmins.each(info -> PLog.infoTag("Permissions", "  @ (@)", info.plainLastName(), info.id));
        }
    }

    /** A change written here: this server applies it at once, the others hear about it. */
    private void announce(String uuid, long revision) {
        sessions.onChanged(uuid, revision);
        network.post(new SecurityPermissionsChangedV1(uuid, (int) revision, roles.serverName(),
                roles.clock().instant().toString()));
    }

    void sync(SecurityStaffSyncRequestV1 request) {
        if (!request.server().equals(roles.serverName())) return;
        if (!roles.enabled()) {
            network.respondError(request, UNAVAILABLE, "Roles are switched off on " + roles.serverName());
            return;
        }
        try {
            PlayerData target = players.findByUuid(request.playerUuid());
            if (target == null) {
                network.respondError(request, NOT_FOUND, "No such player");
                return;
            }
            // Taking roles away needs no link: that is how an unlinked account loses them.
            // Giving them does, or anybody's Discord roles could be put on anybody's account.
            boolean gives = request.roleIds().stream().anyMatch(id -> roles.model().discord().binding(id).isPresent());
            if (gives && !request.discordId().equals(target.discordId)) {
                network.respondError(request, NOT_FOUND, "The player is not linked to this Discord account");
                return;
            }
            PermissionGrants.Change change = grants.syncDiscord(target, request.roleIds(), request.complete(), DISCORD_SYNC);
            network.respond(request, new SecurityStaffSyncResponseV1(request.server(), request.operationId(),
                    (int) change.document().revision(), change.changed()));
        } catch (RuntimeException e) {
            PLog.err("[Permissions] Discord sync of @ failed: @", request.playerUuid(), e.getMessage());
            network.respondError(request, UNAVAILABLE, "The roles could not be synced; see the server log");
        }
    }

    void resetPassword(SecurityStaffResetPasswordRequestV1 request) {
        if (!request.server().equals(roles.serverName())) return;
        try {
            PlayerData target = players.findByUuid(request.playerUuid());
            if (target == null) {
                network.respondError(request, NOT_FOUND, "No such player");
                return;
            }
            boolean changed = credentials.resetPassword(target, BOT);
            network.respond(request, new SecurityStaffResetPasswordResponseV1(request.server(), request.operationId(), changed));
        } catch (RuntimeException e) {
            PLog.err("[Permissions] Password reset of @ failed: @", request.playerUuid(), e.getMessage());
            network.respondError(request, UNAVAILABLE, "The password could not be reset; see the server log");
        }
    }
}
