package org.xcore.plugin.permission.grant;

import java.time.Instant;
import java.util.List;

/**
 * Everything granted to one player. Kept apart from the player's profile, which is saved as a
 * whole and would overwrite a grant written in between.
 *
 * @param revision grows by one on every write; 0 for a player nothing was ever written for
 */
public record GrantDocument(String uuid, long revision, List<Grant> grants) {

    /** Enough for any real member of staff, and a bound on what a runaway caller can store. */
    public static final int MAX_GRANTS = 64;

    public GrantDocument {
        grants = List.copyOf(grants);
    }

    public static GrantDocument empty(String uuid) {
        return new GrantDocument(uuid, 0, List.of());
    }

    public List<Grant> active(Instant now) {
        return grants.stream().filter(grant -> !grant.isExpired(now)).toList();
    }
}
