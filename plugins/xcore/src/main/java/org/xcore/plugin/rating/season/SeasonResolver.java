package org.xcore.plugin.rating.season;

import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * This server's view of which season every ladder is in. Reads never block; the view is
 * brought up to date by {@link SeasonLifecycleService}.
 */
@Singleton
public class SeasonResolver {
    private volatile Map<String, View> views = Map.of();

    /** The season matches currently count towards, empty for a ladder without seasons. */
    public Optional<Season> current(String ladderId) {
        View view = views.get(ladderId);
        return view == null ? Optional.empty() : Optional.of(view.current());
    }

    /** Finished seasons of a ladder that are not archived yet. */
    public List<Season> closing(String ladderId) {
        View view = views.get(ladderId);
        return view == null ? List.of() : view.closing();
    }

    /**
     * Replaces the view with the open seasons just read from storage.
     *
     * @return what changed for ladders this server was already watching; a ladder seen for
     *         the first time produces nothing
     */
    synchronized List<SeasonChange> update(List<Season> open) {
        Map<String, List<Season>> byLadder = new HashMap<>();
        for (Season season : open) {
            byLadder.computeIfAbsent(season.ladderId(), _ -> new ArrayList<>()).add(season);
        }

        Map<String, View> updated = new HashMap<>();
        List<SeasonChange> changes = new ArrayList<>();
        byLadder.forEach((ladderId, seasons) -> {
            View view = View.of(seasons);
            updated.put(ladderId, view);
            View before = views.get(ladderId);
            if (before != null) {
                change(before, view).ifPresent(changes::add);
            }
        });
        views = Map.copyOf(updated);
        return changes;
    }

    private static Optional<SeasonChange> change(View before, View after) {
        Season current = after.current();
        if (current.number() != before.current().number()) {
            Season previous = after.closing().stream()
                    .filter(season -> season.number() == before.current().number())
                    .findFirst()
                    .orElse(before.current());
            return Optional.of(new SeasonChange.Started(previous, current));
        }
        Set<String> notices = new HashSet<>(current.sentNotices());
        notices.removeAll(before.current().sentNotices());
        return notices.isEmpty() ? Optional.empty() : Optional.of(new SeasonChange.NoticeDue(current, notices));
    }

    /**
     * @param current the active season, or the newest closing one in the instant between a
     *                season closing and its successor being stored
     */
    private record View(Season current, List<Season> closing) {
        static View of(List<Season> open) {
            Season current = open.getFirst();
            for (Season season : open) {
                boolean better = season.active() != current.active()
                        ? season.active()
                        : season.number() > current.number();
                if (better) {
                    current = season;
                }
            }
            return new View(current, open.stream().filter(season -> !season.active()).toList());
        }
    }
}
