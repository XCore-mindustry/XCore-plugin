package tilelogger;

import arc.util.Reflect;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.Iconc;
import mindustry.net.Administration.PlayerInfo;
import mindustry.world.Block;
import mindustry.world.Tile;

public class TileState {
    public int x, y, team, block, rotation, config_type;
    public String uuid = "";
    public boolean valid, destroy, legacy, configTruncated;
    public int configBytes;
    public long time, sequence, planId, expectedSequence;
    public int action_kind = ActionKind.LEGACY.id;
    private Object config = 0;

    public Object getConfig() {
        if (configTruncated)
            throw new IllegalStateException(
                    "History contains a display preview; use a rollback plan for restoration");
        return ConfigCodec.decode(config_type, config);
    }
    public String getConfigAsString() {
        if (config_type == 5 && configBytes > 0)
            return configBytes + "b";
        if (configTruncated && config_type == 6) {
            String preview = new String((byte[]) config, java.nio.charset.StandardCharsets.UTF_8);
            int end =
                    preview.offsetByCodePoints(0, Math.min(256, preview.codePointCount(0, preview.length())));
            return preview.substring(0, end) + "…";
        }
        return ConfigCodec.display(config_type, config) + (configTruncated ? "…" : "");
    }
    public ActionKind kind() {
        return ActionKind.fromId(action_kind);
    }
    public Tile tile() {
        return Vars.world.tile(x, y);
    }
    public PlayerInfo playerInfo() {
        return Vars.netServer.admins.getInfoOptional(uuid);
    }
    public Team team() {
        if (team < 0 || team >= Team.all.length)
            throw new IllegalArgumentException("Invalid team");
        return Team.all[team];
    }
    public Block block() {
        var blocks = Vars.content.blocks();
        return block >= 0 && block < blocks.size ? blocks.get(block) : null;
    }
    public String blockEmoji() {
        try {
            if (block == 0)
                return destroy ? "[#ff8a8a]X[]" : "[#98ff98][]";
            Block value = block();
            if (value == null)
                return Integer.toString(block);
            return (destroy ? "[#ff8a8a]" : "[]")
                    + Reflect.get(Iconc.class,
                            Strings.kebabToCamel(value.getContentType().name() + "-" + value.name))
                    + "[]";
        } catch (RuntimeException failure) {
            return Integer.toString(block);
        }
    }
    public String rotationAsString() {
        if (block() == null || !block().rotate)
            return "";
        return switch (rotation) {
            case 0 -> "";
            case 1 -> "";
            case 2 -> "";
            case 3 -> "";
            default -> "?";
        };
    }
    public static String formatDuration(long seconds) {
        if (seconds < 0)
            throw new IllegalArgumentException("Negative elapsed time");
        return String.format(
                java.util.Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }
    public String timeAsString() {
        return (valid ? "[#b0b5c8]" : "[#6e7080]") + formatDuration(time) + "[]";
    }
}
