package org.xcore.plugin.rating.prize;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/** Stores prize grants. Blocking, like the other rating stores. */
public interface PrizeGrantRepository {

    /** @return false when a grant with that id already exists; it is left untouched */
    boolean createIfAbsent(PrizeGrant grant);

    List<PrizeGrant> findBySeason(String seasonId);

    /** Every grant a player has been promised, in no particular order. */
    List<PrizeGrant> findByPlayer(String playerUuid);

    /**
     * Sets a grant's status only while it still has {@code expected}, so two servers racing to
     * deliver the same grant cannot both win.
     *
     * @return whether this call changed it
     */
    boolean transition(String id, PrizeStatus expected, PrizeStatus status, String by, String note, Instant now);

    /**
     * Marks a person's delivery of every unsettled grant of one place of a season.
     *
     * @param playerUuid limits it to that player's grants; {@code null} settles the whole place
     * @return how many grants changed
     */
    int markDelivered(String seasonId, int place, @Nullable String playerUuid, String by, String note, Instant now);
}
