package org.xcore.plugin.rating.match;

import com.mongodb.client.ClientSession;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Non-durable {@link MatchStore} with the same semantics as the MongoDB one.
 * Meant for tests of modes built on the ladder engine, in this plugin and in dependants.
 */
public final class InMemoryMatchStore implements MatchStore {
    private static final Comparator<MatchRecord> NEWEST_FIRST = Comparator
            .comparing(MatchRecord::endedAt).reversed()
            .thenComparing(MatchRecord::id, Comparator.reverseOrder());

    private final Map<String, MatchRecord> matches = new LinkedHashMap<>();

    @Override
    public synchronized void record(MatchRecord match) {
        matches.put(match.id(), match);
    }

    @Override
    public synchronized MatchPage page(String ladderId, String uuid, int limit, @Nullable String cursor) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Limit must be 1.." + MAX_PAGE_SIZE);
        }
        MatchCursor position = MatchCursor.decode(cursor);
        List<MatchRecord> all = playerMatches(ladderId, uuid).stream()
                .filter(match -> position == null || position.precedes(match))
                .map(match -> match.withParticipants(match.participant(uuid).stream().toList()))
                .toList();
        if (all.size() <= limit) {
            return new MatchPage(all, null);
        }
        List<MatchRecord> page = all.subList(0, limit);
        return new MatchPage(page, MatchCursor.after(page.getLast()).encode());
    }

    @Override
    public synchronized long count(String ladderId, String uuid) {
        return playerMatches(ladderId, uuid).size();
    }

    @Override
    public synchronized Optional<MatchRecord> find(String ladderId, String matchId) {
        return Optional.ofNullable(matches.get(MatchRecord.id(ladderId, matchId)));
    }

    @Override
    public synchronized Optional<Instant> firstRecorded(String ladderId) {
        return matches.values().stream()
                .filter(match -> match.ladder().equals(ladderId))
                .map(MatchRecord::endedAt)
                .min(Comparator.naturalOrder());
    }

    @Override
    public synchronized long reassignPlayer(@Nullable ClientSession session, String sourceUuid, String targetUuid) {
        long changed = 0;
        for (var entry : matches.entrySet()) {
            MatchRecord match = entry.getValue();
            if (match.participant(sourceUuid).isEmpty()) {
                continue;
            }
            List<MatchParticipant> moved = new ArrayList<>();
            for (MatchParticipant p : match.participants()) {
                moved.add(p.uuid().equals(sourceUuid) ? new MatchParticipant(targetUuid, p.name(), p.team(),
                        p.placement(), p.win(), p.ratingBefore(), p.delta(), p.ratingAfter(), p.counted(),
                        p.reason(), p.participation(), p.extra()) : p);
            }
            entry.setValue(match.withParticipants(moved));
            changed++;
        }
        return changed;
    }

    /** Every match held, whole, in no particular order. */
    public synchronized List<MatchRecord> all() {
        return List.copyOf(matches.values());
    }

    private List<MatchRecord> playerMatches(String ladderId, String uuid) {
        return matches.values().stream()
                .filter(match -> match.ladder().equals(ladderId) && match.participant(uuid).isPresent())
                .sorted(NEWEST_FIRST)
                .toList();
    }
}
