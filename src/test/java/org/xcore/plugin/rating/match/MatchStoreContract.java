package org.xcore.plugin.rating.match;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** What every {@link MatchStore} must do, run against each implementation. */
abstract class MatchStoreContract {
    static final Instant T0 = Instant.parse("2026-10-01T12:00:00Z");

    abstract MatchStore store();

    static MatchRecord match(String ladder, String id, Instant ended, String... uuids) {
        List<MatchParticipant> participants = new ArrayList<>();
        for (int i = 0; i < uuids.length; i++) {
            boolean won = i % 2 == 0;
            participants.add(MatchParticipant.counted(uuids[i], "Player " + uuids[i], won ? 1 : 2, won ? 1 : 2, won,
                            1000, won ? 16 : -16, won ? "winner" : "defeated")
                    .withParticipation(1.0)
                    .withExtra(Map.of("hexes", i)));
        }
        return MatchRecord.of(ladder, 1, id, "v1", null,
                new MatchReport(ended.minusSeconds(300), ended, null, "Glacier", participants));
    }

    @Test
    @DisplayName("a recorded match is found whole, with every participant as it was written")
    void record_find() {
        MatchRecord match = match("duel", "m1", T0, "a", "b", "c", "d");
        store().record(match);

        MatchRecord found = store().find("duel", "m1").orElseThrow();
        assertThat(found).isEqualTo(match);
        assertThat(found.players()).isEqualTo(4);
        assertThat(found.teamSizes()).containsExactlyInAnyOrderEntriesOf(Map.of(1, 2, 2, 2));
        assertThat(found.participant("b").orElseThrow().figure("hexes")).isEqualTo(1);
        assertThat(store().find("other", "m1")).isEmpty();
    }

    @Test
    @DisplayName("recording the same match again replaces it instead of adding a second one")
    void record_replaces() {
        store().record(match("duel", "m1", T0, "a", "b"));
        store().record(match("duel", "m1", T0, "a", "b", "c"));

        assertThat(store().count("duel", "a")).isEqualTo(1);
        assertThat(store().find("duel", "m1").orElseThrow().players()).isEqualTo(3);
    }

    @Test
    @DisplayName("a page holds the player's own entry only, newest first")
    void page_projectsPlayer() {
        store().record(match("duel", "old", T0, "a", "b"));
        store().record(match("duel", "new", T0.plusSeconds(60), "b", "a"));
        store().record(match("duel", "other", T0.plusSeconds(120), "c", "d"));
        store().record(match("hexed", "elsewhere", T0.plusSeconds(180), "a", "c"));

        MatchPage page = store().page("duel", "a", 10, null);

        assertThat(page.matches()).extracting(MatchRecord::matchId).containsExactly("new", "old");
        assertThat(page.matches()).allSatisfy(match -> {
            assertThat(match.participants()).extracting(MatchParticipant::uuid).containsExactly("a");
            assertThat(match.players()).isEqualTo(2);
        });
        assertThat(page.hasNext()).isFalse();
        assertThat(store().count("duel", "a")).isEqualTo(2);
    }

    @Test
    @DisplayName("pages run through every match exactly once, also where matches ended at the same moment")
    void page_cursorHasNoGapsOrRepeats() {
        List<MatchRecord> recorded = new ArrayList<>();
        for (int i = 0; i < 23; i++) {
            // Three matches share each end time.
            MatchRecord match = match("duel", "m" + (char) ('a' + i), T0.plusSeconds(i / 3), "p", "q");
            store().record(match);
            recorded.add(match);
        }
        List<String> newestFirst = recorded.stream()
                .sorted(java.util.Comparator.comparing(MatchRecord::endedAt)
                        .thenComparing(match -> match.id()).reversed())
                .map(MatchRecord::matchId)
                .toList();

        List<String> seen = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            MatchPage page = store().page("duel", "p", 5, cursor);
            page.matches().forEach(match -> seen.add(match.matchId()));
            cursor = page.nextCursor();
            pages++;
        } while (cursor != null);

        assertThat(pages).isEqualTo(5);
        assertThat(seen).isEqualTo(newestFirst);
    }

    @Test
    @DisplayName("the history starts with the oldest match of the ladder")
    void firstRecorded() {
        assertThat(store().firstRecorded("duel")).isEmpty();
        store().record(match("duel", "b", T0.plusSeconds(60), "a", "b"));
        store().record(match("duel", "a", T0, "a", "b"));
        store().record(match("hexed", "c", T0.minusSeconds(60), "a", "b"));

        assertThat(store().firstRecorded("duel")).contains(T0);
    }

    @Test
    @DisplayName("a merged account takes the matches of the one merged into it")
    void reassignPlayer() {
        store().record(match("duel", "m1", T0, "old", "x"));
        store().record(match("hexed", "m2", T0, "y", "old"));
        store().record(match("duel", "m3", T0, "x", "y"));

        assertThat(store().reassignPlayer(null, "old", "new")).isEqualTo(2);

        assertThat(store().count("duel", "old")).isZero();
        assertThat(store().count("duel", "new")).isEqualTo(1);
        assertThat(store().count("hexed", "new")).isEqualTo(1);
        MatchParticipant moved = store().find("duel", "m1").orElseThrow().participant("new").orElseThrow();
        assertThat(moved.name()).isEqualTo("Player old");
        assertThat(moved.delta()).isEqualTo(16);
    }

    @Test
    @DisplayName("where both merged accounts played the same match, the target keeps its own entry only")
    void reassignPlayer_doesNotDuplicate() {
        store().record(match("duel", "m1", T0, "old", "main", "x"));

        store().reassignPlayer(null, "old", "main");

        MatchRecord match = store().find("duel", "m1").orElseThrow();
        assertThat(match.participants()).extracting(MatchParticipant::uuid).containsExactly("main", "x");
        assertThat(match.participant("main").orElseThrow().name()).isEqualTo("Player main");
        assertThat(store().count("duel", "main")).isEqualTo(1);
    }

    @Test
    @DisplayName("line-ups count the players the match counted for, or everyone when it counted for nobody")
    void lineup_countsCountedPlayers() {
        List<MatchParticipant> rated = List.of(
                MatchParticipant.counted("a", "A", 1, 1, true, 1000, 10, "winner"),
                MatchParticipant.counted("b", "B", 2, 2, false, 1000, -10, "defeated"),
                MatchParticipant.uncounted("late", "Late", 2, 2, false, 1000, "late_join"));
        MatchRecord match = MatchRecord.of("duel", 1, "m1", "v1", null,
                new MatchReport(T0.minusSeconds(60), T0, null, null, rated));
        assertThat(match.players()).isEqualTo(2);
        assertThat(match.teamSizes()).containsExactlyInAnyOrderEntriesOf(Map.of(1, 1, 2, 1));
        assertThat(match.participants()).hasSize(3);

        List<MatchParticipant> unrated = List.of(
                MatchParticipant.uncounted("a", "A", 1, 1, true, 1000, "match_unrated"),
                MatchParticipant.uncounted("b", "B", 1, 1, true, 1000, "match_unrated"));
        MatchRecord nobody = MatchRecord.of("duel", 1, "m2", "v1", "not_enough_players",
                new MatchReport(T0.minusSeconds(60), T0, null, null, unrated));
        assertThat(nobody.players()).isEqualTo(2);
        assertThat(nobody.teamSizes()).containsExactlyEntriesOf(Map.of(1, 2));
    }
}
