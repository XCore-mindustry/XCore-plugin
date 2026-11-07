package org.xcore.plugin.service.stats;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.xcore.plugin.service.stats.ParticipantOutcomes.Outcome;
import org.xcore.plugin.service.stats.ParticipantOutcomes.Result;

import java.util.ArrayList;

import static org.xcore.plugin.service.stats.ParticipantOutcomes.RULES_VERSION;
import static org.xcore.plugin.service.stats.ParticipantOutcomes.number;

/** Pure projection arithmetic. Live writes and replay use exactly this fold. */
public final class PlayerTotals {
    private static final int RECENT_LIMIT = 10;

    private PlayerTotals() {
    }

    public static Document empty(String uuid) {
        return new Document("_id", uuid)
                .append("schema_version", 1)
                .append("rules_version", RULES_VERSION)
                .append("status", "ready")
                .append("all", new Document())
                .append("modes", new Document())
                .append("recent", new ArrayList<Document>());
    }

    /** Games are folded by end time, then by id, so streaks do not depend on arrival order. */
    public static Document order(Document game) {
        return new Document("ended_at", number(game, "ended_at")).append("game_id", game.getObjectId("_id").toHexString());
    }

    public static boolean after(Document game, Document last) {
        if (last == null) return true;
        var next = order(game);
        long ended = number(next, "ended_at");
        long lastEnded = number(last, "ended_at");
        return ended > lastEnded || ended == lastEnded && next.getString("game_id").compareTo(last.getString("game_id")) > 0;
    }

    public static void apply(Document totals, Document game, Result result) {
        totals.put("last_order", order(game));
        if (result.outcome() == Outcome.NOT_COUNTED) return;

        String category = game.getString("stats_category");
        var modes = totals.get("modes", Document.class);
        var mode = modes.get(category, Document.class);
        if (mode == null) {
            mode = new Document();
            modes.put(category, mode);
        }
        accumulate(totals.get("all", Document.class), game, result);
        accumulate(mode, game, result);

        if ("SURVIVAL".equals(category) && result.outcome() != Outcome.LEFT) {
            long wave = Math.max(0, number(game, "waves_reached"));
            mode.put("best_wave", Math.max(number(mode, "best_wave"), wave));
            add(mode, "wave_sum", wave);
            add(mode, "wave_n", 1);
        }
        if ("HEXED".equals(category) && result.place() != null) {
            long place = result.place();
            mode.put("best_place", mode.get("best_place") == null ? place : Math.min(number(mode, "best_place"), place));
            add(mode, "place_sum", place);
            add(mode, "place_n", 1);
            if (place <= 3) add(mode, "top3", 1);
        }

        ObjectId map = game.getObjectId("map");
        var recent = totals.getList("recent", Document.class);
        recent.addFirst(new Document("game_id", game.getObjectId("_id").toHexString())
                .append("match_id", game.getString("match_id"))
                .append("ended_at", number(game, "ended_at"))
                .append("category", category)
                .append("outcome", result.outcome().name())
                .append("map_id", map == null ? null : map.toHexString())
                .append("mode", game.getString("game_mode")));
        if (recent.size() > RECENT_LIMIT) recent.removeLast();
    }

    private static void accumulate(Document counters, Document game, Result result) {
        add(counters, "games", 1);
        add(counters, switch (result.outcome()) {
            case WIN -> "wins";
            case LOSS -> "losses";
            case LEFT -> "left";
            case NOT_COUNTED -> throw new IllegalStateException("Not counted results are not folded");
        }, 1);
        add(counters, "play_ms", result.presentMs());
        add(counters, "blocks_built", result.built());
        add(counters, "blocks_deconstructed", result.deconstructed());

        long streak = result.outcome() == Outcome.WIN ? number(counters, "streak") + 1 : 0;
        counters.put("streak", streak);
        counters.put("best_streak", Math.max(number(counters, "best_streak"), streak));

        long end = number(game, "ended_at");
        counters.put("first_at", counters.containsKey("first_at") ? Math.min(number(counters, "first_at"), end) : end);
        counters.put("last_at", Math.max(number(counters, "last_at"), end));
    }

    private static void add(Document counters, String key, long value) {
        counters.put(key, Math.addExact(number(counters, key), value));
    }
}
