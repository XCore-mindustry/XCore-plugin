package org.xcore.plugin.permission.role;

import org.xcore.plugin.permission.Access;
import org.xcore.plugin.permission.PermissionNode;
import org.xcore.plugin.permission.grant.Grant;
import org.xcore.plugin.permission.grant.GrantDocument;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * What one player is granted on this server, ready to be asked without I/O.
 * <p>
 * Built from the player's grants and the role model, and replaced as a whole when either
 * changes. Expiry is decided at the moment of each check, so a set that was loaded before a
 * grant ran out stops honouring it on time.
 */
public final class PermissionSet {

    /** How long a set that could not be refreshed keeps giving staff rights. */
    public static final Duration STALE_AFTER = Duration.ofMinutes(15);

    public static final PermissionSet EMPTY = new PermissionSet(0, List.of(), List.of(), Instant.EPOCH, true);

    /** A rule with where it came from. */
    private record Entry(Rule rule, boolean serverSpecific, Instant expiresAt, String origin, int weight) {
        boolean active(Instant now) {
            return expiresAt == null || now.isBefore(expiresAt);
        }
    }

    /** The outcome of asking for one node. */
    public record Decision(boolean granted, String reason) {
    }

    private final long revision;
    private final List<Entry> direct;
    private final List<Entry> fromRoles;
    private final Instant refreshedAt;
    private final boolean placeholder;

    private PermissionSet(long revision, List<Entry> direct, List<Entry> fromRoles, Instant refreshedAt, boolean placeholder) {
        this.revision = revision;
        this.direct = direct;
        this.fromRoles = fromRoles;
        this.refreshedAt = refreshedAt;
        this.placeholder = placeholder;
    }

    /**
     * @param serverName  this server; grants for another server are left out
     * @param nativeAdmin whether the game's own admin list vouches for the player and this server
     *                    trusts it; they then hold {@code admin} here
     * @param loadedAt    when the grants were read
     */
    public static PermissionSet compile(PermissionModel model, GrantDocument document, String serverName,
                                        boolean nativeAdmin, Instant loadedAt) {
        List<Entry> direct = new ArrayList<>();
        List<Entry> fromRoles = new ArrayList<>();
        for (Grant grant : document.grants()) {
            if (!grant.appliesOn(serverName)) {
                continue;
            }
            boolean serverSpecific = grant.server() != null;
            if (grant.isRole()) {
                // A role that was removed from permissions.toml gives nothing until it is back.
                model.role(grant.role()).ifPresent(role -> addRole(fromRoles, role, serverSpecific,
                        grant.expiresAt(), "role " + role.name() + " (" + grant.id() + ")"));
            } else {
                direct.add(new Entry(new Rule(grant.node(), grant.allow()), serverSpecific,
                        grant.expiresAt(), "direct grant " + grant.id(), 0));
            }
        }
        if (nativeAdmin) {
            model.role(NATIVE_ADMIN_ROLE).ifPresent(role ->
                    addRole(fromRoles, role, true, null, "role " + role.name() + " (native admin list)"));
        }
        return new PermissionSet(document.revision(), List.copyOf(direct), List.copyOf(fromRoles), loadedAt, false);
    }

    /** The role a trusted entry of the game's admin list stands for. */
    public static final String NATIVE_ADMIN_ROLE = "admin";

    private static void addRole(List<Entry> into, RoleDefinition role, boolean serverSpecific, Instant expiresAt, String origin) {
        if (role.rules().isEmpty()) {
            // Still counts for the hierarchy.
            into.add(new Entry(null, serverSpecific, expiresAt, origin, role.weight()));
        }
        for (Rule rule : role.rules()) {
            into.add(new Entry(rule, serverSpecific, expiresAt, origin, role.weight()));
        }
    }

    public long revision() {
        return revision;
    }

    /** True until the player's grants have been read for the first time. */
    public boolean isPlaceholder() {
        return placeholder;
    }

    /** The same set, marked as confirmed against the store at {@code now}. */
    public PermissionSet refreshedAt(Instant now) {
        return new PermissionSet(revision, direct, fromRoles, now, placeholder);
    }

    public boolean isStale(Instant now) {
        return Duration.between(refreshedAt, now).compareTo(STALE_AFTER) > 0;
    }

    /**
     * @param authenticated whether the player has logged in as staff on this connection
     */
    public Decision check(PermissionNode node, boolean authenticated, Instant now) {
        if (node.access() == Access.CONSOLE_ONLY) {
            return new Decision(false, "local console only");
        }

        Entry match = best(direct, node.name(), now);
        if (match == null) {
            match = best(fromRoles, node.name(), now);
        }

        if (match == null) {
            return node.access() == Access.PLAYER
                    ? new Decision(true, "open to every player")
                    : new Decision(false, "not granted");
        }
        if (!match.rule.allow()) {
            // A denial holds no matter how old the set is or whether the player logged in.
            return new Decision(false, "denied by " + match.rule + " from " + match.origin);
        }
        if (node.access() == Access.STAFF) {
            if (!authenticated) {
                return new Decision(false, "granted by " + match.origin + ", but not logged in");
            }
            if (isStale(now)) {
                return new Decision(false, "granted by " + match.origin + ", but the grants could not be refreshed");
            }
        }
        return new Decision(true, "granted by " + match.rule + " from " + match.origin);
    }

    /**
     * The most specific rule that matches. Among equally specific ones a rule for this server
     * beats a global one, and then a denial beats a permission.
     */
    private static Entry best(List<Entry> entries, String node, Instant now) {
        Entry best = null;
        for (Entry entry : entries) {
            if (entry.rule == null || !entry.active(now) || !entry.rule.matches(node)) {
                continue;
            }
            if (best == null || compare(entry, best) > 0) {
                best = entry;
            }
        }
        return best;
    }

    private static int compare(Entry a, Entry b) {
        int bySpecificity = Integer.compare(a.rule.specificity(), b.rule.specificity());
        if (bySpecificity != 0) {
            return bySpecificity;
        }
        int byServer = Boolean.compare(a.serverSpecific, b.serverSpecific);
        if (byServer != 0) {
            return byServer;
        }
        return Boolean.compare(!a.rule.allow(), !b.rule.allow());
    }

    /** Where the rules in force right now come from, such as "role moderator (g-1f3a)"; for showing to people. */
    public List<String> origins(Instant now) {
        List<String> origins = new ArrayList<>();
        for (List<Entry> entries : List.of(fromRoles, direct)) {
            for (Entry entry : entries) {
                if (!entry.active(now)) {
                    continue;
                }
                String origin = entry.rule != null && entries == direct ? entry.rule + " (" + entry.origin + ")" : entry.origin;
                if (!origins.contains(origin)) {
                    origins.add(origin);
                }
            }
        }
        return origins;
    }

    /** The highest weight among the roles held right now; 0 without any. */
    public int weight(Instant now) {
        int weight = 0;
        for (Entry entry : fromRoles) {
            if (entry.active(now)) {
                weight = Math.max(weight, entry.weight);
            }
        }
        return weight;
    }

    /**
     * Whether the player may log in as staff at all: something they hold right now allows a
     * staff node. Logging in is what turns that into rights.
     */
    public boolean canLogInAsStaff(Instant now) {
        for (PermissionNode node : org.xcore.plugin.permission.PermissionNodes.all()) {
            if (node.access() != Access.STAFF) {
                continue;
            }
            Entry match = best(direct, node.name(), now);
            if (match == null) {
                match = best(fromRoles, node.name(), now);
            }
            if (match != null && match.rule.allow()) {
                return true;
            }
        }
        return false;
    }
}
