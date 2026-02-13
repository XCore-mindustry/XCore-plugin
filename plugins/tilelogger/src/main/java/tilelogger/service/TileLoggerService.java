package tilelogger.service;

import arc.struct.ObjectMap;
import arc.util.Log;
import arc.util.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.ai.types.LogicAI;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.io.JsonIO;
import mindustry.net.Administration.PlayerInfo;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.ConstructBlock.ConstructBuild;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.localization.BundleService;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.FindService;
import org.xcore.plugin.session.SessionService;
import tilelogger.*;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class TileLoggerService {
    public static final List<Block> rollbackBlacklist = Arrays.asList(
            Blocks.coreShard, Blocks.coreFoundation, Blocks.coreNucleus,
            Blocks.coreCitadel, Blocks.coreBastion, Blocks.coreAcropolis
    );

    private final PlayerDataRepository playerDataRepository;
    private final SessionService playerSessionService;
    private final FindService findService;
    private final BundleService bundle;

    private final ObjectMap<String, PlayerConfig> playerConfigs = new ObjectMap<>();

    @Inject
    public TileLoggerService(PlayerDataRepository playerDataRepository,
                             SessionService playerSessionService,
                             FindService findService, BundleService bundle) {
        this.playerDataRepository = playerDataRepository;
        this.playerSessionService = playerSessionService;
        this.findService = findService;
        this.bundle = bundle;
    }

    public PlayerConfig getPlayerConfig(Player player) {
        if (player == null) return new PlayerConfig();
        return playerConfigs.get(player.uuid(), PlayerConfig::new);
    }

    public void removePlayerConfig(String uuid) {
        playerConfigs.remove(uuid);
    }

    public @Nullable PlayerInfo unitToPlayerInfo(@Nullable Unit unit) {
        if (unit == null) return null;
        if (unit.controller() instanceof LogicAI logicAi) {
            TileState[] history = TileLogger.getHistory((short) logicAi.controller.tile.x, (short) logicAi.controller.tile.y, (short) logicAi.controller.tile.x, (short) logicAi.controller.tile.y, "", -1, 0, 1);
            return history.length > 0 ? history[0].playerInfo() : null;
        }
        return unit.isPlayer() ? unit.getPlayer().getInfo() : null;
    }

    public void logBuild(Tile tile, @Nullable PlayerInfo playerInfo) {
        if (tile.build == null) return;
        short blockId = tile.blockID();
        short rotation = (short) tile.build.rotation;
        Object config = tile.build.config();

        if (tile.build instanceof ConstructBuild construct) {
            if (construct.progress == 0 && construct.prevBuild != null) {
                for (Building building : construct.prevBuild)
                    logDestroy(building.tile, playerInfo);
                return;
            }
            blockId = construct.current.id;
            rotation = (short) construct.rotation;
            config = construct.lastConfig;
        }

        String uuid = playerInfo == null ? "" : playerInfo.id;
        ConfigWrapper wrapper = new ConfigWrapper(config);

        if (wrapper.config instanceof Integer integer) {
            TileLogger.onAction(tile.x, tile.y, uuid, (short) tile.team().id, blockId, rotation, wrapper.config_type, integer);
        } else if (wrapper.config instanceof byte[] bytes) {
            TileLogger.onAction2(tile.x, tile.y, uuid, (short) tile.team().id, blockId, rotation, wrapper.config_type, bytes);
        }
    }

    public void logDestroy(Tile tile, @Nullable PlayerInfo playerInfo) {
        TileLogger.onAction(tile.x, tile.y, playerInfo == null ? "" : playerInfo.id, (short) tile.team().id, (short) 0, (short) 0, (short) 0, 0);
    }

    public void resetHistory(String path, boolean write) {
        TileLogger.reset(path, write);

        for (Tile tile : Vars.world.tiles) {
            if (tile.build != null && tile == tile.build.tile) {
                logBuild(tile, null);
            }
        }
    }


    public void showHistory(@Nullable Player caller, PlayerDescriptor target, long size) {
        var locale = caller == null ? bundle.getDefaultLocale() : bundle.locale(caller);

        StringBuilder str = new StringBuilder();
        str.append(bundle.format(locale, "tilelogger-history-player", args(
                "player", target.toString(),
                "time", getCurrentTimeFormatted()
        )));

        for (TileState state : TileLogger.getHistory((short)0, (short)0, (short)-1, (short)-1,
                target.uuid, -1, 0, size)) {
            appendStateLine(str, state);
        }

        if (caller == null) Log.info(str.toString());
        else caller.sendMessage(str.toString());
    }

    public void showHistory(@Nullable Player caller, short x, short y, long size) {
        var locale = caller == null ? bundle.getDefaultLocale() : bundle.locale(caller);

        StringBuilder str = new StringBuilder();
        str.append(bundle.format(locale, "tilelogger-history-tile", args(
                "x", x, "y", y, "time", getCurrentTimeFormatted()
        )));

        for (TileState state : TileLogger.getHistory(x, y, x, y, "", -1, 0, size)) {
            appendStateLine(str, state);
        }

        if (caller == null) Log.info(str.toString());
        else caller.sendMessage(str.toString());
    }

    public void rollback(@Nullable Player caller, PlayerDescriptor target, int teams, int time, Rect rect) {
        TileState[] tiles = TileLogger.rollback(rect.x1, rect.y1, rect.x2, rect.y2, target.uuid, teams, time, 0);
        int count = 0;

        for (TileState state : tiles) {
            if (rollbackBlacklist.contains(state.tile().block())) continue;
            Call.setTile(state.tile(), Vars.content.block(state.destroy ? 0 : state.block), state.team(), state.rotation);
            if (state.tile().build != null) state.tile().build.configure(state.getConfig());
            count++;
        }

        int finalCount = count;
        Groups.player.each(p -> {
            String callerName = caller == null
                    ? bundle.format(bundle.locale(p), "tilelogger-server", args())
                    : caller.coloredName();

            p.sendMessage(bundle.format(bundle.locale(p), "tilelogger-rollback-broadcast", args(
                    "caller", callerName,
                    "target", target.toString(),
                    "count", finalCount
            )));
        });

        Log.info(bundle.format(bundle.getDefaultLocale(), "tilelogger-rollback-broadcast", args(
                "caller", caller == null ? bundle.format(bundle.getDefaultLocale(), "tilelogger-server", args()) : caller.coloredName(),
                "target", target.toString(),
                "count", count
        )));
    }

    public void fill(@Nullable Player caller, @Nullable Team team, Block block, Rect rect) {
        for (short x = rect.x1; x <= rect.x2; x += block.size) {
            for (short y = rect.y1; y <= rect.y2; y += block.size) {
                Call.setTile(Vars.world.tile(x, y), block, team == null ? caller.team() : team, 0);
            }
        }
        if (caller != null) {
            bundle.send(caller, "tilelogger-fill-success", args(
                    "block", block.emoji() + " " + block.name
            ));
        }
    }

    private TileStatePacket[] buildPackets(TileState[] states) {
        return Arrays.stream(states)
                .map(t -> {
                    PlayerDescriptor desc = findPlayerUuid(t.uuid);
                    return new TileStatePacket(
                            t.x, t.y,
                            desc == null ? "@" + t.team() : desc.toString(),
                            t.uuid, t.valid, t.time, t.block, t.destroy,
                            t.rotation, t.config_type, t.getConfigAsString()
                    );
                }).toArray(TileStatePacket[]::new);
    }

    private void sendHistoryPacket(Player caller, String packetName, TileState[] states) {
        TileStatePacket[] packets = buildPackets(states);
        Call.clientPacketUnreliable(caller.con, packetName, JsonIO.write(packets));
    }

    public void sendTileHistory(short x, short y, Player caller) {
        sendHistoryPacket(caller, "tilelogger_history_tile",
                TileLogger.getHistory(x, y, x, y, "", -1, 0, 100));
    }

    public void sendPlayerHistory(PlayerDescriptor target, Player caller) {
        sendHistoryPacket(caller, "tilelogger_history_player",
                TileLogger.getHistory((short)0, (short)0, (short)-1, (short)-1,
                        target.uuid, -1, 0, 100));
    }


    public boolean checkSubnetAccepted(String subnet) {
        return TileLogger.subnetAccepted(subnet);
    }
    public void reloadSubnets() {
        TileLogger.reloadSubnets();
    }

    public String getMemoryUsage(@Nullable Player viewer) {
        var locale = viewer == null ? bundle.getDefaultLocale() : bundle.locale(viewer);

        Runtime runtime = Runtime.getRuntime();
        return bundle.format(locale, "tilelogger-memory", args(
                "jvmUsed", String.format("%.2f", (runtime.totalMemory() - runtime.freeMemory()) / 1e6),
                "jvmMax", String.format("%.2f", runtime.maxMemory() / 1e6),
                "historyUsed", String.format("%.2f", TileLogger.memoryUsage(2) / 1e6),
                "historyCap", String.format("%.2f", TileLogger.memoryUsage(3) / 1e6),
                "playersUsed", String.format("%.2f", TileLogger.memoryUsage(4) / 1e6),
                "playersCap", String.format("%.2f", TileLogger.memoryUsage(5) / 1e6),
                "configsUsed", String.format("%.2f", TileLogger.memoryUsage(6) / 1e6),
                "configsCap", String.format("%.2f", TileLogger.memoryUsage(7) / 1e6)
        ));
    }

    public @Nullable PlayerDescriptor findPlayer(String str) {
        if (str.equals("all")) return new PlayerDescriptor("all", "", -1);

        if (arc.util.Strings.canParseInt(str)) {
            PlayerData data = playerDataRepository.findByPid(Integer.parseInt(str));
            if (data != null) return new PlayerDescriptor(data.nickname, data.uuid, data.pid);
        }

        PlayerData data = playerDataRepository.findByUuid(str);
        if (data != null) return new PlayerDescriptor(data.nickname, data.uuid, data.pid);

        Player player = findService.playerByName(str);
        if (player != null) {
            data = playerSessionService.get(player.uuid()).getData();
            if (data != null) return new PlayerDescriptor(data.nickname, data.uuid, data.pid);
            return new PlayerDescriptor(player.name, player.uuid(), -1);
        }

        return null;
    }

    public @Nullable PlayerDescriptor findPlayerUuid(String uuid) {
        PlayerData data = playerSessionService.getOrLoadFromDb(uuid);
        if (data != null) return new PlayerDescriptor(data.nickname, data.uuid, data.pid);

        var info = Vars.netServer.admins.getInfoOptional(uuid);
        if (info != null) return new PlayerDescriptor(info.lastName, uuid, -1);

        return null;
    }

    public boolean useAdminTools(Player player) {
        PlayerData data = playerSessionService.get(player.uuid()).getData();
        return data != null && data.adminModVersion != null && !player.con.mobile;
    }

    private String getCurrentTimeFormatted() {
        return LocalTime.MIN.plusSeconds(TileLogger.duration()).format(DateTimeFormatter.ISO_LOCAL_TIME);
    }

    private void appendStateLine(StringBuilder str, TileState state) {
        Object rotation = state.rotationAsString();
        PlayerDescriptor desc = findPlayerUuid(state.uuid);

        String validColor = state.valid ? "[#b0b5c8]" : "[#6e7080]";

        str.append("\n    [#6e7080]•[] ")
                .append("[#a4b8ff]").append(state.x).append(",").append(state.y).append("[] ")
                .append(validColor)
                .append(LocalTime.MIN.plusSeconds(state.time).format(DateTimeFormatter.ISO_LOCAL_TIME))
                .append("[] ")
                .append(state.blockEmoji())
                .append(rotation == null ? "" : " " + rotation)
                .append(" ").append("[#ffd37f]").append(state.getConfigAsString()).append("[]");

        if (desc != null) {
            str.append(" [#6e7080](").append(desc.name).append(")[]");
        }
    }
}
