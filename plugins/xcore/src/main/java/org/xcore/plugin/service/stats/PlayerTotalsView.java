package org.xcore.plugin.service.stats;

import org.bson.Document;
import org.xcore.plugin.model.AggregatedPlayerStats;
import org.xcore.plugin.model.ModeStatsSummary;
import org.xcore.plugin.model.PlayerStatsOverview;
import org.xcore.plugin.model.PlayerStatsOverview.RecentMatch;

import java.util.List;
import java.util.Map;

import static org.xcore.plugin.service.stats.ParticipantOutcomes.number;

/** DTO adapter only; it never decides whether a participant qualified for a result. */
public final class PlayerTotalsView {
    private static final int RECENT_SHOWN = 5;

    private PlayerTotalsView() {
    }

    public static PlayerStatsOverview overview(Document totals) {
        return overview(totals, Map.of());
    }

    /** @param mapNames map names by the hex id stored in {@code recent.map_id} */
    public static PlayerStatsOverview overview(Document totals, Map<String, String> mapNames) {
        // Every stored game upserts its owners in the same transaction, so no document means no games yet.
        if (totals == null) return empty("ready", false);
        if (!"ready".equals(totals.getString("status"))) return empty("pending", true);
        if (number(totals, "schema_version") != 1) throw new IllegalStateException("Unsupported player totals schema");

        var all = totals.get("all", Document.class);
        var modes = totals.get("modes", Document.class);
        var rebuild = totals.get("rebuild", Document.class);
        var recent = totals.getList("recent", Document.class, List.of()).stream().limit(RECENT_SHOWN)
                .map(row -> {
                    String mapId = row.getString("map_id");
                    return new RecentMatch(row.getString("mode"), row.getString("outcome"), number(row, "ended_at"),
                            mapId == null ? null : mapNames.get(mapId));
                })
                .toList();
        var overall = new AggregatedPlayerStats(number(all, "games"), number(all, "wins"),
                number(all, "blocks_built"), number(all, "blocks_deconstructed"), 0, 0, 0);
        boolean dirty = rebuild != null && Boolean.TRUE.equals(rebuild.getBoolean("dirty"));
        return new PlayerStatsOverview(overall,
                mode(modes.get("PVP", Document.class), false),
                mode(modes.get("SURVIVAL", Document.class), true),
                mode(modes.get("HEXED", Document.class), false),
                "ready", dirty, true, number(all, "left"), number(all, "streak"), recent);
    }

    private static PlayerStatsOverview empty(String status, boolean updating) {
        return new PlayerStatsOverview(AggregatedPlayerStats.EMPTY, ModeStatsSummary.EMPTY, ModeStatsSummary.EMPTY,
                ModeStatsSummary.EMPTY, status, updating, true, 0, 0, List.of());
    }

    /** Survival rates count only games played to the end. */
    private static ModeStatsSummary mode(Document counters, boolean completedOnly) {
        if (counters == null) return ModeStatsSummary.EMPTY;
        long waves = number(counters, "wave_n");
        int averageWave = waves == 0 ? 0 : (int) Math.round((double) number(counters, "wave_sum") / waves);
        return new ModeStatsSummary(number(counters, "games"), number(counters, "wins"),
                Math.toIntExact(number(counters, "best_wave")), averageWave,
                Math.toIntExact(number(counters, "best_place")), number(counters, "top3"),
                number(counters, "left"), number(counters, "streak"), number(counters, "best_streak"),
                number(counters, "play_ms"), completedOnly);
    }
}
