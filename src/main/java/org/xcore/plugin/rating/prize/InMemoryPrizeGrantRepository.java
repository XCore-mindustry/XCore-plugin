package org.xcore.plugin.rating.prize;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** For tests and read-only deployments. */
public class InMemoryPrizeGrantRepository implements PrizeGrantRepository {
    private final Map<String, PrizeGrant> grants = new ConcurrentHashMap<>();

    @Override
    public boolean createIfAbsent(PrizeGrant grant) {
        return grants.putIfAbsent(grant.id(), grant) == null;
    }

    @Override
    public List<PrizeGrant> findBySeason(String seasonId) {
        return grants.values().stream()
                .filter(grant -> grant.seasonId().equals(seasonId))
                .sorted(Comparator.comparingInt(PrizeGrant::place)
                        .thenComparingInt(PrizeGrant::prizeIndex)
                        .thenComparing(PrizeGrant::playerUuid))
                .toList();
    }

    @Override
    public boolean transition(String id, PrizeStatus expected, PrizeStatus status, String by, String note,
                              Instant now) {
        boolean[] changed = {false};
        grants.computeIfPresent(id, (key, grant) -> {
            if (grant.status() != expected) return grant;
            changed[0] = true;
            return grant.with(status, by, note, now);
        });
        return changed[0];
    }

    @Override
    public int markDelivered(String seasonId, int place, String by, String note, Instant now) {
        int changed = 0;
        for (PrizeGrant grant : findBySeason(seasonId)) {
            if (grant.place() != place || grant.status().settled()) continue;
            if (transition(grant.id(), grant.status(), PrizeStatus.DELIVERED, by, note, now)) changed++;
        }
        return changed;
    }
}
