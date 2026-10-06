package org.xcore.plugin.permission;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.permission.grant.PermissionGrants;
import org.xcore.plugin.permission.role.PermissionRoles;
import org.xcore.plugin.service.NetworkService;
import org.xcore.protocol.generated.messages.security.SecurityMessages.PlayerPasswordResetCommandV1;

/** Resetting a staff password on behalf of the console or the bot. I/O. */
@Singleton
public class StaffCredentials {

    private final PlayerDataRepository players;
    private final PermissionGrants grants;
    private final PermissionRoles roles;
    private final NetworkService network;

    @Inject
    public StaffCredentials(PlayerDataRepository players, PermissionGrants grants, PermissionRoles roles, NetworkService network) {
        this.players = players;
        this.grants = grants;
        this.roles = roles;
        this.network = network;
    }

    /**
     * Forgets the player's password and remembered devices; the next login sets a new password.
     * Every server is told, so a connection that was logged in with the old one is logged out.
     *
     * @return false when there was nothing to forget
     */
    public boolean resetPassword(PlayerData target, Actor by) {
        boolean hadCredentials = (target.password != null && !target.password.isEmpty())
                || (target.deviceTokens != null && !target.deviceTokens.isEmpty())
                || (target.deviceTokenHashes != null && !target.deviceTokenHashes.isEmpty());
        if (!hadCredentials) {
            return false;
        }
        if (!players.clearCredentials(target.uuid)) {
            throw new IllegalStateException("The password of " + target.uuid + " could not be cleared");
        }
        grants.record(target, by, "reset-password", "Staff password reset");
        network.post(new PlayerPasswordResetCommandV1(target.uuid, roles.serverName()));
        return true;
    }
}
