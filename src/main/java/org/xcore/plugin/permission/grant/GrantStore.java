package org.xcore.plugin.permission.grant;

import com.mongodb.client.ClientSession;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Where the grants of players are kept: one document per player. Every call is I/O. */
public interface GrantStore {

    /** The player's grants; an empty document at revision 0 when nothing was ever written. */
    GrantDocument find(String uuid);

    /** The documents of those of {@code uuids} that have one. */
    Map<String, GrantDocument> findAll(Collection<String> uuids);

    /**
     * Replaces the player's grants if nobody wrote in between.
     *
     * @param session          the transaction to join, or null to write on its own
     * @param expectedRevision the revision the new list was computed from
     * @return the document as written, at {@code expectedRevision + 1}; empty when the stored
     *         revision is no longer the expected one and the caller has to start over
     */
    Optional<GrantDocument> replace(@Nullable ClientSession session, String uuid, long expectedRevision, List<Grant> grants);

    /** The players who still carry a grant that ran out before {@code now}. */
    List<String> findUuidsWithExpiredGrants(Instant now);
}
