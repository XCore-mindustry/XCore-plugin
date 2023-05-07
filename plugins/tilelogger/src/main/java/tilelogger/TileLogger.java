package tilelogger;

import arc.Core;
import arc.util.Log;
import arc.util.Nullable;
import arc.util.OS;
import mindustry.Vars;
import mindustry.ai.types.LogicAI;
import mindustry.content.Blocks;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.io.JsonIO;
import mindustry.net.Administration.PlayerInfo;
import mindustry.world.Block;
import mindustry.world.Tile;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

public class TileLogger {
    public static List<Block> rollback_blacklist = Arrays.asList(Blocks.coreShard, Blocks.coreFoundation, Blocks.coreNucleus, Blocks.coreCitadel, Blocks.coreBastion, Blocks.coreAcropolis);

    static {
        String fileName = OS.isWindows ? "TileLogger.dll" : "libTileLogger.so";

        try {
            var file = TileLogger.class.getResource("/" + fileName);
            var temp = Files.createTempFile("tilelogger", OS.isWindows ? ".dll" : ".so");

            Files.copy(file.openStream(), temp);

            System.load(temp.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static @Nullable PlayerInfo unitToPlayerInfo(@Nullable Unit unit) {
        if (unit == null) return null;
        if (unit.controller() instanceof LogicAI logic_ai) {
            TileState[] history = TileLogger.getHistory(logic_ai.controller.tile.x, logic_ai.controller.tile.y, logic_ai.controller.tile.x, logic_ai.controller.tile.y, "", -1, 0, 1);
            return history.length > 0 ? history[0].playerInfo() : null;
        }
        return unit.isPlayer() ? unit.getPlayer().getInfo() : null;
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

    public static void sendTileHistory(@Nullable PlayerInfo player_info, Player caller) {
        Call.clientPacketUnreliable(caller.con, "tilelogger_history_player",
            JsonIO.write(Arrays.stream(getHistory((short)0, (short)0, (short)-1, (short)-1, player_info == null ? "" : player_info.id, -1, 0, 100)).map(t -> {
                var info = Vars.netServer.admins.getInfoOptional(t.uuid);
                return new TileStatePacket(t.x, t.y, info == null ? "@" + t.team() : info.lastName,
                            t.uuid, t.time, t.valid, t.block, t.rotation, t.config_type, t.getConfigAsString());
            }
        ).toArray(TileStatePacket[]::new)));
    }

    public static void sendTileHistory(short x, short y, Player caller) {
        Call.clientPacketUnreliable(caller.con, "tilelogger_history_tile",
            JsonIO.write(Arrays.stream(getHistory(x, y, x, y, "", -1, 0, 100)).map(t -> {
                var info = Vars.netServer.admins.getInfoOptional(t.uuid);
                return new TileStatePacket(t.x, t.y, info == null ? "@" + t.team() : info.lastName,
                            t.uuid, t.time, t.valid, t.block, t.rotation, t.config_type, t.getConfigAsString());
            }
        ).toArray(TileStatePacket[]::new)));
    }

    public static void showHistory(@Nullable Player caller, @Nullable PlayerInfo player_info, long size) {
        String str = String.format("Player %s[white] history. Current time: %s.", player_info == null ? "" : player_info.lastName, LocalTime.MIN.plusSeconds(duration()).format(DateTimeFormatter.ISO_LOCAL_TIME));
        for (TileState state : getHistory((short)0, (short)0, (short)-1, (short)-1, player_info == null ? "" : player_info.id, -1, 0, size)) {
            Object rotation = state.rotationAsString();
            str += "[white]\n    " + state.x + "," + state.y + " " + (state.valid ? "[white] " : "[gray] ")
                    + LocalTime.MIN.plusSeconds(state.time).format(DateTimeFormatter.ISO_LOCAL_TIME) + " " + state.blockEmoji() + (rotation == null ? "" : " " + rotation) + " " + state.getConfigAsString();
        }
        SendMessage(caller, str);
    }

    public static void showHistory(@Nullable Player caller, short x, short y, long size) {
        String str = String.format("Tile (%d,%d) history. Current time: %s.", x, y, LocalTime.MIN.plusSeconds(duration()).format(DateTimeFormatter.ISO_LOCAL_TIME));
        for (TileState state : getHistory(x, y, x, y, "", -1, 0, size)) {
            Object rotation = state.rotationAsString();
            str += "[white]\n    " + (state.playerInfo() == null ? "@" + state.team() : state.playerInfo().lastName) + (state.valid ? "[white] " : "[gray] ")
                    + LocalTime.MIN.plusSeconds(state.time).format(DateTimeFormatter.ISO_LOCAL_TIME) + " " + state.blockEmoji() + (rotation == null ? "" : " " + rotation) + " " + state.getConfigAsString();
        }
        SendMessage(caller, str);
    }

    public enum RollbackFlags {
        kPreview, 
    }

    public static void rollback(@Nullable Player caller, @Nullable PlayerInfo target, int teams, int time, short x1, short y1, short x2, short y2) {
        TileState[] tiles = rollback(x1, y1, x2, y2, target == null ? "" : target.id, teams, time, 0);
        for (TileState state : tiles) {
            if (rollback_blacklist.contains(state.tile().block())) continue;
            Call.setTile(state.tile(), Vars.content.block(state.block), state.team(), state.rotation);
            if (state.tile().build != null)
                state.tile().build.configure(state.getConfig());
        }
        Broadcast(String.format((caller == null ? "Server" : caller.coloredName()) + "[white] initiated rollback against player %s[white], time %d, rect %d %d %d %d, tiles %d",
                target != null ? target.lastName : "@all", time, x1, y1, x2, y2, tiles.length));

        if (caller != null) {
            Call.clientPacketUnreliable(caller.con, "tilelogger_rollback_preview",
                JsonIO.write(Arrays.stream(tiles).map(t -> {
                    var info = Vars.netServer.admins.getInfoOptional(t.uuid);
                    return new TileStatePacket(t.x, t.y, info == null ? "" : info.lastName, t.uuid, t.time, t.valid, t.block, t.rotation, t.config_type, t.getConfigAsString());
                }
            ).toArray(TileStatePacket[]::new)));
        }
    }

    public static void reset() {
        reset((short) Vars.world.width(), (short) Vars.world.height());
        for (Tile tile : Vars.world.tiles) {
            if (tile.build != null && tile == tile.build.tile)
                build(tile, null);
        }
    }

    public static void showInfo(@Nullable Player player) {
        Runtime runtime = Runtime.getRuntime();
        String str = String.format("TileLogger by [white] (Горыныч#3545), thanks to kowkonya#8536.\nBuild: %s", getBuildString());
        str += String.format("\nMemory usage in MB: used | allocated | maximum");
        str += String.format("\n    JVM: %.3f | %.3f | %.3f", (runtime.totalMemory() - runtime.freeMemory()) * 1e-6, runtime.totalMemory() * 1e-6, runtime.maxMemory() * 1e-6);
        str += String.format("\n    Native:");
        str += String.format("\n        Grid: %.3f | %.3f", memoryUsage(0) * 1e-6, memoryUsage(1) * 1e-6);
        str += String.format("\n        Tiles: %.3f | %.3f", memoryUsage(2) * 1e-6, memoryUsage(3) * 1e-6);
        str += String.format("\n        Players: %.3f | %.3f", memoryUsage(4) * 1e-6, memoryUsage(5) * 1e-6);
        str += String.format("\n        Configs: %.3f | %.3f", memoryUsage(6) * 1e-6, memoryUsage(7) * 1e-6);
        SendMessage(player, str);
    }

    private static void SendMessage(@Nullable Player player, String msg) {
        if (player == null)
            Log.info(msg);
        else
            player.sendMessage(msg);
    }

    private static void Broadcast(String msg) {
        Call.sendMessage(msg);
        Log.info(msg);
    }

    private static native long reset(short width, short height);

    private static native short duration();

    private static native void onAction(short x, short y, String uuid, short team, short block, short rotation, short config_type, int config);

    private static native void onAction2(short x, short y, String uuid, short team, short block, short rotation, short config_type, byte[] config);

    private static native TileState[] getHistory(short x1, short y1, short x2, short y2, String uuid, int teams, int time, long size);

    private static native TileState[] rollback(short x1, short y1, short x2, short y2, String uuid, int teams, int time, int flags);

    private static native long memoryUsage(long id);

    private static native String getBuildString();
    
}