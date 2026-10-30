package tilelogger;

import arc.math.geom.Vec2;
import com.ospx.flubundle.Bundle;
import com.ospx.flubundle.mindustry.Messenger;
import mindustry.Vars;
import mindustry.ai.UnitCommand;
import mindustry.content.*;
import mindustry.core.GameState;
import mindustry.gen.Payloadc;
import mindustry.input.InputHandler;
import mindustry.game.Team;
import mindustry.world.blocks.units.UnitFactory.UnitFactoryBuild;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.service.FindService;
import org.xcore.plugin.session.SessionService;
import org.xcore.testkit.fixtures.HeadlessWorld;
import org.xcore.testkit.fixtures.junit.*;
import tilelogger.event.TileLoggerEventHandler;
import tilelogger.service.TileLoggerService;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(HeadlessWorldExtension.class)
@WithHeadlessWorld(width = 32, height = 32)
class RollbackSafetyTest {
    private TileLoggerService service;
    private TileLoggerEventHandler handler;
    private final Rect area = new Rect(0, 0, 31, 31);
    private final PlayerDescriptor all = new PlayerDescriptor("all", "", -1);

    @BeforeEach
    void start() {
        service = new TileLoggerService(mock(PlayerDataRepository.class), mock(SessionService.class),
                mock(FindService.class), Bundle.INSTANCE, mock(Async.class));
        Vars.state.set(GameState.State.menu);
        handler = new TileLoggerEventHandler(service, Messenger.of(Bundle.INSTANCE));
        handler.init();
        Vars.state.set(GameState.State.playing);
        Vars.state.rules.unitCap = 100;
        service.resetHistory("", true);
    }
    @AfterEach
    void stop() { handler.stop(); }

    private UnitFactoryBuild factory(HeadlessWorld world) {
        world.setBlock(8, 8, Blocks.groundFactory, Team.sharded);
        var build = (UnitFactoryBuild) world.tile(8, 8).build;
        build.configured(null, 0);
        service.resetHistory("", true);
        return build;
    }
    private void stock(UnitFactoryBuild build) {
        build.items.set(Items.silicon, 7);
        build.health = build.maxHealth / 2;
    }
    @Test
    void configRollbackKeepsTheBuildingInventoryAndHealth(HeadlessWorld world) {
        var build = factory(world);
        InputHandler.tileConfig(null, build, 1);
        stock(build);
        service.rollback(null, all, -1, 0, area);
        assertThat(world.tile(8, 8).build).isSameAs(build);
        assertThat(build.config()).isEqualTo(0);
        assertThat(build.items.get(Items.silicon)).isEqualTo(7);
        assertThat(build.health).isEqualTo(build.maxHealth / 2);
        assertThat(TileLogger.currentState(8, 8).getConfig()).isEqualTo(0);
    }
    @Test
    void rotationRollbackKeepsUnrelatedProductionState(HeadlessWorld world) {
        var build = factory(world);
        InputHandler.rotateBlock(null, build, true);
        stock(build);
        build.progress = 23;
        build.command = UnitCommand.moveCommand;
        build.commandPos = new Vec2(80, 100);
        service.rollback(null, all, -1, 0, area);
        assertThat(world.tile(8, 8).build).isSameAs(build);
        assertThat(build.rotation).isZero();
        assertThat(build.items.get(Items.silicon)).isEqualTo(7);
        assertThat(build.health).isEqualTo(build.maxHealth / 2);
        assertThat(build.progress).isEqualTo(23);
        assertThat(build.command).isSameAs(UnitCommand.moveCommand);
        assertThat(build.commandPos).isEqualTo(new Vec2(80, 100));
    }
    @Test
    void failedConfigCommitRestoresTheFullBeforeImage(HeadlessWorld world) {
        var build = factory(world);
        InputHandler.tileConfig(null, build, 1);
        stock(build);
        build.progress = 23;
        long sequence = TileLogger.currentState(8, 8).sequence;
        try (var calls = mockStatic(TileLogger.class, CALLS_REAL_METHODS)) {
            calls.when(() -> TileLogger.commitRollback(any(TileState.class)))
                    .thenThrow(new IllegalStateException("commit rejected"));
            service.rollback(null, all, -1, 0, area);
        }
        assertThat(world.tile(8, 8).build).isSameAs(build);
        assertThat(build.config()).isEqualTo(1);
        assertThat(build.progress).isEqualTo(23);
        assertThat(build.items.get(Items.silicon)).isEqualTo(7);
        assertThat(build.health).isEqualTo(build.maxHealth / 2);
        assertThat(TileLogger.currentState(8, 8).sequence).isEqualTo(sequence);
    }
    @Test
    void failedReplacementCommitRestoresResourcesAndFactoryFields(HeadlessWorld world) {
        world.setBlock(8, 8, Blocks.router, Team.sharded);
        service.resetHistory("", true);
        world.setBlock(8, 8, Blocks.groundFactory, Team.sharded);
        var build = (UnitFactoryBuild) world.tile(8, 8).build;
        build.configured(null, 1);
        service.logBuild(build.tile, null, ActionKind.BUILD_COMPLETED);
        stock(build);
        build.progress = 23;
        build.command = UnitCommand.moveCommand;
        build.commandPos = new Vec2(80, 100);
        long sequence = TileLogger.currentState(8, 8).sequence;
        try (var calls = mockStatic(TileLogger.class, CALLS_REAL_METHODS)) {
            calls.when(() -> TileLogger.commitRollback(any(TileState.class)))
                    .thenThrow(new IllegalStateException("commit rejected"));
            service.rollback(null, all, -1, 0, area);
        }
        var restored = (UnitFactoryBuild) world.tile(8, 8).build;
        assertThat(restored.config()).isEqualTo(1);
        assertThat(restored.items.get(Items.silicon)).isEqualTo(7);
        assertThat(restored.health).isEqualTo(restored.maxHealth / 2);
        assertThat(restored.progress).isEqualTo(23);
        assertThat(restored.command).isSameAs(UnitCommand.moveCommand);
        assertThat(restored.commandPos).isEqualTo(new Vec2(80, 100));
        assertThat(TileLogger.currentState(8, 8).sequence).isEqualTo(sequence);
    }
    @Test
    void pickupPlanIsRejectedBeforeEitherTileOrPayloadChanges(HeadlessWorld world) {
        world.setBlock(8, 8, Blocks.router, Team.sharded);
        var original = world.tile(8, 8).build;
        service.resetHistory("", true);
        var carrier = (Payloadc) world.spawnUnit(UnitTypes.mega, Team.sharded, 0, 0);
        carrier.pickup(original);
        long sequence = TileLogger.currentState(8, 8).sequence;
        assertThatThrownBy(() -> service.rollback(null, all, -1, 0, area))
                .isInstanceOf(org.xcore.plugin.cloud.exception.XCoreCommandException.class)
                .hasMessage("tilelogger-error-payload");
        assertThatThrownBy(() -> TileLogger.planRollback(area, "", -1, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Rollback crosses a payload move");
        assertThat(world.tile(8, 8).build).isNull();
        assertThat(carrier.payloads()).hasSize(1);
        assertThat(((mindustry.world.blocks.payloads.BuildPayload) carrier.payloads().first()).build)
                .isSameAs(original);
        assertThat(TileLogger.currentState(8, 8).sequence).isEqualTo(sequence);
    }
    @Test
    void droppedBuildingCannotBeDeletedOrDuplicatedByRollback(HeadlessWorld world) {
        world.setBlock(8, 8, Blocks.router, Team.sharded);
        var original = world.tile(8, 8).build;
        service.resetHistory("", true);
        var unit = world.spawnUnit(UnitTypes.mega, Team.sharded, 0, 0);
        var carrier = (Payloadc) unit;
        carrier.pickup(original);
        unit.set(12 * Vars.tilesize, 12 * Vars.tilesize);
        assertThat(carrier.dropLastPayload()).isTrue();
        assertThat(world.tile(12, 12).build).isSameAs(original);
        long source = TileLogger.currentState(8, 8).sequence;
        long destination = TileLogger.currentState(12, 12).sequence;
        for (Rect selection : new Rect[] {area, new Rect(8, 8, 8, 8), new Rect(12, 12, 12, 12)}) {
            assertThatThrownBy(() -> service.rollback(null, all, -1, 0, selection, true))
                    .isInstanceOf(org.xcore.plugin.cloud.exception.XCoreCommandException.class)
                    .hasMessage("tilelogger-error-payload");
            assertThatThrownBy(() -> service.rollback(null, all, -1, 0, selection))
                    .isInstanceOf(org.xcore.plugin.cloud.exception.XCoreCommandException.class)
                    .hasMessage("tilelogger-error-payload");
        }
        assertThat(world.tile(8, 8).build).isNull();
        assertThat(world.tile(12, 12).build).isSameAs(original);
        assertThat(carrier.payloads()).isEmpty();
        assertThat(TileLogger.currentState(8, 8).sequence).isEqualTo(source);
        assertThat(TileLogger.currentState(12, 12).sequence).isEqualTo(destination);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void failedPowerMutationRestoresReciprocalLinksAndGraph(boolean replacement, HeadlessWorld world) {
        var oldIndexer = Vars.indexer;
        var oldPathfinder = Vars.pathfinder;
        try {
            Vars.indexer = mock(mindustry.ai.BlockIndexer.class);
            Vars.pathfinder = mock(mindustry.ai.Pathfinder.class);
            world.setBlock(8, 8, replacement ? Blocks.router : Blocks.combustionGenerator, Team.sharded);
            world.setBlock(12, 8, Blocks.powerNode, Team.crux);
            world.setBlock(15, 8, Blocks.powerNode, Team.crux);
            var node = world.tile(12, 8).build;
            var other = world.tile(15, 8).build;
            InputHandler.tileConfig(null, node, other.pos());
            service.resetHistory("", true);
            if (replacement) world.setBlock(8, 8, Blocks.combustionGenerator, Team.crux);
            var generator = world.tile(8, 8).build;
            if (!replacement) generator.changeTeam(Team.crux);
            service.logBuild(generator.tile, null, ActionKind.CONFIGURE);
            InputHandler.tileConfig(null, node, generator.pos());
            assertThat(generator.power.graph).isSameAs(node.power.graph).isSameAs(other.power.graph);
            long sequence = TileLogger.currentState(8, 8).sequence;
            try (var calls = mockStatic(TileLogger.class, CALLS_REAL_METHODS)) {
                calls.when(() -> TileLogger.commitRollback(any(TileState.class))).thenAnswer(call -> {
                    if (replacement) assertThat(world.tile(8, 8).block()).isSameAs(Blocks.router);
                    else assertThat(generator.team).isSameAs(Team.sharded);
                    assertThat(node.power.links.contains(generator.pos())).isFalse();
                    throw new IllegalStateException("commit rejected");
                });
                service.rollback(null, all, -1, 0, new Rect(6, 6, 10, 10));
                calls.verify(() -> TileLogger.commitRollback(any(TileState.class)));
            }
            var restored = world.tile(8, 8).build;
            assertThat(restored.team).isSameAs(Team.crux);
            assertThat(restored.power.links.contains(node.pos())).isTrue();
            assertThat(node.power.links.contains(restored.pos())).isTrue();
            assertThat(node.power.links.contains(other.pos())).isTrue();
            assertThat(other.power.links.contains(node.pos())).isTrue();
            assertThat(restored.power.graph).isSameAs(node.power.graph).isSameAs(other.power.graph);
            assertThat(TileLogger.currentState(8, 8).sequence).isEqualTo(sequence);
        } finally {
            Vars.indexer = oldIndexer;
            Vars.pathfinder = oldPathfinder;
        }
    }

    @Test
    void oversizedBeforeImageIsRejectedBeforeChangingTheWorld(HeadlessWorld world) {
        world.setBlock(8, 8, Blocks.router, Team.sharded);
        service.resetHistory("", true);
        var original = spy(world.tile(8, 8).build);
        world.tile(8, 8).build = original;
        InputHandler.rotateBlock(null, original, true);
        doAnswer(call -> {
            call.<arc.util.io.Writes>getArgument(0).b(new byte[4 * 1024 * 1024 + 1]);
            return null;
        }).when(original).writeAll(any(arc.util.io.Writes.class));
        long sequence = TileLogger.currentState(8, 8).sequence;
        service.rollback(null, all, -1, 0, area);
        assertThat(world.tile(8, 8).build).isSameAs(original);
        assertThat(TileLogger.currentState(8, 8).sequence).isEqualTo(sequence);
    }
}
