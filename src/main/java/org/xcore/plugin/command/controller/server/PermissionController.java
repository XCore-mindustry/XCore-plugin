package org.xcore.plugin.command.controller.server;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.incendo.cloud.annotation.specifier.Quoted;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Flag;
import org.incendo.cloud.annotations.Permission;
import org.jspecify.annotations.Nullable;
import org.xcore.cloud.mindustry.selector.annotation.DenySelectors;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudServerController;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.database.PagedDataResult;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.permission.Access;
import org.xcore.plugin.permission.Actor;
import org.xcore.plugin.permission.PermissionNode;
import org.xcore.plugin.permission.PermissionNodes;
import org.xcore.plugin.permission.PermissionSessions;
import org.xcore.plugin.permission.StaffCredentials;
import org.xcore.plugin.permission.grant.Grant;
import org.xcore.plugin.permission.grant.GrantDocument;
import org.xcore.plugin.permission.grant.PermissionGrants;
import org.xcore.plugin.permission.grant.PermissionGrants.GrantException;
import org.xcore.plugin.permission.grant.PermissionMigration;
import org.xcore.plugin.permission.role.PermissionConfigException;
import org.xcore.plugin.permission.role.PermissionModel;
import org.xcore.plugin.permission.role.PermissionRoles;
import org.xcore.plugin.permission.role.PermissionSet;
import org.xcore.plugin.permission.role.RoleDefinition;
import org.xcore.plugin.service.TimeService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Console management of roles and grants. Storage work runs off the server thread.
 * <p>
 * Everything that changes what a player holds asks for {@link PermissionNodes#PERMISSIONS_MANAGE},
 * which only the console of this very server has: not a player, and not a command relayed
 * from another server.
 */
@Singleton
public class PermissionController implements CloudServerController {

    private final PermissionRoles roles;
    private final PermissionGrants grants;
    private final PermissionSessions permissionSessions;
    private final PermissionMigration migration;
    private final StaffCredentials credentials;
    private final PlayerDataRepository players;
    private final SessionService sessions;
    private final TimeService time;
    private final Async async;

    @Inject
    public PermissionController(PermissionRoles roles,
                                PermissionGrants grants,
                                PermissionSessions permissionSessions,
                                PermissionMigration migration,
                                StaffCredentials credentials,
                                PlayerDataRepository players,
                                SessionService sessions,
                                TimeService time,
                                Async async) {
        this.roles = roles;
        this.grants = grants;
        this.permissionSessions = permissionSessions;
        this.migration = migration;
        this.credentials = credentials;
        this.players = players;
        this.sessions = sessions;
        this.time = time;
        this.async = async;
    }

    @DenySelectors
    @Permission(PermissionNodes.PERMISSIONS_INSPECT)
    @Command("perm user <player> info")
    @CommandDescription("Shows what a player is granted: roles, denials, where they come from and when they end.")
    public void info(XCoreSender sender,
                     @Argument(value = "player", description = "Player #ID, UUID, @username or nickname") String player) {
        run(() -> {
            PlayerData target = resolve(player);
            Instant now = roles.clock().instant();
            GrantDocument document = grants.load(target.uuid);
            PLog.info("Grants of @ (revision @):", describe(target), document.revision());
            if (document.grants().isEmpty()) {
                PLog.info("  none");
            }
            for (Grant grant : document.grants()) {
                PLog.info("  @", describe(grant, now));
            }
            PermissionSet set = PermissionSet.compile(roles.model(), document, roles.serverName(), false, now);
            PLog.info("  weight on @: @, may log in as staff: @, password set: @", roles.serverName(), set.weight(now),
                    set.canLogInAsStaff(now), target.password != null && !target.password.isEmpty());
            if (!roles.enabled()) {
                PLog.info("  roles are off on this server (permissions.mode = \"legacy\"): none of this is in force here");
            }
        });
    }

    @DenySelectors
    @Permission(PermissionNodes.PERMISSIONS_MANAGE)
    @Command("perm user <player> role add <role>")
    @CommandDescription("Gives a role. Example: perm user #12 role add moderator --for 7d --reason \"trial week\"")
    public void roleAdd(XCoreSender sender,
                        @Argument(value = "player", description = "Player #ID, UUID, @username or nickname") String player,
                        @Argument(value = "role", description = "A role from permissions.toml") String role,
                        @Nullable @Flag(value = "server", description = "Only on this server") String server,
                        @Nullable @Flag(value = "for", description = "How long: 12h, 7d, 2w") String duration,
                        @Nullable @Flag(value = "reason", description = "Why, for the audit log") @Quoted String reason) {
        Actor actor = sender.actor();
        run(() -> {
            PlayerData target = resolve(player);
            report(grants.addRole(target, role, server, duration(duration), reason(reason), actor), target);
        });
    }

    @DenySelectors
    @Permission(PermissionNodes.PERMISSIONS_MANAGE)
    @Command("perm user <player> role remove <role>")
    @CommandDescription("Takes away a role given by hand, by its name or by the id of one grant.")
    public void roleRemove(XCoreSender sender,
                           @Argument(value = "player", description = "Player #ID, UUID, @username or nickname") String player,
                           @Argument(value = "role", description = "Role name or grant id") String role,
                           @Nullable @Flag(value = "reason", description = "Why, for the audit log") @Quoted String reason) {
        Actor actor = sender.actor();
        run(() -> {
            PlayerData target = resolve(player);
            report(grants.removeRole(target, role, reason(reason), actor), target);
        });
    }

    @DenySelectors
    @Permission(PermissionNodes.PERMISSIONS_MANAGE)
    @Command("perm user <player> deny <node>")
    @CommandDescription("Takes a node away from a player whatever their roles say. Example: perm user #12 deny xcore.moderation.ban --for 1d --reason \"cooldown\"")
    public void deny(XCoreSender sender,
                     @Argument(value = "player", description = "Player #ID, UUID, @username or nickname") String player,
                     @Argument(value = "node", description = "A node, or a wildcard such as xcore.moderation.*") String node,
                     @Nullable @Flag(value = "for", description = "How long: 12h, 1d, 2w") String duration,
                     @Nullable @Flag(value = "reason", description = "Why, for the audit log") @Quoted String reason) {
        Actor actor = sender.actor();
        run(() -> {
            PlayerData target = resolve(player);
            report(grants.deny(target, node, duration(duration), reason(reason), actor), target);
        });
    }

    @DenySelectors
    @Permission(PermissionNodes.PERMISSIONS_MANAGE)
    @Command("perm user <player> undeny <grant>")
    @CommandDescription("Lifts a denial by its grant id, as shown by perm user <player> info.")
    public void undeny(XCoreSender sender,
                       @Argument(value = "player", description = "Player #ID, UUID, @username or nickname") String player,
                       @Argument(value = "grant", description = "Grant id, e.g. g-77c0") String grant,
                       @Nullable @Flag(value = "reason", description = "Why, for the audit log") @Quoted String reason) {
        Actor actor = sender.actor();
        run(() -> {
            PlayerData target = resolve(player);
            report(grants.undeny(target, grant, reason(reason), actor), target);
        });
    }

    @DenySelectors
    @Permission(PermissionNodes.PERMISSIONS_MANAGE)
    @Command("perm user <player> reset-password")
    @CommandDescription("Forgets a staff password and every remembered device; the next /login sets a new password.")
    public void resetPassword(XCoreSender sender,
                              @Argument(value = "player", description = "Player #ID, UUID, @username or nickname") String player) {
        Actor actor = sender.actor();
        run(() -> {
            PlayerData target = resolve(player);
            if (credentials.resetPassword(target, actor)) {
                PLog.info("The password of @ is reset; their next /login sets a new one.", describe(target));
            } else {
                PLog.info("@ has no password to reset.", describe(target));
            }
        });
    }

    @DenySelectors
    @Permission(PermissionNodes.PERMISSIONS_INSPECT)
    @Command("perm check <player> <node>")
    @CommandDescription("Says whether a logged-in player would be allowed a node, and which rule decides.")
    public void check(XCoreSender sender,
                      @Argument(value = "player", description = "Player #ID, UUID, @username or nickname") String player,
                      @Argument(value = "node", description = "Permission node") String node,
                      @Nullable @Flag(value = "server", description = "As if on this server") String server) {
        run(() -> {
            PermissionNode declared = PermissionNodes.find(node.toLowerCase(Locale.ROOT))
                    .orElseThrow(() -> new GrantException("Unknown node '" + node + "'; see perm nodes"));
            PlayerData target = resolve(player);
            String where = server == null || server.isBlank() ? roles.serverName() : server.strip();
            Instant now = roles.clock().instant();
            PermissionSet set = PermissionSet.compile(roles.model(), grants.load(target.uuid), where, false, now);
            PermissionSet.Decision decision = set.check(declared, true, now);
            PLog.info("@ on @: @ &fb@&fr - @", describe(target), where, declared.name(),
                    decision.granted() ? "ALLOWED" : "DENIED", decision.reason());
            if (declared.access() == Access.STAFF && decision.granted()) {
                PLog.info("  a staff node: in force only while they are logged in (/login)");
            }
        });
    }

    @Permission(PermissionNodes.PERMISSIONS_INSPECT)
    @Command("perm nodes [filter]")
    @CommandDescription("Lists the permission nodes, optionally only those containing a text.")
    public void nodes(XCoreSender sender,
                      @Nullable @Argument(value = "filter", description = "Part of a node name") String filter) {
        String wanted = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
        List<PermissionNode> found = PermissionNodes.all().stream()
                .filter(node -> node.name().contains(wanted))
                .sorted(Comparator.comparing(PermissionNode::name))
                .toList();
        PLog.info("Permission nodes (@):", found.size());
        for (PermissionNode node : found) {
            PLog.info("  @ &fb@&fr", node.name(), node.access());
        }
    }

    @Permission(PermissionNodes.PERMISSIONS_INSPECT)
    @Command("perm roles")
    @CommandDescription("Lists the roles of permissions.toml with their weight and rules.")
    public void roles(XCoreSender sender) {
        PermissionModel model = roles.model();
        PLog.info("Roles (@) from @, roles are @ on this server:", model.roles().size(),
                roles.file() == null ? "memory" : roles.file().absolutePath(), roles.enabled() ? "ON" : "OFF");
        model.roles().values().stream()
                .sorted(Comparator.comparingInt(RoleDefinition::weight).reversed())
                .forEach(role -> PLog.info("  &fb@&fr weight @@: @", role.name(), role.weight(),
                        role.parents().isEmpty() ? "" : ", parents " + role.parents(), role.rules()));
        for (PermissionModel.Binding binding : model.discord().bindings()) {
            PLog.info("  Discord role @ -> @@", binding.roleId(), binding.role(),
                    binding.server() == null ? "" : " on " + binding.server());
        }
    }

    @Permission(PermissionNodes.PERMISSIONS_INSPECT)
    @Command("perm reload")
    @CommandDescription("Reads permissions.toml again on this server. A file with an error is refused and the roles in use stay.")
    public void reload(XCoreSender sender) {
        run(() -> {
            try {
                PermissionModel model = roles.reload();
                PLog.info("permissions.toml reloaded: @ roles, @ Discord bindings.", model.roles().size(),
                        model.discord().bindings().size());
            } catch (PermissionConfigException e) {
                throw new GrantException("permissions.toml was not reloaded, the previous roles stay in use: " + e.getMessage());
            }
            async.main(permissionSessions::refreshAll);
        });
    }

    @Permission(PermissionNodes.PERMISSIONS_MANAGE)
    @Command("perm prune")
    @CommandDescription("Removes the grants that have run out from the database.")
    public void prune(XCoreSender sender) {
        run(() -> PLog.info("Removed the expired grants of @ players.", grants.prune()));
    }

    @Permission(PermissionNodes.PERMISSIONS_MANAGE)
    @Command("perm migrate")
    @CommandDescription("Gives the role 'admin' to everybody whose admin flag came from Discord. Only reports unless --apply is given.")
    public void migrate(XCoreSender sender,
                        @Flag(value = "apply", description = "Write the grants instead of only reporting") boolean apply) {
        run(() -> {
            PermissionMigration.Report report = migration.run(!apply);
            PLog.info("Migration @: @ Discord admins, @ @, @ already had it.", report.dryRun() ? "DRY RUN" : "APPLIED",
                    report.candidates(), report.granted(), report.dryRun() ? "would get 'admin'" : "got 'admin'",
                    report.alreadyGranted());
            report.lines().forEach(line -> PLog.info("  @", line));
            if (report.dryRun() && report.granted() > 0) {
                PLog.info("Nothing was written. Run 'perm migrate --apply' to give the roles.");
            }
        });
    }

    private void report(PermissionGrants.Change change, PlayerData target) {
        if (!change.changed()) {
            PLog.info("Nothing to change for @.", describe(target));
            return;
        }
        Instant now = roles.clock().instant();
        change.added().forEach(grant -> PLog.info("&g+&fr @: @", describe(target), describe(grant, now)));
        change.removed().forEach(grant -> PLog.info("&r-&fr @: @", describe(target), describe(grant, now)));
        Session online = sessions.get(target.uuid);
        if (roles.enabled() && online != null) {
            PLog.info("  they are online here; the change is in force now.");
        }
    }

    /**
     * {@code #pid}, uuid and {@code @username} name one player. A nickname has to as well:
     * several matches are refused rather than the first one taken.
     */
    private PlayerData resolve(String text) {
        PlayerData exact = sessions.resolvePlayerData(text);
        if (exact != null) {
            return exact;
        }
        PagedDataResult<PlayerData> byNickname = players.search("^" + Pattern.quote(text.strip()) + "$", 5, 1);
        List<PlayerData> found = new ArrayList<>();
        if (byNickname != null) {
            byNickname.results().forEach(found::add);
        }
        if (found.isEmpty()) {
            throw new GrantException("Player '" + text + "' not found");
        }
        if (found.size() > 1 || byNickname.total() > 1) {
            throw new GrantException("'" + text + "' is the nickname of " + byNickname.total() + " players (" +
                    String.join(", ", found.stream().map(PermissionController::describe).toList())
                    + (byNickname.total() > found.size() ? ", ..." : "") + "); name one by #ID or UUID");
        }
        return found.get(0);
    }

    private @Nullable Duration duration(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Instant parsed = time.parsePeriod(text, TimeUnit.DAYS);
        if (parsed == null || parsed.toEpochMilli() <= 0) {
            throw new GrantException("'" + text + "' is not a time span; try 12h, 7d or 2w");
        }
        return Duration.ofMillis(parsed.toEpochMilli());
    }

    private static String reason(@Nullable String reason) {
        if (reason == null || reason.isBlank()) {
            throw new GrantException("A reason is required: --reason \"...\"");
        }
        return reason.strip();
    }

    private static String describe(PlayerData player) {
        return "#" + player.pid + " " + player.nickname + " (" + player.uuid + ")";
    }

    static String describe(Grant grant, Instant now) {
        StringBuilder text = new StringBuilder(grant.id()).append("  ");
        text.append(grant.isRole() ? "role " + grant.role() : (grant.allow() ? "allow " : "deny ") + grant.node());
        text.append(grant.server() == null ? "" : " on " + grant.server());
        if (grant.expiresAt() != null) {
            text.append(grant.isExpired(now) ? " EXPIRED " : " until ").append(grant.expiresAt());
        }
        text.append("  [").append(grant.source()).append("] by ").append(grant.by()).append(" at ").append(grant.at());
        if (grant.reason() != null && !grant.reason().isBlank()) {
            text.append("  \"").append(grant.reason()).append('"');
        }
        return text.toString();
    }

    private void run(Runnable task) {
        async.run(() -> {
            try {
                task.run();
            } catch (GrantException | IllegalArgumentException e) {
                PLog.err("@", e.getMessage());
            } catch (RuntimeException e) {
                PLog.err("Permission command failed: @", e.getMessage());
                arc.util.Log.err(e);
            }
        });
    }
}
