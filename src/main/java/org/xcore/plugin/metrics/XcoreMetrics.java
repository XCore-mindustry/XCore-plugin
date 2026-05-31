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

    public static final CounterDef COMMANDS_TOTAL = new CounterDef(
            "xcore_commands_total",
            "Total executed commands",
            "commands",
            LabelSchema.of("command", "source", "result")
    );

    public static final HistogramDef COMMAND_DURATION_SECONDS = new HistogramDef(
            "xcore_command_duration_seconds",
            "Command execution duration",
            "seconds",
            LabelSchema.of("command", "source"),
            new double[]{0.005d, 0.01d, 0.025d, 0.05d, 0.1d, 0.25d, 0.5d, 1.0d, 2.5d, 5.0d}
    );

    private XcoreMetrics() {
    }
}
