package tilelogger;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import arc.math.geom.Point2;
import mindustry.Vars;
import mindustry.ai.UnitCommand;
import mindustry.gen.Call;
import mindustry.type.*;
import mindustry.world.Block;

public class ConfigWrapper {
    public short config_type;
    public Object config;

    public ConfigWrapper(Object config) {
        switch (config) {
            case null -> set((short) 0, 0);
            case Integer integer -> set((short) 1, integer);
            case Boolean bool -> set((short) 2, bool ? 1 : 0);
            case Item item -> set((short) 3, Vars.content.items().indexOf(item));
            case Liquid liquid -> set((short) 3, Vars.content.items().size + Vars.content.liquids().indexOf(liquid));
            case UnitType unit ->
                    set((short) 3, Vars.content.items().size + Vars.content.liquids().size + Vars.content.units().indexOf(unit));
            case Block block ->
                    set((short) 3, Vars.content.items().size + Vars.content.liquids().size + Vars.content.units().size + Vars.content.blocks().indexOf(block));
            case UnitCommand command ->
                    set((short) 3, Vars.content.items().size + Vars.content.liquids().size + Vars.content.units().size + Vars.content.blocks().size + command.id);
            case Point2 point -> set((short) 4, point.pack());
            case byte[] bytes -> set((short) 5, bytes);
            case String string -> set((short) 6, string.getBytes());
            case Point2[] points -> set((short) 7, pointArrayToBytes(points));
            default -> Call.sendMessage("Unknown config type: " + config.getClass().getName());
        }
    }

    public void set(short config_type_, Object config_) {
        config_type = config_type_;
        config = config_;
    }

    private byte[] pointArrayToBytes(Point2[] points) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(points.length * 4);
        IntBuffer intBuffer = byteBuffer.asIntBuffer();
        for (Point2 point : points)
            intBuffer.put(point.pack());
        return byteBuffer.array();
    }
}
