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
     * <p>
     * The reset is written into the player's grants first, as a new credentials epoch. That is
     * what logs out a connection that proved itself with the old password: this server reads it
     * at once, the others on the announcement or, if that is lost, on their next reread. The
     * password itself goes second, so a failure in between leaves a reset that can be repeated
     * rather than a cleared password nobody was logged out for.
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
        grants.resetCredentials(target, by, "Staff password reset");
        if (!players.clearCredentials(target.uuid)) {
            throw new IllegalStateException("The password of " + target.uuid + " could not be cleared");
        }
        // For what does not read grants: the legacy listeners and the bot.
        network.post(new PlayerPasswordResetCommandV1(target.uuid, roles.serverName()));
        return true;
    }
}
