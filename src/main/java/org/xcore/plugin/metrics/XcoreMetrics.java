package org.xcore.plugin.metrics;

public final class XcoreMetrics {
    public static final GaugeDef PLAYERS_ONLINE = new GaugeDef(
            "mindustry_players_online",
            "Current online player count",
            "players",
            LabelSchema.empty()
    );

    public static final GaugeDef WAVE = new GaugeDef(
            "mindustry_wave",
            "Current wave number",
            "wave",
            LabelSchema.empty()
    );

    public static final GaugeDef TPS = new GaugeDef(
            "mindustry_tps",
            "Current server ticks per second",
            "tps",
            LabelSchema.empty()
    );

    public static final GaugeDef PLUGIN_UPTIME_SECONDS = new GaugeDef(
            "xcore_plugin_uptime_seconds",
            "Plugin uptime in seconds",
            "seconds",
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
