package org.xcore.plugin.service.stats;

import org.bson.Document;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** The only participation policy; also interprets legacy reconnect rows during replay. */
public final class ParticipantOutcomes {
    /** Raise together with {@code stats-rebuild all} whenever a rule below changes. */
    public static final int RULES_VERSION = 1;

    private static final Set<String> COUNTED_CATEGORIES = Set.of("PVP", "SURVIVAL", "HEXED", "EVENT");
    private static final long MIN_PRESENCE_MS = 60_000;
    private static final double MIN_PRESENCE_SHARE = 0.10;
    private static final double MIN_FINISHER_SHARE = 0.25;

    public enum Outcome { WIN, LOSS, LEFT, NOT_COUNTED }

    public record Result(Outcome outcome, String reason, long presentMs, long built, long deconstructed, Integer place) {
    }

    private record Interval(long start, long end, String team) {
    }

    private ParticipantOutcomes() {
    }

    public static long number(Document document, String key) {
        return document.get(key) instanceof Number n ? n.longValue() : 0;
    }

    /** The result of one owner whose rows (reconnects, merged accounts) all belong to {@code game}. */
    public static Result of(Document game, List<Document> rows) {
        long start = number(game, "started_at");
        long end = number(game, "ended_at");
        long duration = end - start;
        String winningTeam = game.getString("winning_team");
        var intervals = new ArrayList<Interval>();
        long built = 0;
        long deconstructed = 0;
        long latest = Long.MIN_VALUE;
        boolean atEnd = false;
        boolean won = false;
        boolean conflict = false;
        Integer place = null;

        for (var row : rows) {
            built += Math.max(0, number(row, "blocks_built"));
            deconstructed += Math.max(0, number(row, "blocks_deconstructed"));
            atEnd |= Boolean.TRUE.equals(row.getBoolean("played_to_end"));
            if (row.get("placement") instanceof Number n && n.doubleValue() == n.intValue() && n.intValue() >= 1) {
                if (place != null && place != n.intValue()) conflict = true;
                place = n.intValue();
            }
            for (var span : presence(row, end)) {
                if (number(span, "join_time") <= 0) continue;
                long from = Math.max(start, number(span, "join_time"));
                long to = Math.min(end, number(span, "leave_time"));
                if (from >= to) continue;
                String team = span.getString("team") != null ? span.getString("team") : row.getString("final_team");
                intervals.add(new Interval(from, to, team));
                // The team of the latest presence decides the result.
                if (to >= latest) {
                    latest = to;
                    won = team != null && winningTeam != null
                            ? team.equals(winningTeam)
                            : Boolean.TRUE.equals(row.getBoolean("isWinner"));
                }
            }
        }

        intervals.sort(Comparator.comparingLong(Interval::start).thenComparingLong(Interval::end));
        conflict |= hasOpposingOverlap(intervals);
        long present = union(intervals);
        String category = game.getString("stats_category");

        if (duration <= 0 || start <= 0 || rows.isEmpty()) {
            return new Result(Outcome.NOT_COUNTED, "invalid_data", present, built, deconstructed, place);
        }
        if (conflict) {
            return new Result(Outcome.NOT_COUNTED, "conflicting_participation", present, built, deconstructed, place);
        }
        if (!Boolean.TRUE.equals(game.getBoolean("counted_in_stats")) || category == null
                || !COUNTED_CATEGORIES.contains(category)) {
            return new Result(Outcome.NOT_COUNTED, "excluded_match", present, built, deconstructed, place);
        }
        if ("HEXED".equals(category) && place != null) {
            return new Result(place == 1 ? Outcome.WIN : Outcome.LOSS, "placement", present, built, deconstructed, place);
        }
        if (present < Math.max(MIN_PRESENCE_MS, (long) Math.ceil(duration * MIN_PRESENCE_SHARE))) {
            return new Result(Outcome.NOT_COUNTED, "short_presence", present, built, deconstructed, place);
        }
        if (!atEnd) {
            return new Result(Outcome.LEFT, "left", present, built, deconstructed, place);
        }
        if (present < (long) Math.ceil(duration * MIN_FINISHER_SHARE)) {
            return new Result(Outcome.NOT_COUNTED, "late_join", present, built, deconstructed, place);
        }
        return new Result(won ? Outcome.WIN : Outcome.LOSS, "completed", present, built, deconstructed, place);
    }

    /** Rows written before presence intervals existed have a single join/leave pair. */
    private static List<Document> presence(Document row, long end) {
        var presence = row.getList("presence_intervals", Document.class);
        if (presence != null && !presence.isEmpty()) return presence;
        long leave = number(row, "leave_time");
        return List.of(new Document("join_time", number(row, "join_time")).append("leave_time", leave > 0 ? leave : end));
    }

    /** A merge can put simultaneous opponents under the same owner. Expects intervals sorted by start. */
    private static boolean hasOpposingOverlap(List<Interval> intervals) {
        for (int i = 0; i < intervals.size(); i++) {
            var span = intervals.get(i);
            for (int j = i - 1; j >= 0; j--) {
                var other = intervals.get(j);
                if (other.end > span.start && other.team != null && span.team != null && !other.team.equals(span.team)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Total time covered by sorted intervals, counting overlaps once. */
    private static long union(List<Interval> intervals) {
        long total = 0;
        long from = 0;
        long to = 0;
        for (var span : intervals) {
            if (span.start > to) {
                total += to - from;
                from = span.start;
                to = span.end;
            } else {
                to = Math.max(to, span.end);
            }
        }
        return total + to - from;
    }

    /** Rows grouped by owner, in a stable order. */
    public static Map<String, List<Document>> participants(Document game) {
        var result = new TreeMap<String, List<Document>>();
        for (var row : game.getList("player_stats", Document.class, List.of())) {
            String uuid = row.getString("uuid");
            if (uuid != null && !uuid.isBlank()) result.computeIfAbsent(uuid, ignored -> new ArrayList<>()).add(row);
        }
        return result;
    }
}
