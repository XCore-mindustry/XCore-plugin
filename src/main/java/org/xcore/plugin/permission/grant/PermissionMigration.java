package org.xcore.plugin.permission.grant;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.permission.Actor;
import org.xcore.plugin.permission.role.PermissionModel;
import org.xcore.plugin.permission.role.PermissionRoles;
import org.xcore.plugin.permission.role.PermissionSet;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The one-off move from the admin flag of the player record to grants: everybody whose flag
 * came from their Discord role gets the role {@code admin}. I/O.
 * <p>
 * The flag itself is left as it is, so switching back to {@code mode = "legacy"} finds
 * everything where it was. Running the move again gives nothing twice.
 */
@Singleton
public class PermissionMigration {

    public static final String ROLE = PermissionSet.NATIVE_ADMIN_ROLE;
    static final Actor ACTOR = new Actor.SystemActor("migration");

    /**
     * @param candidates players whose admin flag came from their Discord role
     * @param granted    how many got the role now, or would get it in a dry run
     * @param lines      one line per player, for the log
     */
    public record Report(boolean dryRun, int candidates, int granted, int alreadyGranted, List<String> lines) {
    }

    /** How an account merge marks the record it emptied. */
    private static final String MERGED_PREFIX = "merged:";

    private final PlayerDataRepository players;
    private final PermissionGrants grants;
    private final PermissionRoles roles;

    @Inject
    public PermissionMigration(PlayerDataRepository players, PermissionGrants grants, PermissionRoles roles) {
        this.players = players;
        this.grants = grants;
        this.roles = roles;
    }

    /** @param dryRun report what would be given and write nothing */
    public Report run(boolean dryRun) {
        PermissionModel model = roles.model();
        if (model.role(ROLE).isEmpty()) {
            throw new PermissionGrants.GrantException("permissions.toml has no role '" + ROLE + "' to give to the current admins");
        }
        Instant now = roles.clock().instant();
        List<String> lines = new ArrayList<>();
        int granted = 0;
        int alreadyGranted = 0;
        List<PlayerData> candidates = new ArrayList<>();
        for (PlayerData player : players.findDiscordRoleAdmins()) {
            // What an account merge leaves behind: nobody can connect as it, and the account it
            // was merged into is a candidate in its own right.
            if (player.uuid == null || player.uuid.startsWith(MERGED_PREFIX)) {
                lines.add("#" + player.pid + " " + player.nickname + " (" + player.uuid + "): merged into another account, skipped");
                continue;
            }
            candidates.add(player);
        }
        for (PlayerData player : candidates) {
            String who = "#" + player.pid + " " + player.nickname + " (" + player.uuid + ")";
            boolean has = grants.load(player.uuid).active(now).stream()
                    .anyMatch(grant -> grant.isRole() && grant.role().equals(ROLE) && grant.server() == null);
            if (has) {
                alreadyGranted++;
                lines.add(who + ": already holds " + ROLE);
                continue;
            }
            String source = source(model, player);
            if (!dryRun) {
                grants.importRole(player, ROLE, source, "Moved from the Discord admin flag", ACTOR);
            }
            granted++;
            lines.add(who + ": " + (dryRun ? "would get " : "got ") + ROLE + " [" + source + "]");
        }
        return new Report(dryRun, candidates.size(), granted, alreadyGranted, List.copyOf(lines));
    }

    /**
     * The grant is marked as coming from Discord, so the bot's next full sync takes it away
     * from anybody who no longer holds the Discord role. When exactly one Discord role is
     * bound to {@code admin} everywhere, that is the role the flag stood for and the sync
     * finds the grant already in place; otherwise the player's Discord account stands in.
     */
    private static String source(PermissionModel model, PlayerData player) {
        List<PermissionModel.Binding> bound = model.discord().bindings().stream()
                .filter(binding -> binding.role().equals(ROLE) && binding.server() == null)
                .toList();
        if (bound.size() == 1) {
            return Grant.discordSource(bound.get(0).roleId());
        }
        return Grant.discordSource(player.discordId == null || player.discordId.isBlank() ? "unknown" : player.discordId);
    }
}
