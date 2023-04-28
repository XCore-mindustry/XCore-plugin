package main.java;

import arc.math.geom.Point2;
import arc.util.Nullable;
import arc.util.Reflect;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.ai.types.LogicAI;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Iconc;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.net.Administration.PlayerInfo;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.Tile;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import static mindustry.Vars.netServer;

public class TileLogger {
    public static String version = "1.0";

    public static List<Block> rollback_blacklist = Arrays.asList(Blocks.coreShard, Blocks.coreFoundation, Blocks.coreNucleus, Blocks.coreCitadel, Blocks.coreBastion, Blocks.coreAcropolis);

    static {
        System.loadLibrary("TileLogger");
    }

    public static @Nullable PlayerInfo unitToPlayerInfo(@Nullable Unit unit) {
        if (unit == null) return null;
        if (unit.controller() instanceof LogicAI logic_ai) {
            TileState[] history = TileLogger.getHistory(logic_ai.controller.tile.x, logic_ai.controller.tile.y, 1);
            return history.length > 0 ? history[0].playerInfo() : null;
        }
        return unit.getPlayer().getInfo();
    }

    public static void build(Tile tile, @Nullable PlayerInfo player_info) {
        if (tile.build == null) return; // rollback recursion
        String uuid = player_info == null ? "" : player_info.id;
        ConfigWrapper wrapper = new ConfigWrapper(tile.build.config());
        if (wrapper.config instanceof Integer integer)
            onAction(tile.x, tile.y, uuid, (short) tile.team().id, tile.blockID(), (short) tile.build.rotation, wrapper.config_type, integer);
        else if (wrapper.config instanceof byte[] bytes)
            onAction2(tile.x, tile.y, uuid, (short) tile.team().id, tile.blockID(), (short) tile.build.rotation, wrapper.config_type, bytes);
    }

    public static void destroy(Tile tile, @Nullable PlayerInfo player_info) {
        onAction(tile.x, tile.y, player_info == null ? "" : player_info.id, (short) tile.team().id, (short) 0, (short) 0, (short) 0, 0);
    }

    public static TileStatePacket[] getTileStatePacket(Tile tile, long size) {
        short x = (short) tile.centerX();
        short y = (short) tile.centerY();

        return Arrays.stream(getHistory(x, y, size)).map(t ->
                new TileStatePacket(x, y, netServer.admins.getInfoOptional(t.uuid).lastName,
                        t.uuid, t.time, t.block, t.rotation, t.config_type, t.getConfigAsString())).toArray(TileStatePacket[]::new);
    }

    public static void showHistory(short x, short y, long size, Player player) {
        Tile tile = Vars.world.tile(x, y);

        if (tile == null) return;
        x = (short) tile.centerX();
        y = (short) tile.centerY();
        String str = String.format("Tile (%d,%d) history: player, %s, block, rotation, config", x, y, LocalTime.MIN.plusSeconds(duration()).format(DateTimeFormatter.ISO_LOCAL_TIME));
        for (TileState state : getHistory(x, y, size)) {
            Object rotation = state.rotationAsString();
            str += "\n    " + (state.playerInfo() == null ? "@" + state.team() : state.playerInfo().lastName) + "[white] "
                    + LocalTime.MIN.plusSeconds(state.time).format(DateTimeFormatter.ISO_LOCAL_TIME) + " " + state.blockEmoji() + (rotation == null ? "" : " " + rotation) + " " + state.getConfigAsString();
        }
        player.sendMessage(str);
    }

    public static void rollback(@Nullable Player initiator, @Nullable PlayerInfo target, int teams, int time, short x1, short y1, short x2, short y2) {
        TileState[] tiles = rollback(x1, y1, x2, y2, target == null ? "" : target.id, teams, time, true);
        for (TileState state : tiles) {
            if (rollback_blacklist.contains(state.tile().block())) continue;
            Call.setTile(state.tile(), Vars.content.block(state.block), state.team(), state.rotation);
            if (state.tile().build != null)
                state.tile().build.configure(state.getConfig());
        }
        Call.sendMessage(String.format((initiator == null ? "Server" : initiator.coloredName()) + "[white] initiated rollback against player %s, time %d, rect %d %d %d %d, tiles %d",
                target != null ? target.lastName : "@all", time, x1, y1, x2, y2, tiles.length));
    }

    public static void reset() {
        reset((short) Vars.world.width(), (short) Vars.world.height());
        for (Tile tile : Vars.world.tiles) {
            if (tile.build != null && tile == tile.build.tile)
                build(tile, null);
        }
    }

    public static void showInfo(Player player) {
        Runtime runtime = Runtime.getRuntime();
        String str = String.format("TileLogger %s by [white] (Горыныч#3545), thanks to kowkonya#8536.", version);
        str += String.format("\nMemory usage in MB: used | allocated | maximum");
        str += String.format("\n    JVM: %.3f | %.3f | %.3f", (runtime.totalMemory() - runtime.freeMemory()) * 1e-6, runtime.totalMemory() * 1e-6, runtime.maxMemory() * 1e-6);
        str += String.format("\n    Native:");
        str += String.format("\n        Grid: %.3f | %.3f", memoryUsage(0) * 1e-6, memoryUsage(1) * 1e-6);
        str += String.format("\n        Tiles: %.3f | %.3f", memoryUsage(2) * 1e-6, memoryUsage(3) * 1e-6);
        str += String.format("\n        Players: %.3f | %.3f", memoryUsage(4) * 1e-6, memoryUsage(5) * 1e-6);
        str += String.format("\n        Configs: %.3f | %.3f", memoryUsage(6) * 1e-6, memoryUsage(7) * 1e-6);
        player.sendMessage(str);
    }

    private static native long reset(short width, short height);

    private static native short duration();

    private static native void onAction(short x, short y, String uuid, short team, short block, short rotation, short config_type, int config);

    private static native void onAction2(short x, short y, String uuid, short team, short block, short rotation, short config_type, byte[] config);

    private static native TileState[] getHistory(short x, short y, long size);

    private static native TileState[] rollback(short x1, short y1, short x2, short y2, String uuid, int teams, int time, boolean erase);

    private static native long memoryUsage(long id);

    private static class TileStatePacket {
        public short x;
        public short y;
        public String name;
        public String uuid;
        public short time;
        public short block;
        public short rotation;
        public short config_type;
        public String config;

        public TileStatePacket(short x, short y, String name, String uuid, short time, short block, short rotation, short config_type, String config) {
            this.x = x;
            this.y = y;
            this.name = name;
            this.uuid = uuid;
            this.time = time;
            this.block = block;
            this.rotation = rotation;
            this.config_type = config_type;
            this.config = config;
        }
    }

    private static class TileState {
        public short x; // value is undefined if returned by getHistory()
        public short y; // value is undefined if returned by getHistory()

        public String uuid;
        public byte team;
        public short time;
        public short block;
        public short rotation;
        public short config_type;
        private Object config;

        public Object getConfig() {
            return switch (config_type) {
                case 0 -> null;
                case 1 -> config;
                case 2 -> (int) config > 0;
                case 3 -> Vars.content.items().get((int) config);
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
            return netServer.admins.getInfoOptional(uuid);
        }

        public Team team() {
            return Team.all[team];
        }

        public Block block() {
            return Vars.content.block(block);
        }

        public char blockEmoji() {
            try {
                return Reflect.get(Iconc.class, Strings.kebabToCamel(block().getContentType().name() + "-" + Vars.content.block(block).name));
            } catch (Exception e) {
                return 'X';
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
    }

    private static class ConfigWrapper {
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
}