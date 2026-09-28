package org.xcore.testkit.fixtures;

import arc.Core;
import arc.Events;
import arc.Settings;
import arc.mock.MockApplication;
import arc.mock.MockAudio;
import arc.util.CommandHandler;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.core.GameState;
import mindustry.core.NetServer;
import mindustry.core.World;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.net.Administration;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.storage.CoreBlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Headless World fixture managing engine singletons and simulation entities.
 * Implements {@link AutoCloseable} for deterministic teardown.
 */
public final class HeadlessWorld implements AutoCloseable {
    private final int width;
    private final int height;
    private final List<MockPlayer> players = new ArrayList<>();
    private boolean closed = false;

    private HeadlessWorld(Builder builder) {
        this.width = builder.width;
        this.height = builder.height;

        // 1. Ensure immutable base content is loaded
        HeadlessContent.ensureLoaded();

        // 2. Configure Arc mocks
        Core.settings = new Settings();
        Core.app = new MockApplication();
        Core.audio = new MockAudio();

        // 3. Configure Mindustry singletons
        Vars.headless = true;
        Vars.state = new GameState();
        Vars.state.set(builder.initialState);
        Groups.init();

        // 4. Configure Networking
        Vars.net = new MockNet();
        Vars.netServer = new NetServer();

        // 5. Initialize World and Tiles
        Vars.world = new World();
        Vars.world.resize(width, height);
        Vars.world.tiles.fill();

        // Generating mode suppresses tile-change event/group side effects, which is
        // required for bulk map-authoring writes (Tile.setFloor consults
        // World.isGenerating()). Must be set before any tile mutation.
        Vars.world.setGenerating(builder.generating);

        if (builder.defaultFloor != null && builder.defaultFloor != Blocks.air) {
            fillFloor(builder.defaultFloor);
        }
    }

    public static HeadlessWorld create(int width, int height) {
        return builder().dimensions(width, height).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public int width() {
        checkNotClosed();
        return width;
    }

    public int height() {
        checkNotClosed();
        return height;
    }

    public Tile tile(int x, int y) {
        checkNotClosed();
        return Vars.world.tile(x, y);
    }

    public Tile tileWorld(float wx, float wy) {
        checkNotClosed();
        return Vars.world.tileWorld(wx, wy);
    }

    public Building build(int x, int y) {
        checkNotClosed();
        return Vars.world.build(x, y);
    }

    public HeadlessWorld setFloor(int x, int y, Block floor) {
        checkNotClosed();
        Tile t = tile(x, y);
        if (t != null && floor instanceof Floor f) {
            t.setFloor(f);
        }
        return this;
    }

    public HeadlessWorld fillFloor(Block floor) {
        checkNotClosed();
        if (floor instanceof Floor f) {
            Vars.world.tiles.eachTile(tile -> tile.setFloor(f));
        }
        return this;
    }

    public HeadlessWorld fillFloor(int startX, int startY, int w, int h, Block floor) {
        checkNotClosed();
        if (floor instanceof Floor f) {
            for (int x = startX; x < startX + w; x++) {
                for (int y = startY; y < startY + h; y++) {
                    Tile t = tile(x, y);
                    if (t != null) t.setFloor(f);
                }
            }
        }
        return this;
    }

    public HeadlessWorld setBlock(int x, int y, Block block, Team team) {
        return setBlock(x, y, block, team, 0);
    }

    public HeadlessWorld setBlock(int x, int y, Block block, Team team, int rotation) {
        checkNotClosed();
        Tile t = tile(x, y);
        if (t != null) {
            t.setBlock(block, team, rotation);
        }
        return this;
    }

    public HeadlessWorld setAir(int x, int y) {
        checkNotClosed();
        Tile t = tile(x, y);
        if (t != null) {
            t.setAir();
        }
        return this;
    }

    public CoreBlock.CoreBuild addCore(Team team, int x, int y) {
        return addCore(team, x, y, Blocks.coreShard);
    }

    public CoreBlock.CoreBuild addCore(Team team, int x, int y, Block coreBlock) {
        checkNotClosed();
        Tile t = tile(x, y);
        Objects.requireNonNull(t, "Tile cannot be null");
        t.setBlock(coreBlock, team);
        return (CoreBlock.CoreBuild) t.build;
    }

    public MockPlayer addPlayer(String name, Team team) {
        return addPlayer(b -> b.name(name).team(team));
    }

    public MockPlayer addAdmin(String name, Team team) {
        return addPlayer(b -> b.name(name).team(team).admin(true));
    }

    public MockPlayer addPlayer(Consumer<MockPlayer.Builder> configurator) {
        checkNotClosed();
        MockPlayer.Builder b = MockPlayer.builder();
        configurator.accept(b);
        MockPlayer player = b.build();
        players.add(player);
        if (Vars.net instanceof MockNet net) {
            net.register(player.con());
        }
        return player;
    }

    public void removePlayer(MockPlayer player) {
        checkNotClosed();
        player.remove();
        players.remove(player);
    }

    public List<MockPlayer> players() {
        return Collections.unmodifiableList(players);
    }

    public Unit spawnUnit(UnitType type, Team team, float x, float y) {
        checkNotClosed();
        return type.spawn(team, x, y);
    }

    public void registerCommand(String name, String param, String desc, CommandHandler.CommandRunner<Player> runner) {
        checkNotClosed();
        Vars.netServer.clientCommands.register(name, param, desc, runner);
    }

    public CommandHandler.CommandResponse runCommand(MockPlayer player, String commandLine) {
        checkNotClosed();
        String full = commandLine.startsWith("/") ? commandLine : "/" + commandLine;
        return Vars.netServer.clientCommands.handleMessage(full, player.player());
    }

    public void addActionFilter(Administration.ActionFilter filter) {
        checkNotClosed();
        Vars.netServer.admins.addActionFilter(filter);
    }

    public boolean allowAction(MockPlayer player, Administration.ActionType type, Tile tile) {
        return allowAction(player, type, tile, action -> {});
    }

    public boolean allowAction(MockPlayer player, Administration.ActionType type, Tile tile,
                               Consumer<Administration.PlayerAction> config) {
        checkNotClosed();
        return Vars.netServer.admins.allowAction(player.player(), type, tile, config::accept);
    }

    public World world() {
        return Vars.world;
    }

    public GameState state() {
        return Vars.state;
    }

    public NetServer netServer() {
        return Vars.netServer;
    }

    public Administration admins() {
        return Vars.netServer.admins;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;

        for (MockPlayer p : players) {
            p.remove();
        }
        players.clear();

        if (Vars.world != null) {
            Vars.world.clearBuildings();
            Vars.world = null;
        }

        Groups.clear();
        Events.clear();

        Vars.state = null;
        Vars.net = null;
        Vars.netServer = null;
        Vars.player = null;

        Core.app = null;
        Core.audio = null;
        Core.settings = new Settings();
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("HeadlessWorld has already been closed.");
        }
    }

    public static class Builder {
        private int width = 32;
        private int height = 32;
        private Block defaultFloor = Blocks.air;
        private GameState.State initialState = GameState.State.playing;
        private boolean generating = false;

        public Builder dimensions(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder defaultFloor(Block floor) {
            this.defaultFloor = floor;
            return this;
        }

        public Builder state(GameState.State state) {
            this.initialState = state;
            return this;
        }

        /**
         * Enables world generation mode, mirroring {@code World.setGenerating(true)}.
         *
         * <p>Map generators, terrain painters, and layout planners call
         * {@code Tile.setFloor} / {@code Tile.setBlock}, which consult
         * {@code Vars.world.isGenerating()}. In generating mode Mindustry skips
         * per-tile {@code TileChange} event emission and group bookkeeping, so
         * bulk world authoring works without a live net layer or entity groups.
         */
        public Builder generating(boolean generating) {
            this.generating = generating;
            return this;
        }

        public HeadlessWorld build() {
            return new HeadlessWorld(this);
        }
    }
}
