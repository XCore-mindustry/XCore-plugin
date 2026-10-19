package org.xcore.plugin.permission.grant;

import java.time.Instant;
import java.util.List;

/**
 * Everything granted to one player. Kept apart from the player's profile, which is saved as a
 * whole and would overwrite a grant written in between.
 *
 * @param revision         grows by one on every write; 0 for a player nothing was ever written for
 * @param credentialsEpoch grows by one every time the player's password is reset. It lives here
 *                         rather than in the profile so that a profile saved from a stale cache
 *                         cannot take it back, and so that it travels with the grants every
 *                         server already rereads
 */
public record GrantDocument(String uuid, long revision, List<Grant> grants, long credentialsEpoch) {

    /** Enough for any real member of staff, and a bound on what a runaway caller can store. */
    public static final int MAX_GRANTS = 64;

    public GrantDocument {
        grants = List.copyOf(grants);
    }

    public GrantDocument(String uuid, long revision, List<Grant> grants) {
        this(uuid, revision, grants, 0);
    }

    public static GrantDocument empty(String uuid) {
        return new GrantDocument(uuid, 0, List.of());
    }

    public List<Grant> active(Instant now) {
        return grants.stream().filter(grant -> !grant.isExpired(now)).toList();
    }
}
