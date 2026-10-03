package org.xcore.plugin.rating.ladder;

import com.mongodb.client.ClientSession;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Non-durable {@link LadderStore} with the same semantics as the MongoDB one.
 * Meant for tests of modes built on the ladder engine, in this plugin and in dependants.
 */
public final class InMemoryLadderStore implements LadderStore {
    private static final Comparator<LadderStanding> ORDER = Comparator
            .comparingInt(LadderStanding::rating).reversed()
            .thenComparing(LadderStanding::uuid);

    private final Map<Key, LadderStanding> standings = new HashMap<>();
    private final Map<Key, Set<String>> appliedOperations = new HashMap<>();
    private final Map<Key, StandingSeed> seeds = new HashMap<>();
    private final Map<Key, Integer> finalRanks = new HashMap<>();

    @Override
    public synchronized Optional<LadderStanding> find(String ladderId, int season, String uuid) {
        return Optional.ofNullable(standings.get(new Key(ladderId, season, uuid)));
    }

    @Override
    public synchronized Optional<LadderStanding> latestBefore(String ladderId, int season, String uuid) {
        return standings.values().stream()
                .filter(standing -> standing.ladderId().equals(ladderId) && standing.uuid().equals(uuid)
                        && standing.season() < season)
                .max(Comparator.comparingInt(LadderStanding::season));
    }

    @Override
    public synchronized ApplyResult applyOnce(String ladderId, int season, String operationId,
                                              StandingMutation mutation, StandingSeed seed, int minimumRating) {
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("Operation ID must not be blank");
        }
        Key key = new Key(ladderId, season, mutation.uuid());
        LadderStanding current = standings.get(key);
        if (!appliedOperations.computeIfAbsent(key, _ -> new HashSet<>()).add(operationId)) {
            return new ApplyResult(false, current);
        }

        if (current == null && seed.carried()) {
            seeds.put(key, seed);
        }
        int rating = Math.max(minimumRating,
                (current != null ? current.rating() : seed.rating()) + mutation.ratingDelta());
        Map<String, Integer> stats = new HashMap<>(current != null ? current.stats() : Map.of());
        mutation.stats().forEach((name, value) -> stats.merge(name, value, Integer::sum));
        LadderStanding updated = new LadderStanding(ladderId, season, mutation.uuid(), rating,
                Math.max(current != null ? current.peakRating() : 0, rating),
                (current != null ? current.matches() : 0) + 1,
                (current != null ? current.wins() : 0) + (mutation.win() ? 1 : 0),
                stats);
        standings.put(key, updated);
        return new ApplyResult(true, updated);
    }

    @Override
    public synchronized StandingPage top(String ladderId, int season, int limit, @Nullable String cursor) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Limit must be 1.." + MAX_PAGE_SIZE);
        }
        StandingCursor position = StandingCursor.decode(cursor);
        List<LadderStanding> remaining = ordered(ladderId, season).stream()
                .filter(standing -> position == null || position.precedes(standing))
                .toList();
        if (remaining.size() <= limit) {
            return new StandingPage(remaining, null, false);
        }
        List<LadderStanding> page = remaining.subList(0, limit);
        return new StandingPage(page, StandingCursor.after(page.getLast()).encode(), true);
    }

    @Override
    public synchronized OptionalLong rankOf(String ladderId, int season, String uuid) {
        List<LadderStanding> ordered = ordered(ladderId, season);
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).uuid().equals(uuid)) {
                return OptionalLong.of(i + 1L);
            }
        }
        return OptionalLong.empty();
    }

    @Override
    public synchronized long count(String ladderId, int season) {
        return ordered(ladderId, season).size();
    }

    @Override
    public synchronized List<LadderStanding> leaders(String ladderId, int season, int minMatches, int limit) {
        return ordered(ladderId, season).stream()
                .filter(standing -> standing.matches() >= minMatches)
                .limit(limit)
                .toList();
    }

    @Override
    public synchronized int assignFinalRanks(String ladderId, int season) {
        List<LadderStanding> ordered = ordered(ladderId, season);
        for (int i = 0; i < ordered.size(); i++) {
            finalRanks.put(new Key(ladderId, season, ordered.get(i).uuid()), i + 1);
        }
        return ordered.size();
    }

    @Override
    public synchronized int mergePlayer(@Nullable ClientSession session, String sourceUuid, String targetUuid) {
        if (sourceUuid.equals(targetUuid)) {
            throw new IllegalArgumentException("Cannot merge a player's standings into themselves");
        }
        List<Key> sources = standings.keySet().stream().filter(key -> key.uuid().equals(sourceUuid)).toList();
        for (Key sourceKey : sources) {
            LadderStanding source = standings.remove(sourceKey);
            Set<String> operations = appliedOperations.remove(sourceKey);
            Key targetKey = new Key(sourceKey.ladderId(), sourceKey.season(), targetUuid);
            LadderStanding target = standings.get(targetKey);

            Map<String, Integer> stats = new HashMap<>(target != null ? target.stats() : Map.of());
            source.stats().forEach((name, value) -> stats.merge(name, value, Integer::sum));
            standings.put(targetKey, new LadderStanding(sourceKey.ladderId(), sourceKey.season(), targetUuid,
                    Math.max(source.rating(), target != null ? target.rating() : 0),
                    Math.max(source.peakRating(), target != null ? target.peakRating() : 0),
                    source.matches() + (target != null ? target.matches() : 0),
                    source.wins() + (target != null ? target.wins() : 0),
                    stats));
            if (operations != null) {
                appliedOperations.computeIfAbsent(targetKey, _ -> new HashSet<>()).addAll(operations);
            }
        }
        return sources.size();
    }

    /** Seeds a standing directly, bypassing settlement. */
    public synchronized void put(LadderStanding standing) {
        standings.put(new Key(standing.ladderId(), standing.season(), standing.uuid()), standing);
    }

    /** The seed a standing was created from, empty for a player new to the ladder. */
    public synchronized Optional<StandingSeed> seedOf(String ladderId, int season, String uuid) {
        return Optional.ofNullable(seeds.get(new Key(ladderId, season, uuid)));
    }

    /** The rank frozen by {@link #assignFinalRanks}, empty while the season is still open. */
    public synchronized OptionalInt finalRankOf(String ladderId, int season, String uuid) {
        Integer rank = finalRanks.get(new Key(ladderId, season, uuid));
        return rank == null ? OptionalInt.empty() : OptionalInt.of(rank);
    }

    private List<LadderStanding> ordered(String ladderId, int season) {
        List<LadderStanding> ordered = new ArrayList<>();
        for (var entry : standings.entrySet()) {
            if (entry.getKey().ladderId().equals(ladderId) && entry.getKey().season() == season) {
                ordered.add(entry.getValue());
            }
        }
        ordered.sort(ORDER);
        return ordered;
    }

    private record Key(String ladderId, int season, String uuid) {
    }
}
