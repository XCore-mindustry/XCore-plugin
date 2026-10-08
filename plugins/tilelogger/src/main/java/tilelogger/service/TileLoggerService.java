package tilelogger.service;

import arc.struct.ObjectMap;
import arc.util.Log;
import arc.util.Nullable;
import com.ospx.flubundle.Args;
import com.ospx.flubundle.Bundle;
import com.ospx.flubundle.mindustry.Messenger;
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
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.FindService;
import org.xcore.plugin.session.SessionService;
import tilelogger.*;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;


@Singleton
public class TileLoggerService {
    public static final List<Block> rollbackBlacklist = Arrays.asList(
            Blocks.coreShard, Blocks.coreFoundation, Blocks.coreNucleus,
            Blocks.coreCitadel, Blocks.coreBastion, Blocks.coreAcropolis
    );

    private final PlayerDataRepository playerDataRepository;
    private final SessionService playerSessionService;
    private final FindService findService;
    private final Bundle bundle;
    private final Messenger messenger;
    private final Async async;

    private final ObjectMap<String, PlayerConfig> playerConfigs = new ObjectMap<>();
    private final java.util.concurrent.ConcurrentMap<String, PlayerDescriptor> descriptorCache = new java.util.concurrent.ConcurrentHashMap<>();

    @Inject
    public TileLoggerService(PlayerDataRepository playerDataRepository,
                             SessionService playerSessionService,
                             FindService findService,
                             Bundle bundle,
                             Async async) {
        this.playerDataRepository = playerDataRepository;
        this.playerSessionService = playerSessionService;
        this.findService = findService;
        this.bundle = bundle;
        this.messenger = Messenger.of(bundle);
        this.async = async;
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
        TileState[] states = TileLogger.getHistory((short)0, (short)0, (short)-1, (short)-1,
                target.uuid, -1, 0, size);

        preloadAndRenderHistory(caller, states, () -> {
            var locale = resolveLocale(caller);
            StringBuilder str = new StringBuilder();
            str.append(bundle.format(locale, "tilelogger-history-player", Args.of(
                    "player", target.toString(),
                    "time", getCurrentTimeFormatted()
            )));

            for (TileState state : states) {
                appendStateLine(str, state);
            }

            if (caller == null) Log.info(str.toString());
            else caller.sendMessage(str.toString());
        });
    }

    public void showHistory(@Nullable Player caller, short x, short y, long size) {
        TileState[] states = TileLogger.getHistory(x, y, x, y, "", -1, 0, size);

        preloadAndRenderHistory(caller, states, () -> {
            var locale = resolveLocale(caller);
            StringBuilder str = new StringBuilder();
            str.append(bundle.format(locale, "tilelogger-history-tile", Args.of(
                    "x", x, "y", y, "time", getCurrentTimeFormatted()
            )));

            for (TileState state : states) {
                appendStateLine(str, state);
            }

            if (caller == null) Log.info(str.toString());
            else caller.sendMessage(str.toString());
        });
    }

    private CompletableFuture<Void> preloadDescriptorsAsync(TileState[] states) {
        if (states == null || states.length == 0) {
            return CompletableFuture.completedFuture(null);
        }
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (TileState state : states) {
            if (state.uuid != null && !state.uuid.isBlank() && findPlayerUuidFast(state.uuid) == null) {
                futures.add(findPlayerUuidAsync(state.uuid));
            }
        }
        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private void preloadAndRenderHistory(@Nullable Player caller, TileState[] states, Runnable renderAction) {
        CompletableFuture<Void> future = preloadDescriptorsAsync(states);
        if (future.isDone()) {
            renderAction.run();
            return;
        }

        if (caller != null) {
            async.onMainForPlayer(caller, future, (p, ignored) -> renderAction.run());
        } else {
            async.onMain(future, (ignored, err) -> {
                if (err == null) renderAction.run();
            });
        }
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
            var locale = resolveLocale(p);
            String callerName = caller == null
                    ? bundle.format(locale, "tilelogger-server")
                    : caller.coloredName();

            p.sendMessage(bundle.format(locale, "tilelogger-rollback-broadcast", Args.of(
                    "caller", callerName,
                    "target", target.toString(),
                    "count", finalCount
            )));
        });

        Log.info(bundle.format(bundle.getDefaultLocale(), "tilelogger-rollback-broadcast", Args.of(
                "caller", caller == null ? bundle.format(bundle.getDefaultLocale(), "tilelogger-server") : caller.coloredName(),
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
            messenger.to(caller).send("tilelogger-fill-success", Args.of(
                    "emoji", block.emoji(),
                    "block", block
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
        TileState[] states = TileLogger.getHistory(x, y, x, y, "", -1, 0, 100);
        preloadAndRenderHistory(caller, states, () -> {
            sendHistoryPacket(caller, "tilelogger_history_tile", states);
        });
    }

    public void sendPlayerHistory(PlayerDescriptor target, Player caller) {
        TileState[] states = TileLogger.getHistory((short)0, (short)0, (short)-1, (short)-1,
                target.uuid, -1, 0, 100);
        preloadAndRenderHistory(caller, states, () -> {
            sendHistoryPacket(caller, "tilelogger_history_player", states);
        });
    }

    public String getMemoryUsage(@Nullable Player viewer) {
        var locale = resolveLocale(viewer);

        Runtime runtime = Runtime.getRuntime();
        return bundle.format(locale, "tilelogger-memory", Bundle.args(
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

    public @Nullable PlayerDescriptor findPlayerFast(String str) {
        if (str == null || str.isBlank()) return null;
        if (str.equals("all")) return new PlayerDescriptor("all", "", -1);

        PlayerDescriptor cached = descriptorCache.get(str);
        if (cached != null) return cached;

        Player player = findService.playerByName(str);
        if (player != null) {
            var session = playerSessionService.get(player.uuid());
            PlayerData data = session != null ? session.getData() : null;
            if (data != null) {
                PlayerDescriptor desc = new PlayerDescriptor(data.nickname, data.uuid, data.pid);
                descriptorCache.put(data.uuid, desc);
                return desc;
            }
            return new PlayerDescriptor(player.name, player.uuid(), -1);
        }

        var onlineByUuid = playerSessionService.get(str);
        if (onlineByUuid != null && onlineByUuid.getData() != null) {
            PlayerDescriptor desc = new PlayerDescriptor(onlineByUuid.getData().nickname, onlineByUuid.getData().uuid, onlineByUuid.getData().pid);
            descriptorCache.put(onlineByUuid.getData().uuid, desc);
            return desc;
        }

        var info = Vars.netServer.admins.getInfoOptional(str);
        if (info != null) {
            PlayerDescriptor desc = new PlayerDescriptor(info.lastName, str, -1);
            descriptorCache.put(str, desc);
            return desc;
        }

        return null;
    }

    public @Nullable PlayerDescriptor findPlayerUuidFast(String uuid) {
        if (uuid == null || uuid.isBlank()) return null;

        PlayerDescriptor cached = descriptorCache.get(uuid);
        if (cached != null) return cached;

        var session = playerSessionService.get(uuid);
        if (session != null && session.getData() != null) {
            PlayerDescriptor desc = new PlayerDescriptor(session.getData().nickname, session.getData().uuid, session.getData().pid);
            descriptorCache.put(uuid, desc);
            return desc;
        }

        var info = Vars.netServer.admins.getInfoOptional(uuid);
        if (info != null) {
            PlayerDescriptor desc = new PlayerDescriptor(info.lastName, uuid, -1);
            descriptorCache.put(uuid, desc);
            return desc;
        }

        return null;
    }

    public CompletableFuture<PlayerDescriptor> findPlayerUuidAsync(String uuid) {
        if (uuid == null || uuid.isBlank()) return CompletableFuture.completedFuture(null);
        PlayerDescriptor fast = findPlayerUuidFast(uuid);
        if (fast != null) return CompletableFuture.completedFuture(fast);

        return playerDataRepository.findByUuidAsync(uuid)
                .toCompletableFuture()
                .thenApply(data -> {
                    if (data != null) {
                        PlayerDescriptor desc = new PlayerDescriptor(data.nickname, data.uuid, data.pid);
                        descriptorCache.put(uuid, desc);
                        return desc;
                    }
                    return null;
                });
    }

    public CompletableFuture<PlayerDescriptor> findPlayerAsync(String str) {
        if (str == null || str.isBlank()) return CompletableFuture.completedFuture(null);
        PlayerDescriptor fast = findPlayerFast(str);
        if (fast != null) return CompletableFuture.completedFuture(fast);

        if (arc.util.Strings.canParseInt(str)) {
            int pid = Integer.parseInt(str);
            return playerDataRepository.findByPidAsync(pid)
                    .toCompletableFuture()
                    .thenCompose(data -> {
                        if (data != null) {
                            PlayerDescriptor desc = new PlayerDescriptor(data.nickname, data.uuid, data.pid);
                            descriptorCache.put(data.uuid, desc);
                            return CompletableFuture.completedFuture(desc);
                        }
                        return playerDataRepository.findByUuidAsync(str)
                                .toCompletableFuture()
                                .thenApply(d -> {
                                    if (d != null) {
                                        PlayerDescriptor desc = new PlayerDescriptor(d.nickname, d.uuid, d.pid);
                                        descriptorCache.put(d.uuid, desc);
                                        return desc;
                                    }
                                    return null;
                                });
                    });
        }

        return playerDataRepository.findByUuidAsync(str)
                .toCompletableFuture()
                .thenApply(data -> {
                    if (data != null) {
                        PlayerDescriptor desc = new PlayerDescriptor(data.nickname, data.uuid, data.pid);
                        descriptorCache.put(data.uuid, desc);
                        return desc;
                    }
                    return null;
                });
    }

    public void executeRollback(XCoreSender sender, String targetStr, Duration duration, boolean useSelection) {
        int timeSeconds = (int) duration.toSeconds();
        Rect rect;
        if (useSelection && sender.isPlayer()) {
            rect = getPlayerConfig(sender.player()).rect;
        } else {
            rect = new Rect((short)0, (short)0, (short)(Vars.world.width()-1), (short)(Vars.world.height()-1));
        }

        if (targetStr.equalsIgnoreCase("self") && sender.isPlayer()) {
            PlayerDescriptor target = findPlayerUuidFast(sender.player().uuid());
            if (target == null) {
                target = new PlayerDescriptor(sender.player().plainName(), sender.player().uuid(), -1);
            }
            rollback(sender.player(), target, -1, timeSeconds, rect);
            return;
        }

        PlayerDescriptor fastTarget = findPlayerFast(targetStr);
        if (fastTarget != null) {
            rollback(sender.isPlayer() ? sender.player() : null, fastTarget, -1, timeSeconds, rect);
            return;
        }

        var future = findPlayerAsync(targetStr);
        if (sender.isPlayer()) {
            async.onMainForPlayer(sender.player(), future, (player, target) -> {
                if (target == null) {
                    sender.send("error-player-not-found");
                    return;
                }
                rollback(player, target, -1, timeSeconds, rect);
            });
        } else {
            async.onMain(future, (target, err) -> {
                if (target == null || err != null) {
                    sender.send("error-player-not-found");
                    return;
                }
                rollback(null, target, -1, timeSeconds, rect);
            });
        }
    }

    public void executeHistoryPlayer(XCoreSender sender, String targetStr, long size) {
        if (size <= 0) return;

        PlayerDescriptor fastTarget = findPlayerFast(targetStr);
        if (fastTarget != null) {
            showHistory(sender.player(), fastTarget, size);
            if (sender.isPlayer() && useAdminTools(sender.player())) {
                sendPlayerHistory(fastTarget, sender.player());
            }
            return;
        }

        var future = findPlayerAsync(targetStr);
        if (sender.isPlayer()) {
            async.onMainForPlayer(sender.player(), future, (player, target) -> {
                if (target == null) {
                    sender.send("error-player-not-found");
                    return;
                }
                showHistory(player, target, size);
                if (useAdminTools(player)) {
                    sendPlayerHistory(target, player);
                }
            });
        } else {
            async.onMain(future, (target, err) -> {
                if (target == null || err != null) {
                    sender.send("error-player-not-found");
                    return;
                }
                showHistory(null, target, size);
            });
        }
    }

    public @Nullable PlayerDescriptor findPlayer(String str) {
        PlayerDescriptor fast = findPlayerFast(str);
        if (fast != null) return fast;

        if (arc.util.Strings.canParseInt(str)) {
            int pid = Integer.parseInt(str);
            PlayerData data = playerSessionService.getOrLoadFromDb(pid);
            if (data != null) return new PlayerDescriptor(data.nickname, data.uuid, data.pid);
        }

        PlayerData data = playerDataRepository.findByUuid(str);
        if (data != null) return new PlayerDescriptor(data.nickname, data.uuid, data.pid);

        return null;
    }

    public @Nullable PlayerDescriptor findPlayerUuid(String uuid) {
        PlayerDescriptor fast = findPlayerUuidFast(uuid);
        if (fast != null) return fast;

        PlayerData data = playerSessionService.getOrLoadFromDb(uuid);
        if (data != null) {
            PlayerDescriptor desc = new PlayerDescriptor(data.nickname, data.uuid, data.pid);
            descriptorCache.put(uuid, desc);
            return desc;
        }

        return null;
    }

    public boolean useAdminTools(Player player) {
        var session = playerSessionService.get(player.uuid());
        if (session == null || session.getData() == null) return false;
        return session.getData().adminModVersion != null;
    }

    private String getCurrentTimeFormatted() {
        return LocalTime.MIN.plusSeconds(TileLogger.duration()).format(DateTimeFormatter.ISO_LOCAL_TIME);
    }

    private java.util.Locale resolveLocale(@Nullable Player player) {
        return player == null ? bundle.getDefaultLocale() : bundle.locale(player);
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
            str.append(" [#6e7080](").append(desc).append(")[]");
        }
    }
}
