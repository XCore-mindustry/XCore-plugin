package tilelogger.service;

import arc.func.Cons2;
import arc.struct.ObjectMap;
import arc.util.Log;
import arc.util.Nullable;
import arc.util.Strings;
import com.ospx.flubundle.Args;
import com.ospx.flubundle.Bundle;
import com.ospx.flubundle.mindustry.Messenger;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.ai.types.LogicAI;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.net.Administration.PlayerInfo;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.ConstructBlock;
import mindustry.world.blocks.ConstructBlock.ConstructBuild;
import mindustry.world.blocks.storage.CoreBlock;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.cloud.exception.XCoreCommandException;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.FindService;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.startup.ShutdownOrder;
import tilelogger.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class TileLoggerService {
    public static final int MAX_HISTORY = 100, MAX_MUTATIONS = 256;
    public static final List<Block> rollbackBlacklist = List.of(Blocks.coreShard, Blocks.coreFoundation,
            Blocks.coreNucleus, Blocks.coreCitadel, Blocks.coreBastion, Blocks.coreAcropolis);
    private final PlayerDataRepository repository;
    private final SessionService sessions;
    private final FindService find;
    private final Bundle bundle;
    private final Messenger messenger;
    private final Async async;
    private final ObjectMap<String, PlayerConfig> configs = new ObjectMap<>();
    private final Map<String, PlayerDescriptor> descriptors = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<PlayerDescriptor>> pending = new HashMap<>();
    private final Map<String, Long> absentUntil = new ConcurrentHashMap<>();
    private final Set<String> v2Clients = new HashSet<>();
    private final Map<String, PageQuery> requests = new HashMap<>();
    private Cons2<Player, String> helloHandler, requestHandler;
    private mindustry.core.NetServer packetServer;
    private long generation, archiveRequest, serverRequest;
    private volatile boolean stopped;
    public void suspendRecording() {
        recording = false;
        TileLogger.setLive(false);
    }
    private boolean recording = true, suppressEvents;

    @Inject
    public TileLoggerService(PlayerDataRepository repository, SessionService sessions, FindService find,
            Bundle bundle, Async async) {
        this.repository = repository;
        this.sessions = sessions;
        this.find = find;
        this.bundle = bundle;
        this.messenger = Messenger.of(bundle);
        this.async = async;
    }
    public long generation() {
        return generation;
    }
    public PlayerConfig getPlayerConfig(Player player) {
        if (player == null)
            return new PlayerConfig();
        return configs.get(player.uuid(), PlayerConfig::new);
    }
    public void removePlayerConfig(String uuid) {
        configs.remove(uuid);
        descriptors.remove(uuid);
        absentUntil.remove(uuid);
        requests.remove(uuid);
        v2Clients.remove(uuid);
        requestTimes.remove(uuid);
    }
    public static int checkedHistoryLimit(long size) {
        if (size < 1 || size > MAX_HISTORY)
            throw new XCoreCommandException("tilelogger-error-limit", Args.of("max", MAX_HISTORY));
        return (int) size;
    }
    public static Rect checkedRect(Rect rect) {
        if (rect.x1 < 0 || rect.y1 < 0 || rect.x2 < rect.x1 || rect.y2 < rect.y1
                || rect.x2 >= Vars.world.width() || rect.y2 >= Vars.world.height())
            throw new XCoreCommandException("tilelogger-error-coordinates");
        return rect.copy();
    }
    public Rect selectedRect(Player player) {
        PlayerConfig config = getPlayerConfig(player);
        if (config.selection != PlayerConfig.Selection.READY || config.session != generation)
            throw new XCoreCommandException("tilelogger-error-selection");
        return checkedRect(config.rect);
    }
    public void beginSelection(Player player) {
        var config = getPlayerConfig(player);
        config.selection = PlayerConfig.Selection.FIRST_POINT;
        config.session = generation;
    }
    public void invalidateWorld() {
        ++generation;
        ++archiveRequest;
        recording = false;
        requests.clear();
        TileLogger.setLive(false);
        configs.each((uuid, config) -> {
            config.selection = PlayerConfig.Selection.NONE;
            config.session = generation;
        });
    }
    public void resetHistory(String path, boolean write) {
        if (!write)
            throw new IllegalArgumentException("Use openArchive for asynchronous reads");
        String session = UUID.randomUUID().toString();
        TileLogger.startSession(path, session, ConfigCodec.manifest());
        invalidateWorld();
        recording = true;
        TileLogger.setLive(true);
        for (Tile tile : Vars.world.tiles)
            if (tile.build != null && tile == tile.build.tile && !(tile.build instanceof ConstructBuild))
                logBuild(tile, null, ActionKind.BASELINE);
        Groups.player.each(this::sendSession);
    }
    public void startMapHistory() {
        String name =
                Vars.state.map == null ? "map" : Vars.state.map.name().replaceAll("[^a-zA-Z0-9_-]", "_");
        resetHistory(UUID.randomUUID() + "_" + name, true);
    }
    public void openArchive(XCoreSender sender, String path) {
        long captured = generation, request = ++archiveRequest;
        String manifest = ConfigCodec.manifest();
        async.supply(() -> TileLogger.prepareArchive(path, manifest)).thenMain((handle, error) -> {
            if (error != null) {
                Log.err("[TileLogger] Archive load failed", error);
                sender.send("tilelogger-error-archive");
                return;
            }
            if (stopped || captured != generation || request != archiveRequest
                    || (sender.isPlayer()
                            && (!Async.isPlayerOnline(sender.player()) || !sender.player().admin))) {
                TileLogger.discardArchive(handle);
                if (!stopped)
                    sender.send("tilelogger-error-session");
                return;
            }
            TileLogger.activateArchive(handle);
            invalidateWorld();
            Groups.player.each(this::sendSession);
            sender.send("tilelogger-archive-open", Args.of("status", TileLogger.status()));
        });
    }
    public @Nullable PlayerInfo unitToPlayerInfo(@Nullable Unit unit) {
        if (unit == null)
            return null;
        if (unit.controller() instanceof LogicAI logic && logic.controller != null) {
            Tile tile = logic.controller.tile;
            TileState[] states = TileLogger.getHistory(tile.x, tile.y, tile.x, tile.y, "", -1, 0, 1);
            return states.length == 0 ? null : states[0].playerInfo();
        }
        return unit.isPlayer() ? unit.getPlayer().getInfo() : null;
    }
    private void persist(int x, int y, String actor, int team, int block, int rotation,
            ConfigCodec.Encoded config, ActionKind kind) {
        try {
            TileLogger.record(x, y, actor, team, block, rotation, config, kind);
        } catch (RuntimeException failure) {
            suspendRecording();
            throw failure;
        }
    }
    public void logBuild(Tile tile, @Nullable PlayerInfo actor) {
        logBuild(tile, actor, ActionKind.BUILD_COMPLETED);
    }
    public void logBuild(Tile tile, @Nullable PlayerInfo actor, ActionKind kind) {
        if (stopped || !recording || suppressEvents || tile == null || tile.build == null)
            return;
        tile = tile.build.tile;
        Block block = tile.block();
        Object value = tile.build.config();
        int rotation = tile.build.rotation;
        if (tile.build instanceof ConstructBuild construct) {
            if (kind != ActionKind.BUILD_STARTED && kind != ActionKind.DESTROY_STARTED)
                return;
            block = construct.current;
            value = construct.lastConfig;
            if (kind == ActionKind.DESTROY_STARTED && construct.prevBuild != null
                    && !construct.prevBuild.isEmpty()) {
                Building previous = construct.prevBuild.first();
                block = previous.block;
                value = previous.config();
            }
        }
        persist(tile.x, tile.y, actor == null ? "" : actor.id, tile.team().id, Short.toUnsignedInt(block.id),
                rotation, ConfigCodec.encode(value), kind);
    }
    public void logDestroy(Tile tile, @Nullable PlayerInfo actor) {
        logDestroy(tile, actor, ActionKind.DESTROY_COMPLETED);
    }
    public void logDestroy(Tile tile, @Nullable PlayerInfo actor, ActionKind kind) {
        if (stopped || !recording || suppressEvents || tile == null)
            return;
        if (tile.build != null)
            tile = tile.build.tile;
        Block block = tile.block();
        Object value = tile.build == null ? null : tile.build.config();
        int rotation = tile.build == null ? 0 : tile.build.rotation;
        if (tile.build instanceof ConstructBuild construct)
            block = construct.current;
        persist(tile.x, tile.y, actor == null ? "" : actor.id, tile.team().id, Short.toUnsignedInt(block.id),
                rotation, ConfigCodec.encode(value), kind);
    }
    public void logPickup(Building building, @Nullable PlayerInfo actor) {
        if (stopped || !recording || suppressEvents)
            return;
        persist(building.tile.x, building.tile.y, actor == null ? "" : actor.id, building.team.id,
                Short.toUnsignedInt(building.block.id), building.rotation,
                ConfigCodec.encode(building.config()), ActionKind.PICKUP);
    }
    public void showHistory(@Nullable Player caller, PlayerDescriptor target, long size) {
        show(caller, "tilelogger-history-player",
                Args.of("player", target.toString(), "time", TileState.formatDuration(TileLogger.duration())),
                TileLogger.getHistory(0, 0, -1, -1, target.uuid, -1, 0, checkedHistoryLimit(size)));
    }
    public void showHistory(@Nullable Player caller, int x, int y, long size) {
        checkedRect(new Rect(x, y, x, y));
        TileState[] states = TileLogger.getHistory(x, y, x, y, "", -1, 0, checkedHistoryLimit(size));
        show(caller, "tilelogger-history-tile",
                Args.of("x", x, "y", y, "time", TileState.formatDuration(TileLogger.duration())), states);
    }
    private void show(Player caller, String key, Map<String, Object> args, TileState[] states) {
        preloadAndRender(caller, states, () -> {
            var locale = caller == null ? bundle.getDefaultLocale() : bundle.locale(caller);
            StringBuilder text = new StringBuilder(bundle.format(locale, key, args));
            if (TileLogger.isReadOnly())
                text.append("\n").append(bundle.format(locale, "tilelogger-read-only"));
            for (var state : states) {
                var descriptor = findPlayerUuidFast(state.uuid);
                String actor = descriptor == null ? state.uuid : descriptor.toString();
                text.append("\n  • ")
                        .append(state.x)
                        .append(',')
                        .append(state.y)
                        .append(' ')
                        .append(state.timeAsString())
                        .append(' ')
                        .append(state.blockEmoji())
                        .append(' ')
                        .append(bundle.format(locale, state.kind().messageKey()))
                        .append(' ')
                        .append(escape(state.getConfigAsString()))
                        .append(' ')
                        .append(actor == null || actor.isBlank() ? "" : escape(actor));
            }
            if (caller == null)
                Log.info(text.toString());
            else
                caller.sendMessage(text.toString());
        });
    }
    private CompletableFuture<Void> preloadDescriptors(TileState[] states) {
        var futures = Arrays.stream(states)
                              .map(s -> s.uuid)
                              .filter(Objects::nonNull)
                              .filter(s -> !s.isBlank())
                              .distinct()
                              .map(this::findPlayerUuidAsync)
                              .toArray(CompletableFuture[] ::new);
        return CompletableFuture.allOf(futures).handle((ignored, error) -> {
            if (error != null)
                Log.err("[TileLogger] Identity lookup unavailable; rendering UUIDs", error);
            return null;
        });
    }
    private void preloadAndRender(Player caller, TileState[] states, Runnable render) {
        long captured = generation;
        var ready = preloadDescriptors(states);
        if (caller == null)
            async.onMain(ready, (ignored, error) -> {
                if (!stopped && captured == generation)
                    render.run();
            });
        else
            async.onMainForPlayer(caller, ready, (player, ignored) -> {
                if (!stopped && captured == generation)
                    render.run();
            });
    }
    public @Nullable PlayerDescriptor findPlayerUuidFast(String uuid) {
        if (uuid == null || uuid.isBlank())
            return null;
        var session = sessions.get(uuid);
        if (session != null && session.getData() != null)
            return cache(session.getData());
        var cached = descriptors.get(uuid);
        if (cached != null)
            return cached;
        var info = Vars.netServer.admins.getInfoOptional(uuid);
        if (info == null)
            return null;
        var result = new PlayerDescriptor(info.lastName, uuid, -1);
        descriptors.put(uuid, result);
        return result;
    }
    public @Nullable PlayerDescriptor findPlayerFast(String input) {
        if (input == null || input.isBlank())
            return null;
        if (input.equalsIgnoreCase("all"))
            return new PlayerDescriptor("all", "", -1);
        var player = find.playerByName(input);
        if (player != null) {
            var result = findPlayerUuidFast(player.uuid());
            return result == null ? new PlayerDescriptor(player.name, player.uuid(), -1) : result;
        }
        return findPlayerUuidFast(input);
    }
    private PlayerDescriptor cache(PlayerData data) {
        if (data == null)
            return null;
        var result = new PlayerDescriptor(data.nickname, data.uuid, data.pid);
        descriptors.put(data.uuid, result);
        absentUntil.remove(data.uuid);
        return result;
    }
    public CompletableFuture<PlayerDescriptor> findPlayerUuidAsync(String uuid) {
        if (uuid == null || uuid.isBlank())
            return CompletableFuture.completedFuture(null);
        var fast = findPlayerUuidFast(uuid);
        if (fast != null)
            return CompletableFuture.completedFuture(fast);
        if (absentUntil.getOrDefault(uuid, 0L) > System.nanoTime())
            return CompletableFuture.completedFuture(null);
        // Entries are installed before invoking the repository: completed futures cannot race cleanup.
        synchronized (pending) {
            var existing = pending.get(uuid);
            if (existing != null)
                return existing;
            var result = new CompletableFuture<PlayerDescriptor>();
            pending.put(uuid, result);
            result.orTimeout(2, java.util.concurrent.TimeUnit.SECONDS).whenComplete((value, error) -> {
                synchronized (pending) {
                    pending.remove(uuid, result);
                }
                if (error != null)
                    absentUntil.put(uuid, System.nanoTime() + 1_000_000_000L);
            });
            try {
                repository.findByUuidAsync(uuid).whenComplete((data, error) -> {
                    if (error != null)
                        result.completeExceptionally(error);
                    else {
                        if (data == null)
                            absentUntil.put(uuid, System.nanoTime() + 5_000_000_000L);
                        result.complete(cache(data));
                    }
                    synchronized (pending) {
                        pending.remove(uuid, result);
                    }
                });
            } catch (RuntimeException error) {
                pending.remove(uuid);
                result.completeExceptionally(error);
            }
            return result;
        }
    }
    public CompletableFuture<PlayerDescriptor> findPlayerAsync(String input) {
        var fast = findPlayerFast(input);
        if (fast != null)
            return CompletableFuture.completedFuture(fast);
        if (input != null && Strings.canParseInt(input)) {
            return repository.findByPidAsync(Integer.parseInt(input))
                    .toCompletableFuture()
                    .thenCompose(data -> {
                        if (data != null)
                            return CompletableFuture.completedFuture(cache(data));
                        return repository.findByUuidAsync(input).toCompletableFuture().thenApply(this::cache);
                    });
        }
        return findPlayerUuidAsync(input);
    }
    public boolean useAdminTools(Player player) {
        var session = sessions.get(player.uuid());
        return session != null && session.getData() != null && session.getData().adminModVersion != null;
    }
    public String getMemoryUsage(Player viewer) {
        return bundle.format(viewer == null ? bundle.getDefaultLocale() : bundle.locale(viewer),
                       "tilelogger-memory",
                       Bundle.args("jvmUsed",
                               mb(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()),
                               "jvmMax", mb(Runtime.getRuntime().maxMemory()), "historyUsed",
                               mb(TileLogger.memoryUsage(2)), "historyCap", mb(TileLogger.memoryUsage(3)),
                               "playersUsed", mb(TileLogger.memoryUsage(4)), "playersCap",
                               mb(TileLogger.memoryUsage(5)), "configsUsed", mb(TileLogger.memoryUsage(6)),
                               "configsCap", mb(TileLogger.memoryUsage(7))))
                + "\n" + TileLogger.status();
    }
    private static String escape(String value) {
        return value == null ? "" : value.replace("[", "[[");
    }
    private static String mb(long bytes) {
        return String.format(Locale.ROOT, "%.2f", bytes / 1e6);
    }

    private void requireWritable() {
        if (stopped || !recording)
            throw new XCoreCommandException("tilelogger-error-session");
        try {
            TileLogger.checkWritable();
        } catch (RuntimeException failure) {
            throw new XCoreCommandException(
                    "tilelogger-error-storage", Args.of("reason", escape(failure.getMessage())));
        }
    }
    private static void checkAdmin(XCoreSender sender) {
        // The existing "admin" command permission maps to the native admin capability.
        if (sender.isPlayer() && !sender.player().admin)
            throw new XCoreCommandException("error-access-denied");
    }
    public void executeRollback(XCoreSender sender, String input, Duration duration, boolean selection) {
        executeRollback(sender, input, duration, selection, false);
    }
    public void executeRollback(
            XCoreSender sender, String input, Duration duration, boolean selection, boolean preview) {
        checkAdmin(sender);
        requireWritable();
        long seconds;
        try {
            seconds = duration.toSeconds();
            if (seconds == Long.MIN_VALUE)
                throw new ArithmeticException();
        } catch (ArithmeticException invalid) {
            throw new XCoreCommandException("tilelogger-error-duration");
        }
        if (selection && !sender.isPlayer())
            throw new XCoreCommandException("error-not-allowed-from-console");
        Rect rect = selection ? selectedRect(sender.player())
                              : checkedRect(new Rect(0, 0, Vars.world.width() - 1, Vars.world.height() - 1));
        long captured = generation;
        if (input.equalsIgnoreCase("self") && sender.isPlayer()) {
            rollback(sender.player(),
                    new PlayerDescriptor(sender.player().plainName(), sender.player().uuid(), -1), -1,
                    seconds, rect, preview);
            return;
        }
        var future = findPlayerAsync(input);
        async.onMain(future, (target, error) -> {
            if (stopped)
                return;
            if (captured != generation || (sender.isPlayer() && !Async.isPlayerOnline(sender.player()))) {
                sender.send("tilelogger-error-session");
                return;
            }
            checkAdmin(sender);
            if (error != null) {
                Log.err("[TileLogger] Target lookup failed", error);
                sender.send("tilelogger-error-lookup");
                return;
            }
            if (target == null) {
                sender.send("error-player-not-found");
                return;
            }
            rollback(sender.isPlayer() ? sender.player() : null, target, -1, seconds, rect, preview);
        });
    }
    public void executeHistoryPlayer(XCoreSender sender, String input, long size) {
        checkedHistoryLimit(size);
        long captured = generation;
        async.onMain(findPlayerAsync(input), (target, error) -> {
            if (stopped || captured != generation
                    || (sender.isPlayer() && !Async.isPlayerOnline(sender.player())))
                return;
            if (error != null) {
                sender.send("tilelogger-error-lookup");
                return;
            }
            if (target == null) {
                sender.send("error-player-not-found");
                return;
            }
            Player caller = sender.isPlayer() ? sender.player() : null;
            // One native read is shared by chat and the bounded legacy response.
            TileState[] states = TileLogger.getHistory(0, 0, -1, -1, target.uuid, -1, 0, size);
            show(caller, "tilelogger-history-player",
                    Args.of("player", target.toString(), "time",
                            TileState.formatDuration(TileLogger.duration())),
                    states);
            if (caller != null && useAdminTools(caller)) {
                if (v2Clients.contains(caller.uuid()))
                    beginPage(caller, playerQuery(target.uuid, --serverRequest));
                else
                    sendLegacy(caller, "tilelogger_history_player", states);
            }
        });
    }
    private record Image(Tile tile, Block block, Team team, int rotation, ConfigCodec.Encoded config) {
        static Image capture(Tile tile) {
            var config = ConfigCodec.encode(tile.build == null ? null : tile.build.config());
            if (!config.supported())
                throw new IllegalArgumentException("Cannot restore current configuration");
            return new Image(
                    tile, tile.block(), tile.team(), tile.build == null ? 0 : tile.build.rotation, config);
        }
        void restore() {
            set(tile, block, team, rotation,
                    ConfigCodec.decode(config.type(), config.type() >= 5 ? config.bytes() : config.scalar()));
        }
    }
    private static void set(Tile tile, Block block, Team team, int rotation, Object config) {
        Call.setTile(tile, block, team, rotation);
        if (tile.build != null)
            tile.build.configure(config);
    }
    private enum Check { OK, PROTECTED, CONFLICT, UNRESTORABLE }
    private static Set<Integer> footprint(Tile anchor, Block block, Rect area) {
        var positions = new HashSet<Integer>();
        for (int dx = 0; dx < block.size; dx++)
            for (int dy = 0; dy < block.size; dy++) {
                int x = anchor.x + block.sizeOffset + dx, y = anchor.y + block.sizeOffset + dy;
                if (!area.contains(x, y) || Vars.world.tile(x, y) == null)
                    throw new IllegalArgumentException("Building footprint out of selection");
                positions.add(arc.math.geom.Point2.pack(x, y));
            }
        return positions;
    }
    private static boolean matches(Tile tile, TileState expected) {
        if (expected == null || expected.destroy || expected.block == 0)
            return tile.block() == Blocks.air;
        return tile.build != null && tile.build.tile == tile
                && Short.toUnsignedInt(tile.blockID()) == expected.block && tile.team().id == expected.team
                && tile.build.rotation == expected.rotation
                && Objects.deepEquals(tile.build.config(), expected.getConfig());
    }
    private Check validate(TileState change, Rect area, Set<Integer> reserved) {
        Tile tile = change.tile();
        Block target = change.destroy ? Blocks.air : change.block();
        if (tile == null || target == null)
            return Check.UNRESTORABLE;
        if (tile.block() instanceof CoreBlock || target instanceof CoreBlock)
            return Check.PROTECTED;
        if (tile.block() instanceof ConstructBlock || target instanceof ConstructBlock)
            return Check.CONFLICT;
        try {
            var current = TileLogger.currentState(change.x, change.y);
            if ((current == null ? 0 : current.sequence) != change.expectedSequence
                    || !matches(tile, current))
                return Check.CONFLICT;
            var covered = footprint(tile, tile.block(), area);
            covered.addAll(footprint(tile, target, area));
            for (int position : covered) {
                Tile other = Vars.world.tile(position);
                if (other.block() instanceof CoreBlock)
                    return Check.PROTECTED;
                if (other.build != null && other.build != tile.build)
                    return Check.CONFLICT;
                if (reserved.contains(position))
                    return Check.CONFLICT;
            }
            if (!change.destroy)
                change.getConfig();
            Image.capture(tile);
            reserved.addAll(covered);
            return Check.OK;
        } catch (IllegalArgumentException invalid) {
            return Check.UNRESTORABLE;
        }
    }
    public void rollback(@Nullable Player caller, PlayerDescriptor target, int teams, long time, Rect rect) {
        rollback(caller, target, teams, time, rect, false);
    }
    public void rollback(@Nullable Player caller, PlayerDescriptor target, int teams, long time,
            Rect requested, boolean preview) {
        requireWritable();
        Rect rect = checkedRect(requested);
        TileState[] changes;
        try { changes = TileLogger.planRollback(rect, target.uuid, teams, time); }
        catch (IllegalArgumentException limit) { throw new XCoreCommandException("tilelogger-error-plan"); }
        long plan = changes.length == 0 ? 0 : changes[0].planId;
        int applied = 0, protectedCount = 0, conflicts = 0, unrestorable = 0, failed = 0;
        try {
            if (changes.length > MAX_MUTATIONS)
                throw new XCoreCommandException("tilelogger-error-mutations", Args.of("max", MAX_MUTATIONS));
            var reserved = new HashSet<Integer>();
            var ready = new ArrayList<TileState>();
            for (var change : changes) {
                switch (validate(change, rect, reserved)) {
                    case OK -> ready.add(change);
                    case PROTECTED -> protectedCount++;
                    case CONFLICT -> conflicts++;
                    case UNRESTORABLE -> unrestorable++;
                }
            }
            if (preview)
                applied = ready.size();
            else
                for (int index = 0; index < ready.size(); index++) {
                    var change = ready.get(index);
                    Image before = null;
                    boolean mutationStarted = false;
                    try {
                        requireWritable();
                        // A previous Call may synchronously invoke another plugin. Check the full footprint
                        // again.
                        Check recheck = validate(change, rect, new HashSet<>());
                        if (recheck != Check.OK) {
                            switch (recheck) {
                                case PROTECTED -> protectedCount++;
                                case CONFLICT -> conflicts++;
                                case UNRESTORABLE -> unrestorable++;
                                default -> {
                                }
                            }
                            continue;
                        }
                        before = Image.capture(change.tile());
                        suppressEvents = true;
                        mutationStarted = true;
                        set(change.tile(), change.destroy ? Blocks.air : change.block(), change.team(),
                                change.rotation, change.destroy ? null : change.getConfig());
                        TileLogger.commitRollback(change);
                        applied++;
                    } catch (RuntimeException failure) {
                        failed++;
                        Log.err("[TileLogger] Rollback position failed", failure);
                        boolean abort = false;
                        if (mutationStarted && before != null)
                            try {
                                before.restore();
                            } catch (RuntimeException restoreFailure) {
                                Log.err("[TileLogger] Before-image restore failed; recording suspended",
                                        restoreFailure);
                                abort = true;
                            }
                        try {
                            TileLogger.checkWritable();
                        } catch (RuntimeException storageFailure) {
                            abort = true;
                        }
                        if (abort) {
                            suspendRecording();
                            failed += ready.size() - index - 1;
                            break;
                        }
                    } finally {
                        suppressEvents = false;
                    }
                }
        } finally {
            if (plan != 0)
                TileLogger.discardPlan(plan);
        }
        var args = Bundle.args("count", applied, "protected", protectedCount, "conflicts", conflicts,
                "unsupported", unrestorable, "failed", failed, "target", target.toString(), "caller",
                caller == null ? bundle.format(bundle.getDefaultLocale(), "tilelogger-server")
                               : caller.coloredName());
        String key = preview ? "tilelogger-rollback-preview" : "tilelogger-rollback-broadcast";
        if (preview) {
            if (caller == null)
                Log.info(bundle.format(bundle.getDefaultLocale(), key, args));
            else
                messenger.to(caller).send(key, args);
        } else {
            Groups.player.each(p -> messenger.to(p).send(key, args));
            Log.info(bundle.format(bundle.getDefaultLocale(), key, args));
        }
    }
    public void fill(@Nullable Player caller, @Nullable Team team, Block block, Rect requested) {
        requireWritable();
        Rect rect = checkedRect(requested);
        if (block instanceof CoreBlock || block instanceof ConstructBlock || block.size < 1)
            throw new XCoreCommandException("tilelogger-error-protected");
        if (team == null && caller == null)
            throw new XCoreCommandException("tilelogger-error-team");
        Team destination = team == null ? caller.team() : team;
        var tiles = new ArrayList<Tile>();
        var covered = new HashSet<Integer>();
        for (int x = rect.x1 - block.sizeOffset; x + block.sizeOffset + block.size - 1 <= rect.x2;
                x += block.size)
            for (int y = rect.y1 - block.sizeOffset; y + block.sizeOffset + block.size - 1 <= rect.y2;
                    y += block.size) {
                Tile tile = Vars.world.tile(x, y);
                if (tile == null)
                    throw new XCoreCommandException("tilelogger-error-coordinates");
                if (tile.build != null && tile.build.tile != tile)
                    throw new XCoreCommandException("tilelogger-error-footprint");
                try {
                    var footprint = footprint(tile, block, rect);
                    footprint.addAll(footprint(tile, tile.block(), rect));
                    for (int position : footprint) {
                        Tile other = Vars.world.tile(position);
                        if (other.block() instanceof CoreBlock)
                            throw new XCoreCommandException("tilelogger-error-protected");
                        if (other.build != null && other.build != tile.build)
                            throw new XCoreCommandException("tilelogger-error-footprint");
                        if (!covered.add(position))
                            throw new XCoreCommandException("tilelogger-error-footprint");
                    }
                } catch (IllegalArgumentException invalid) {
                    throw new XCoreCommandException("tilelogger-error-footprint");
                }
                Image.capture(tile);
                tiles.add(tile);
                if (tiles.size() > MAX_MUTATIONS)
                    throw new XCoreCommandException(
                            "tilelogger-error-mutations", Args.of("max", MAX_MUTATIONS));
            }
        for (Tile tile : tiles) {
            Image before = Image.capture(tile);
            try {
                requireWritable();
                suppressEvents = true;
                set(tile, block, destination, 0, null);
                persist(tile.x, tile.y, caller == null ? "" : caller.uuid(), destination.id,
                        Short.toUnsignedInt(block.id), 0, ConfigCodec.encode(null), ActionKind.ADMIN_FILL);
            } catch (RuntimeException failure) {
                before.restore();
                throw failure;
            } finally {
                suppressEvents = false;
            }
        }
        if (caller != null)
            messenger.to(caller).send(
                    "tilelogger-fill-success", Args.of("emoji", block.emoji(), "block", block));
    }

    private static final int PAGE_ROWS = 32, PACKET_BYTES = 8192;
    private static final class PageQuery {
        long id, generation, before = Long.MAX_VALUE;
        int x, y;
        String type, uuid = "", session, cursor = "";
        boolean busy;
    }
    private final Map<String, Long> requestTimes = new HashMap<>();
    public void registerPackets() {
        if (packetServer != null)
            return;
        packetServer = Vars.netServer;
        helloHandler = (player, version) -> {
            if (!stopped && "2".equals(version) && useAdminTools(player)) {
                v2Clients.add(player.uuid());
                sendSession(player);
            }
        };
        requestHandler = (player, text) -> {
            if (stopped || !v2Clients.contains(player.uuid()) || !useAdminTools(player)
                    || text == null || text.length() > 1024)
                return;
            long now = System.nanoTime();
            if (now - requestTimes.getOrDefault(player.uuid(), 0L) < 100_000_000L)
                return;
            requestTimes.put(player.uuid(), now);
            try {
                var request = HistoryWire.read(HistoryRequest.class, text);
                if (request == null || request.requestId == 0)
                    return;
                if (request.cursor != null && !request.cursor.isEmpty()) {
                    var previous = requests.get(player.uuid());
                    if (previous == null || previous.id != request.requestId || previous.cursor.isEmpty()
                            || !previous.cursor.equals(request.cursor) || previous.busy)
                        return;
                    sendPage(player, previous);
                    return;
                }
                if (request.requestId < 1)
                    return;
                var query = new PageQuery();
                query.id = request.requestId;
                query.type = request.type;
                if ("tile".equals(query.type)) {
                    checkedRect(new Rect(request.x, request.y, request.x, request.y));
                    query.x = request.x;
                    query.y = request.y;
                } else if ("player".equals(query.type)) {
                    if (request.uuid == null || request.uuid.isBlank()
                            || request.uuid.getBytes(StandardCharsets.UTF_8).length > 256)
                        return;
                    query.uuid = request.uuid;
                } else
                    return;
                beginPage(player, query);
            } catch (RuntimeException invalid) {
                Log.debug("[TileLogger] Rejected history request: @", invalid.getMessage());
            }
        };
        packetServer.addPacketHandler("tilelogger_hello", helloHandler);
        packetServer.addPacketHandler("tilelogger_history_request", requestHandler);
    }
    public void unregisterPackets() {
        if (!org.xcore.plugin.concurrent.GameThread.isGameThread()) {
            arc.Core.app.post(this::unregisterPackets);
            return;
        }
        if (packetServer == null)
            return;
        packetServer.getPacketHandlers("tilelogger_hello").remove(helloHandler, true);
        packetServer.getPacketHandlers("tilelogger_history_request").remove(requestHandler, true);
        packetServer = null;
    }
    private void sendSession(Player player) {
        if (!v2Clients.contains(player.uuid()) || !useAdminTools(player) || player.con == null)
            return;
        var page = new HistoryPage();
        page.sessionId = TileLogger.sessionId();
        page.query = "session";
        Call.clientPacketReliable(player.con, "tilelogger_session", HistoryWire.write(page));
    }
    private PageQuery playerQuery(String uuid, long id) {
        var query = new PageQuery();
        query.id = id;
        query.type = "player";
        query.uuid = uuid;
        return query;
    }
    private void beginPage(Player player, PageQuery query) {
        query.generation = generation;
        query.session = TileLogger.sessionId();
        requests.put(player.uuid(), query);
        sendPage(player, query);
    }
    private void sendPage(Player player, PageQuery query) {
        if (query.busy || query.generation != generation || !useAdminTools(player))
            return;
        query.busy = true;
        TileState[] states;
        try {
            states = "tile".equals(query.type)
                    ? TileLogger.getHistory(
                              query.x, query.y, query.x, query.y, "", -1, 0, PAGE_ROWS + 1, query.before)
                    : TileLogger.getHistory(0, 0, -1, -1, query.uuid, -1, 0, PAGE_ROWS + 1, query.before);
        } catch (RuntimeException failure) {
            query.busy = false;
            throw failure;
        }
        preloadAndRender(player, states, () -> {
            if (requests.get(player.uuid()) != query || !useAdminTools(player))
                return;
            query.busy = false;
            var page = new HistoryPage();
            page.requestId = query.id;
            page.sessionId = query.session;
            page.query = query.type;
            var entries = new ArrayList<TileStatePacket>();
            var locale = bundle.locale(player);
            long before = query.before;
            for (int i = states.length - 1; i >= 0 && entries.size() < PAGE_ROWS; i--) {
                var packet = packet(states[i], locale, false);
                entries.add(0, packet);
                page.entries = entries.toArray(TileStatePacket[] ::new);
                // Reserve room for the continuation token before deciding the byte budget.
                page.nextCursor = "00000000-0000-0000-0000-000000000000";
                if (HistoryWire.write(page).getBytes(StandardCharsets.UTF_8).length > PACKET_BYTES) {
                    entries.remove(0);
                    break;
                }
                before = states[i].sequence;
            }
            page.truncated = states.length > entries.size();
            page.entries = entries.toArray(TileStatePacket[] ::new);
            if (page.truncated && !entries.isEmpty()) {
                query.before = before;
                query.cursor = UUID.randomUUID().toString();
                page.nextCursor = query.cursor;
            } else {
                query.cursor = "";
                page.nextCursor = "";
            }
            Call.clientPacketReliable(player.con, "tilelogger_history_v2", HistoryWire.write(page));
        });
    }
    private TileStatePacket packet(TileState state, Locale locale, boolean legacy) {
        var descriptor = findPlayerUuidFast(state.uuid);
        String name = descriptor == null ? (state.uuid.isBlank() ? "@" + state.team() : state.uuid)
                                         : descriptor.toString();
        if (name.codePointCount(0, name.length()) > 128)
            name = name.substring(0, name.offsetByCodePoints(0, 128));
        var packet = new TileStatePacket(
                state, escape(name), bundle.format(locale, state.kind().messageKey()), legacy);
        packet.config = escape(packet.config);
        return packet;
    }
    private void sendLegacy(Player player, String name, TileState[] states) {
        preloadAndRender(player, states, () -> {
            var packets = new ArrayList<TileStatePacket>();
            for (int i = states.length - 1; i >= 0 && packets.size() < 16; i--) {
                packets.add(0, packet(states[i], bundle.locale(player), true));
                if (HistoryWire.write(packets.toArray(TileStatePacket[] ::new))
                                .getBytes(StandardCharsets.UTF_8)
                                .length
                        > PACKET_BYTES) {
                    packets.remove(0);
                    break;
                }
            }
            Call.clientPacketReliable(
                    player.con, name, HistoryWire.write(packets.toArray(TileStatePacket[] ::new)));
        });
    }
    public void sendTileHistory(int x, int y, Player player) {
        if (v2Clients.contains(player.uuid()))
            return; // v2 clients supply their own request identity on tap.
        checkedRect(new Rect(x, y, x, y));
        sendLegacy(player, "tilelogger_history_tile", TileLogger.getHistory(x, y, x, y, "", -1, 0, 16));
    }
    public void sendPlayerHistory(PlayerDescriptor target, Player player) {
        if (v2Clients.contains(player.uuid()))
            beginPage(player, playerQuery(target.uuid, --serverRequest));
        else
            sendLegacy(player, "tilelogger_history_player",
                    TileLogger.getHistory(0, 0, -1, -1, target.uuid, -1, 0, 16));
    }
    @PreDestroy(priority = ShutdownOrder.STOP_PRODUCERS)
    public void stop() {
        stopped = true;
        recording = false;
        TileLogger.setLive(false);
        ++generation;
        Runnable remove = () -> {
            unregisterPackets();
            requests.clear();
            v2Clients.clear();
        };
        if (org.xcore.plugin.concurrent.GameThread.isGameThread())
            remove.run();
        else
            arc.Core.app.post(remove);
        // The writer owns native files beyond this scope. Drain it off the tick thread.
        try {
            async.run(() -> {
                try {
                    TileLogger.flush();
                } catch (RuntimeException error) {
                    Log.err("[TileLogger] Flush failed", error);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException shutdown) {
            Log.err("[TileLogger] Flush executor already stopped", shutdown);
        }
    }
}
