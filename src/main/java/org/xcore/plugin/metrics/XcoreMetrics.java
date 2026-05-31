package org.xcore.plugin.metrics;

public final class XcoreMetrics {
    public static final GaugeDef PLAYERS_ONLINE = new GaugeDef(
            "mindustry_players_online",
            "Current online player count",
            "players",
            LabelSchema.empty()
    );

    public static final CounterDef PLAYER_JOINS_TOTAL = new CounterDef(
            "mindustry_player_joins_total",
            "Total player joins",
            "events",
            LabelSchema.empty()
    );

    public static final CounterDef PLAYER_LEAVES_TOTAL = new CounterDef(
            "mindustry_player_leaves_total",
            "Total player leaves",
            "events",
            LabelSchema.empty()
    );

    private XcoreMetrics() {
    }
}
