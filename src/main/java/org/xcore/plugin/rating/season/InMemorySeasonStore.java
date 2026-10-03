package org.xcore.plugin.rating.season;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Non-durable {@link SeasonStore} with the same semantics as the MongoDB one.
 * Meant for tests of code built on seasons, in this plugin and in dependants.
 */
public final class InMemorySeasonStore implements SeasonStore {
    private static final Comparator<Season> ORDER = Comparator
            .comparing(Season::ladderId)
            .thenComparing(Comparator.comparingInt(Season::number).reversed());

    private final Map<String, Season> seasons = new HashMap<>();

    @Override
    public synchronized Optional<Season> find(String ladderId, int number) {
        return Optional.ofNullable(seasons.get(Season.id(ladderId, number)));
    }

    @Override
    public synchronized List<Season> list(String ladderId) {
        return all().stream().filter(season -> season.ladderId().equals(ladderId)).toList();
    }

    @Override
    public synchronized List<Season> all() {
        List<Season> all = new ArrayList<>(seasons.values());
        all.sort(ORDER);
        return all;
    }

    @Override
    public synchronized List<Season> open() {
        return all().stream().filter(season -> season.status() != SeasonStatus.ARCHIVED).toList();
    }

    @Override
    public synchronized boolean create(Season season) {
        return seasons.putIfAbsent(season.id(), season) == null;
    }

    @Override
    public synchronized Optional<Season> update(Season expected, Season updated) {
        Season current = seasons.get(expected.id());
        if (current == null || current.revision() != expected.revision()) {
            return Optional.empty();
        }
        Season stored = new Season(current.ladderId(), current.number(), updated.name(), current.startsAt(),
                updated.endsAt(), updated.status(), updated.sentNotices(), updated.podium(), updated.summary(),
                updated.rescheduled(), current.matches(), current.revision() + 1);
        seasons.put(stored.id(), stored);
        return Optional.of(stored);
    }

    @Override
    public synchronized void countMatch(String ladderId, int number) {
        seasons.computeIfPresent(Season.id(ladderId, number), (_, season) -> new Season(
                season.ladderId(), season.number(), season.name(), season.startsAt(), season.endsAt(),
                season.status(), season.sentNotices(), season.podium(), season.summary(), season.rescheduled(),
                season.matches() + 1, season.revision()));
    }
}
