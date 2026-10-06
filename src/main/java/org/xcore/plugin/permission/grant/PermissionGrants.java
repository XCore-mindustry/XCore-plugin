package org.xcore.plugin.permission.grant;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.database.MongoTransactions;
import org.xcore.plugin.model.AuditAction;
import org.xcore.plugin.model.AuditActor;
import org.xcore.plugin.model.AuditActorType;
import org.xcore.plugin.model.AuditAppendCommand;
import org.xcore.plugin.model.AuditDetails;
import org.xcore.plugin.model.AuditOrigin;
import org.xcore.plugin.model.AuditOriginChannel;
import org.xcore.plugin.model.AuditTarget;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.permission.Actor;
import org.xcore.plugin.permission.role.PermissionModel;
import org.xcore.plugin.permission.role.PermissionRoles;
import org.xcore.plugin.permission.role.PermissionSet;
import org.xcore.plugin.permission.role.Rule;
import org.xcore.plugin.model.AuditAppendResult;
import org.xcore.plugin.service.moderation.AuditService;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Reads and changes what players are granted. Every method is I/O: never call one on the game
 * thread.
 * <p>
 * A change and its audit record are written in one transaction, and the players' sessions are
 * told afterwards through {@link #onChanged}.
 */
@Singleton
public class PermissionGrants {

    /** What a change did. */
    public record Change(GrantDocument document, boolean changed, List<Grant> added, List<Grant> removed) {
        static Change none(GrantDocument document) {
            return new Change(document, false, List.of(), List.of());
        }
    }

    /** The request cannot be carried out as given; the message is for whoever asked. */
    public static class GrantException extends RuntimeException {
        public GrantException(String message) {
            super(message);
        }
    }

    static final String AUDIT_KIND = "permission";
    private static final int WRITE_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GrantStore store;
    private final MongoTransactions transactions;
    private final AuditService audit;
    private final PermissionRoles roles;
    private final List<BiConsumer<String, Long>> listeners = new CopyOnWriteArrayList<>();

    @Inject
    public PermissionGrants(GrantStore store, MongoTransactions transactions, AuditService audit, PermissionRoles roles) {
        this.store = store;
        this.transactions = transactions;
        this.audit = audit;
        this.roles = roles;
    }

    /** Called with the player's uuid and the new revision after each change that was written. */
    public void onChanged(BiConsumer<String, Long> listener) {
        listeners.add(listener);
    }

    public GrantDocument load(String uuid) {
        return store.find(uuid);
    }

    public Map<String, GrantDocument> loadAll(java.util.Collection<String> uuids) {
        return store.findAll(uuids);
    }

    /** The weight of a player who is not online here, for the hierarchy. */
    public int weightOf(String uuid) {
        Instant now = roles.clock().instant();
        return PermissionSet.compile(roles.model(), store.find(uuid), roles.serverName(), false, now).weight(now);
    }

    /**
     * @param server   the only server the role is given on, or null for all of them
     * @param duration how long it lasts, or null for good
     */
    public Change addRole(PlayerData target, String role, String server, Duration duration, String reason, Actor by) {
        String roleName = normalize(role);
        if (roles.model().role(roleName).isEmpty()) {
            throw new GrantException("Unknown role '" + role + "'. Known roles: " + String.join(", ", roles.model().roles().keySet()));
        }
        Instant now = roles.clock().instant();
        Grant grant = new Grant(newId(), roleName, null, true, blankToNull(server), expiry(now, duration),
                Grant.SOURCE_MANUAL, issuer(by), now, reason);
        return change(target, by, reason, "role-add", document -> {
            if (document.active(now).stream().anyMatch(existing -> existing.sameSubject(grant)
                    && Objects.equals(existing.expiresAt(), grant.expiresAt()))) {
                return null;
            }
            List<Grant> grants = withoutSubject(document.active(now), grant);
            grants.add(grant);
            return grants;
        });
    }

    /**
     * Removes a role given by hand. A role that comes from Discord is taken away in Discord: the
     * next sync would only give it back.
     *
     * @param roleOrGrantId the role's name, or the id of one grant
     */
    public Change removeRole(PlayerData target, String roleOrGrantId, String reason, Actor by) {
        String wanted = normalize(roleOrGrantId);
        Instant now = roles.clock().instant();
        return change(target, by, reason, "role-remove", document -> {
            List<Grant> kept = new ArrayList<>();
            boolean removedAny = false;
            for (Grant grant : document.active(now)) {
                boolean named = grant.isRole() && (grant.id().equals(wanted) || grant.role().equals(wanted));
                if (named && grant.isFromDiscord()) {
                    throw new GrantException("Grant " + grant.id() + " (" + grant.role() + ") comes from " + grant.source()
                            + "; remove the Discord role instead");
                }
                if (named) {
                    removedAny = true;
                } else {
                    kept.add(grant);
                }
            }
            if (!removedAny) {
                throw new GrantException("No role or grant '" + roleOrGrantId + "' to remove");
            }
            return kept;
        });
    }

    /** Takes a node, or everything under a wildcard, away from a player whatever their roles say. */
    public Change deny(PlayerData target, String node, Duration duration, String reason, Actor by) {
        Rule rule;
        try {
            rule = Rule.parse(node);
        } catch (IllegalArgumentException e) {
            throw new GrantException(e.getMessage());
        }
        if (!rule.allow()) {
            throw new GrantException("Name the node to deny without a leading '-'");
        }
        Instant now = roles.clock().instant();
        Grant grant = new Grant(newId(), null, rule.pattern(), false, null, expiry(now, duration),
                Grant.SOURCE_MANUAL, issuer(by), now, reason);
        return change(target, by, reason, "deny", document -> {
            if (document.active(now).stream().anyMatch(existing -> existing.sameSubject(grant)
                    && Objects.equals(existing.expiresAt(), grant.expiresAt()))) {
                return null;
            }
            List<Grant> grants = withoutSubject(document.active(now), grant);
            grants.add(grant);
            return grants;
        });
    }

    public Change undeny(PlayerData target, String grantId, String reason, Actor by) {
        String wanted = normalize(grantId);
        Instant now = roles.clock().instant();
        return change(target, by, reason, "undeny", document -> {
            List<Grant> kept = new ArrayList<>(document.active(now));
            if (!kept.removeIf(grant -> !grant.isRole() && grant.id().equals(wanted))) {
                throw new GrantException("No denial with id '" + grantId + "'");
            }
            return kept;
        });
    }

    /**
     * Makes the grants that come from Discord match the bound Discord roles the player holds.
     * Grants from any other source are left alone.
     *
     * @param discordRoleIds the bound roles the member holds
     * @param complete       false when the member could not be loaded fully: roles are then only
     *                       added, never taken away
     */
    public Change syncDiscord(PlayerData target, List<String> discordRoleIds, boolean complete, Actor by) {
        PermissionModel model = roles.model();
        Instant now = roles.clock().instant();
        List<Grant> wanted = new ArrayList<>();
        for (String roleId : discordRoleIds) {
            // A role the file does not bind is none of our business, whoever sent it.
            model.discord().binding(roleId).ifPresent(binding -> wanted.add(new Grant(newId(), binding.role(), null, true,
                    binding.server(), null, Grant.discordSource(roleId), issuer(by), now, "Discord role sync")));
        }
        return change(target, by, "Discord role sync", "discord-sync", document -> {
            List<Grant> result = new ArrayList<>();
            boolean changed = false;
            for (Grant existing : document.active(now)) {
                boolean stillWanted = wanted.stream().anyMatch(existing::sameSubject);
                if (existing.isFromDiscord() && complete && !stillWanted) {
                    changed = true;
                } else {
                    result.add(existing);
                }
            }
            for (Grant grant : wanted) {
                if (result.stream().noneMatch(grant::sameSubject)) {
                    result.add(grant);
                    changed = true;
                }
            }
            return changed ? result : null;
        });
    }

    /**
     * Gives a role once, marked with {@code source}; for the one-off move from the admin flag.
     * Doing it again changes nothing.
     */
    public Change importRole(PlayerData target, String role, String source, String reason, Actor by) {
        Instant now = roles.clock().instant();
        Grant grant = new Grant(newId(), role, null, true, null, null, source, issuer(by), now, reason);
        return change(target, by, reason, "migrate", document -> {
            if (document.active(now).stream().anyMatch(existing -> existing.isRole() && existing.role().equals(role)
                    && existing.server() == null)) {
                return null;
            }
            List<Grant> grants = new ArrayList<>(document.active(now));
            grants.add(grant);
            return grants;
        });
    }

    /**
     * Writes an audit record about a player's staff access that is not a change of grants,
     * such as a password reset. Never put a secret or a hash into it.
     */
    public void record(PlayerData target, Actor by, String operation, String reason) {
        AuditAppendResult result = audit.append(auditRecord(target, by, reason, operation, null, List.of(), List.of()));
        if (result != null && !result.isSuccess()) {
            PLog.err("[Permissions] The audit record for @ of @ could not be stored: @", operation, target.uuid,
                    result.getMessage().orElse("unknown error"));
        }
    }

    /** Drops the grants that have run out. Returns how many players were cleaned up. */
    public int prune() {
        Instant now = roles.clock().instant();
        int cleaned = 0;
        for (String uuid : store.findUuidsWithExpiredGrants(now)) {
            for (int attempt = 0; attempt < WRITE_ATTEMPTS; attempt++) {
                GrantDocument document = store.find(uuid);
                List<Grant> active = document.active(now);
                if (active.size() == document.grants().size()) {
                    break;
                }
                // Expired grants gave nothing already, so sessions need no telling.
                if (store.replace(null, uuid, document.revision(), active).isPresent()) {
                    cleaned++;
                    break;
                }
            }
        }
        return cleaned;
    }

    /** Computes the new list from the current document, or returns null when nothing would change. */
    private interface Edit {
        List<Grant> apply(GrantDocument current);
    }

    private Change change(PlayerData target, Actor by, String reason, String operation, Edit edit) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(by, "by");
        for (int attempt = 0; attempt < WRITE_ATTEMPTS; attempt++) {
            GrantDocument current = store.find(target.uuid);
            List<Grant> next = edit.apply(current);
            if (next == null) {
                return Change.none(current);
            }
            if (next.size() > GrantDocument.MAX_GRANTS) {
                throw new GrantException("A player can hold at most " + GrantDocument.MAX_GRANTS + " grants");
            }
            List<Grant> added = next.stream().filter(grant -> !current.grants().contains(grant)).toList();
            List<Grant> removed = current.grants().stream().filter(grant -> !next.contains(grant)).toList();

            Optional<GrantDocument> written = transactions.run(session -> {
                Optional<GrantDocument> stored = store.replace(session, target.uuid, current.revision(), next);
                if (stored.isPresent()) {
                    AuditAppendResult result = audit.append(session,
                            auditRecord(target, by, reason, operation, stored.get(), added, removed));
                    if (result != null && !result.isSuccess()) {
                        // Aborts the transaction: a grant nobody can trace is worse than no grant.
                        throw new IllegalStateException("The audit record could not be stored: "
                                + result.getMessage().orElse("unknown error"));
                    }
                }
                return stored;
            });
            if (written.isPresent()) {
                notifyChanged(target.uuid, written.get().revision());
                return new Change(written.get(), true, added, removed);
            }
        }
        throw new GrantException("The grants of " + target.uuid + " kept changing; try again");
    }

    private void notifyChanged(String uuid, long revision) {
        for (BiConsumer<String, Long> listener : listeners) {
            try {
                listener.accept(uuid, revision);
            } catch (RuntimeException e) {
                // The change is stored; the periodic reread will deliver it.
                PLog.err("A permission change listener failed for @: @", uuid, e.getMessage());
            }
        }
    }

    private AuditAppendCommand auditRecord(PlayerData target, Actor by, String reason, String operation,
                                           GrantDocument written, List<Grant> added, List<Grant> removed) {
        Map<String, String> extra = new LinkedHashMap<>();
        extra.put("kind", AUDIT_KIND);
        extra.put("operation", operation);
        if (written != null) {
            extra.put("revision", String.valueOf(written.revision()));
        }
        extra.put("server", roles.serverName());
        if (!added.isEmpty()) {
            extra.put("added", describe(added));
        }
        if (!removed.isEmpty()) {
            extra.put("removed", describe(removed));
        }
        Instant expiresAt = added.size() == 1 ? added.get(0).expiresAt() : null;
        return AuditAppendCommand.builder()
                .action(AuditAction.NOTE)
                .target(AuditTarget.builder()
                        .uuid(target.uuid)
                        .pid(target.pid)
                        .nameSnapshot(target.nickname == null || target.nickname.isBlank() ? target.uuid : target.nickname)
                        .build())
                .actor(auditActor(by))
                .origin(AuditOrigin.builder()
                        .channel(by instanceof Actor.SystemActor ? AuditOriginChannel.SYSTEM : AuditOriginChannel.SERVER_CONSOLE)
                        .source("xcore-plugin")
                        .serverId(roles.serverName())
                        .build())
                .reason(reason)
                .details(AuditDetails.builder().expiresAt(expiresAt).extra(extra).build())
                .build();
    }

    private AuditActor auditActor(Actor by) {
        return switch (by) {
            case Actor.LocalConsole console -> AuditActor.builder()
                    .type(AuditActorType.SERVER_CONSOLE)
                    .id(console.auditName())
                    .nameSnapshot(console.auditName())
                    .serverId(roles.serverName())
                    .build();
            case Actor.RemoteConsole remote -> AuditActor.builder()
                    .type(AuditActorType.SERVER_CONSOLE)
                    .id(remote.auditName())
                    .nameSnapshot(remote.auditName())
                    .serverId(remote.sourceServer())
                    .build();
            case Actor.SystemActor system -> AuditActor.builder()
                    .type(AuditActorType.SYSTEM)
                    .id(system.auditName())
                    .nameSnapshot(system.auditName())
                    .serverId(roles.serverName())
                    .build();
            case Actor.PlayerActor player -> AuditActor.builder()
                    .type(AuditActorType.PLAYER_ADMIN)
                    .id(player.uuid())
                    .nameSnapshot(player.auditName())
                    .playerUuid(player.uuid())
                    .serverId(roles.serverName())
                    .build();
        };
    }

    /** Who issued a grant, as it is stored on the grant itself. */
    private String issuer(Actor by) {
        return by instanceof Actor.LocalConsole ? by.auditName() + "@" + roles.serverName() : by.auditName();
    }

    private static String describe(List<Grant> grants) {
        List<String> parts = new ArrayList<>();
        for (Grant grant : grants) {
            StringBuilder part = new StringBuilder(grant.id()).append(' ');
            part.append(grant.isRole() ? "role " + grant.role() : (grant.allow() ? "" : "-") + grant.node());
            if (grant.server() != null) {
                part.append(" on ").append(grant.server());
            }
            if (grant.expiresAt() != null) {
                part.append(" until ").append(grant.expiresAt());
            }
            part.append(" [").append(grant.source()).append(']');
            parts.add(part.toString());
        }
        return String.join("; ", parts);
    }

    /** The grants that give something else than {@code grant} does; a renewed grant replaces the old one. */
    private static List<Grant> withoutSubject(List<Grant> grants, Grant grant) {
        List<Grant> kept = new ArrayList<>(grants);
        kept.removeIf(grant::sameSubject);
        return kept;
    }

    private static Instant expiry(Instant now, Duration duration) {
        if (duration == null) {
            return null;
        }
        if (duration.isZero() || duration.isNegative()) {
            throw new GrantException("A grant has to last longer than nothing");
        }
        return now.plus(duration);
    }

    private static String normalize(String text) {
        if (text == null || text.isBlank()) {
            throw new GrantException("Nothing was named");
        }
        return text.strip().toLowerCase(java.util.Locale.ROOT);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private static String newId() {
        byte[] bytes = new byte[4];
        RANDOM.nextBytes(bytes);
        return "g-" + HexFormat.of().formatHex(bytes);
    }
}
