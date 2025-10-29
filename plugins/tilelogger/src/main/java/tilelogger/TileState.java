package tilelogger;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import arc.math.geom.Point2;
import arc.util.Reflect;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.ai.UnitCommand;
import mindustry.game.Team;
import mindustry.gen.Iconc;
import mindustry.net.Administration.PlayerInfo;
import mindustry.world.Block;
import mindustry.world.Tile;

public class TileState {
    public short x;
    public short y;

    public String uuid;
    public byte team;
    public boolean valid;
    public short time;
    public short block;
    public boolean destroy;
    public short rotation;
    public short config_type;
    private Object config;

    public Object getConfig() {
        return switch (config_type) {
            case 0 -> null;
            case 1 -> config;
            case 2 -> (int) config > 0;
            case 3 -> (int) config < Vars.content.items().size
                ? Vars.content.items().get((int) config)
                : (int)config < Vars.content.items().size + Vars.content.liquids().size
                    ? Vars.content.liquids().get((int) config - Vars.content.items().size)
                    : (int)config < Vars.content.items().size + Vars.content.liquids().size + Vars.content.units().size
                        ? Vars.content.units().get((int) config - Vars.content.items().size - Vars.content.liquids().size)
                        : (int)config < Vars.content.items().size + Vars.content.liquids().size + Vars.content.units().size + Vars.content.blocks().size
                            ? Vars.content.blocks().get((int) config - Vars.content.items().size - Vars.content.liquids().size - Vars.content.units().size)
                            : (int)config < Vars.content.items().size + Vars.content.liquids().size + Vars.content.units().size + Vars.content.blocks().size + Vars.content.unitCommands().size
                                ? Vars.content.unitCommands().get((int) config - Vars.content.items().size - Vars.content.liquids().size - Vars.content.units().size - Vars.content.blocks().size)
                                : null;
            case 4 -> Point2.unpack((int) config);
            case 5 -> config;
            case 6 -> new String((byte[]) config, StandardCharsets.UTF_8);
            case 7 -> bytesToPointArray((byte[]) config);
            default -> null;
        };
    }

    private Point2[] bytesToPointArray(byte[] bytes) {
        IntBuffer intBuffer = ByteBuffer.wrap(bytes).asIntBuffer();
        int[] array = new int[intBuffer.remaining()];
        Point2[] points = new Point2[intBuffer.remaining()];
        intBuffer.get(array);
        for (int i = 0; i < points.length; i++) {
            points[i] = Point2.unpack(array[i]);
        }
        return points;
    }

    public String getConfigAsString() {
        Object obj = getConfig();
        if (obj instanceof byte[] bytes)
            return bytes.length + "b";
        else if (obj instanceof Point2[] points) {
            String str = "";
            for (Point2 p : points)
                str += p.toString() + " ";
            return str;
        }
        return obj == null ? "" : obj + "";
    }

    public Tile tile() {
        return Vars.world.tile(x, y);
    }

    public PlayerInfo playerInfo() {
        return Vars.netServer.admins.getInfoOptional(uuid);
    }

    public Team team() {
        return Team.all[team];
    }

    public Block block() {
        return Vars.content.block(block);
    }

    public String blockEmoji() {
        try {
            if (block == 0) return destroy ? "[red]X[]" : "[green][]";
            if (block >= 3 && block <= 11) return "[green]" + (block - 2) + "[]";
            return (destroy ? "[red]" : "[]") + Reflect.get(Iconc.class, Strings.kebabToCamel(block().getContentType().name() + "-" + block().name)) + "[]";
        } catch (Exception e) {
            return "[scarlet]" + block + "[]";
        }
    }

    public Object rotationAsString() {
        if (!block().rotate)
            return null;
        return switch (rotation) {
            case 0 -> "";
            case 1 -> "";
            case 2 -> "";
            case 3 -> "";
            default -> "?";
        };
    }

    public String timeAsString() {
        return (valid ? "[white]" : "[gray]") + LocalTime.MIN.plusSeconds(time).format(DateTimeFormatter.ISO_LOCAL_TIME) + "[]";
    }
}