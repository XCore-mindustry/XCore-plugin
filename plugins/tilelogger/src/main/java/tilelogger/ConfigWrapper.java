package tilelogger;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import arc.math.geom.Point2;
import mindustry.Vars;
import mindustry.gen.Call;
import mindustry.type.*;
import mindustry.world.Block;

public class ConfigWrapper {
    public short config_type;
    public Object config;

    ConfigWrapper(Object config) {
        if (config == null)
            set((short) 0, 0);
        else if (config instanceof Integer integer)
            set((short) 1, integer);
        else if (config instanceof Boolean bool)
            set((short) 2, bool ? 1 : 0);
        else if (config instanceof Item item)
            set((short) 3, Vars.content.items().indexOf(item));
        else if (config instanceof Liquid liquid)
            set((short) 3, Vars.content.items().size + Vars.content.liquids().indexOf(liquid));
        else if (config instanceof UnitType unit)
            set((short) 3, Vars.content.items().size + Vars.content.liquids().size + Vars.content.units().indexOf(unit));
        else if (config instanceof Block block)
            set((short) 3, Vars.content.items().size + Vars.content.liquids().size + Vars.content.units().size + Vars.content.blocks().indexOf(block));
        else if (config instanceof Point2 point)
            set((short) 4, point.pack());
        else if (config instanceof byte[] bytes)
            set((short) 5, bytes);
        else if (config instanceof String string)
            set((short) 6, string.getBytes());
        else if (config instanceof Point2[] points)
            set((short) 7, pointArrayToBytes(points));
        else
            Call.sendMessage("Unknown config type: " + config.getClass().getName());
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
