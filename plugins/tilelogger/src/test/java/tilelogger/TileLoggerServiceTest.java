package tilelogger;

import com.ospx.flubundle.Bundle;
import mindustry.content.Blocks;
import mindustry.content.UnitTypes;
import mindustry.game.Team;
import mindustry.net.Administration.PlayerInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.service.FindService;
import org.xcore.plugin.session.SessionService;
import org.xcore.testkit.fixtures.HeadlessWorld;
import org.xcore.testkit.fixtures.MockPlayer;
import org.xcore.testkit.fixtures.junit.HeadlessWorldExtension;
import org.xcore.testkit.fixtures.junit.WithHeadlessWorld;
import tilelogger.service.TileLoggerService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith({HeadlessWorldExtension.class, MockitoExtension.class})
@WithHeadlessWorld(width = 32, height = 32)
class TileLoggerServiceTest {
    @Mock private PlayerDataRepository playerDataRepository;

    @Mock private SessionService sessionService;

    @Mock private FindService findService;

    @Mock private Async async;

    private TileLoggerService service;

    @BeforeEach
    void setUp() {
        service = new TileLoggerService(
                playerDataRepository, sessionService, findService, Bundle.INSTANCE, async);
        service.resetHistory("", true);
    }

    @Test
    @DisplayName("fill places blocks in specified Rect on world and sends success message to player")
    void fillPlacesBlocksInRectAndNotifiesPlayer(HeadlessWorld world) {
        MockPlayer player = world.addPlayer("Builder", Team.sharded);
        Rect rect = new Rect((short) 2, (short) 3, (short) 5, (short) 6);

        service.fill(player.player(), null, Blocks.copperWall, rect);

        for (int x = rect.x1; x <= rect.x2; x++) {
            for (int y = rect.y1; y <= rect.y2; y++) {
                assertThat(world.tile(x, y).block()).isEqualTo(Blocks.copperWall);
                assertThat(world.tile(x, y).team()).isEqualTo(player.team());
            }
        }

        assertThat(player.receivedMessages())
                .anyMatch(msg
                        -> msg.contains("Filled") || msg.contains("tilelogger-fill-success")
                                || msg.contains(Blocks.copperWall.name));
    }

    @Test
    @DisplayName("rollbackBlacklist contains all core blocks")
    void rollbackBlacklistContainsCoreBlocks() {
        assertThat(TileLoggerService.rollbackBlacklist)
                .containsExactlyInAnyOrder(Blocks.coreShard, Blocks.coreFoundation, Blocks.coreNucleus,
                        Blocks.coreCitadel, Blocks.coreBastion, Blocks.coreAcropolis);
    }

    @Test
    @DisplayName("playerConfig returns defaults, tracks updates, and cleans up on remove")
    void playerConfigLifecycle(HeadlessWorld world) {
        MockPlayer player = world.addPlayer("ConfigUser", Team.sharded);

        PlayerConfig defaultConfig = service.getPlayerConfig(player.player());
        assertThat(defaultConfig).isNotNull();
        assertThat(defaultConfig.historySize).isZero();
        assertThat(defaultConfig.selection).isEqualTo(PlayerConfig.Selection.NONE);
        assertThat(defaultConfig.rect).isNotNull();

        defaultConfig.historySize = 10;
        defaultConfig.selection = PlayerConfig.Selection.SECOND_POINT;
        defaultConfig.rect.set((short) 1, (short) 2, (short) 3, (short) 4);

        PlayerConfig updatedConfig = service.getPlayerConfig(player.player());
        assertThat(updatedConfig).isSameAs(defaultConfig);
        assertThat(updatedConfig.historySize).isEqualTo(10);
        assertThat(updatedConfig.selection).isEqualTo(PlayerConfig.Selection.SECOND_POINT);

        service.removePlayerConfig(player.uuid());

        PlayerConfig freshConfig = service.getPlayerConfig(player.player());
        assertThat(freshConfig).isNotSameAs(defaultConfig);
        assertThat(freshConfig.historySize).isZero();
        assertThat(freshConfig.selection).isEqualTo(PlayerConfig.Selection.NONE);

        PlayerConfig nullPlayerConfig = service.getPlayerConfig(null);
        assertThat(nullPlayerConfig).isNotNull();
        assertThat(nullPlayerConfig.historySize).isZero();
    }

    @Test
    @DisplayName("unitToPlayerInfo extracts PlayerInfo from MockPlayer unit")
    void unitToPlayerInfoExtractsPlayerInfo(HeadlessWorld world) {
        MockPlayer player = world.addPlayer("Pilot", Team.sharded);
        player.spawnUnit(UnitTypes.dagger);

        PlayerInfo info = service.unitToPlayerInfo(player.unit());

        assertThat(info).isNotNull();
        assertThat(info.id).isEqualTo(player.uuid());
        assertThat(info).isEqualTo(player.player().getInfo());

        assertThat(service.unitToPlayerInfo(null)).isNull();

        var aiUnit = world.spawnUnit(UnitTypes.flare, Team.crux, 0, 0);
        assertThat(service.unitToPlayerInfo(aiUnit)).isNull();
    }

    @Test
    void previewPreservesHistoryAndCommitChangesOnlyApplicableTiles(HeadlessWorld world) {
        MockPlayer player = world.addPlayer("Builder", Team.sharded);
        var tile = world.tile(5, 5);
        tile.setBlock(Blocks.router, Team.sharded, 0);
        service.resetHistory("", true);
        tile.setBlock(Blocks.copperWall, Team.sharded, 0);
        service.logBuild(tile, player.player().getInfo(), ActionKind.BUILD_COMPLETED);
        var target = new PlayerDescriptor("Builder", player.uuid(), -1);
        var rect = new Rect(0, 0, 31, 31);
        service.rollback(null, target, -1, 0, rect, true);
        assertThat(tile.block()).isEqualTo(Blocks.copperWall);
        assertThat(TileLogger.currentState(5, 5).block).isEqualTo(Blocks.copperWall.id);
        service.rollback(null, target, -1, 0, rect);
        assertThat(tile.block()).isEqualTo(Blocks.router);
        assertThat(TileLogger.currentState(5, 5).block).isEqualTo(Blocks.router.id);
    }

    @Test
    void protectedDestinationDoesNotInvalidateHistory(HeadlessWorld world) {
        MockPlayer player = world.addPlayer("Builder", Team.sharded);
        var tile = world.tile(10, 10);
        tile.setBlock(Blocks.coreShard, Team.sharded, 0);
        service.resetHistory("", true);
        tile.setBlock(Blocks.copperWall, Team.sharded, 0);
        service.logBuild(tile, player.player().getInfo(), ActionKind.BUILD_COMPLETED);
        long sequence = TileLogger.currentState(10, 10).sequence;
        service.rollback(
                null, new PlayerDescriptor("Builder", player.uuid(), -1), -1, 0, new Rect(0, 0, 31, 31));
        assertThat(tile.block()).isEqualTo(Blocks.copperWall);
        assertThat(TileLogger.currentState(10, 10).sequence).isEqualTo(sequence);
    }

    @Test
    void selectionAndLimitsAreValidatedBeforeMutation(HeadlessWorld world) {
        var player = world.addPlayer("Selector", Team.sharded).player();
        assertThatThrownBy(() -> service.selectedRect(player))
                .isInstanceOf(org.xcore.plugin.cloud.exception.XCoreCommandException.class);
        var config = service.getPlayerConfig(player);
        config.selection = PlayerConfig.Selection.READY;
        config.session = service.generation();
        config.rect = new Rect(1, 1, 3, 3);
        assertThat(service.selectedRect(player).area()).isEqualTo(9);
        service.invalidateWorld();
        assertThatThrownBy(() -> service.selectedRect(player))
                .isInstanceOf(org.xcore.plugin.cloud.exception.XCoreCommandException.class);
        for (long size : new long[] {0, -1, 101, Long.MAX_VALUE})
            assertThatThrownBy(() -> TileLoggerService.checkedHistoryLimit(size))
                    .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> TileLoggerService.checkedRect(new Rect(-1, 0, 3, 3)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void identicalUnknownIdentitiesShareOneLookupAndCacheAbsence() {
        var lookup = new java.util.concurrent.CompletableFuture<org.xcore.plugin.model.PlayerData>();
        when(playerDataRepository.findByUuidAsync("missing")).thenReturn(lookup);
        var first = service.findPlayerUuidAsync("missing");
        assertThat(service.findPlayerUuidAsync("missing")).isSameAs(first);
        lookup.complete(null);
        assertThat(first.join()).isNull();
        assertThat(service.findPlayerUuidAsync("missing").join()).isNull();
        verify(playerDataRepository, times(1)).findByUuidAsync("missing");
        verify(playerDataRepository, never()).findByUuid(anyString());
    }

    @Test
    void missingDatabaseNamesStillRenderWithoutBlockingFallback(HeadlessWorld world) {
        var player = world.addPlayer("Viewer", Team.sharded);
        var direct = new Async(mock(org.xcore.plugin.concurrent.StorageExecutor.class), Runnable::run);
        service = new TileLoggerService(
                playerDataRepository, sessionService, findService, Bundle.INSTANCE, direct);
        when(playerDataRepository.findByUuidAsync("missing"))
                .thenReturn(java.util.concurrent.CompletableFuture.failedFuture(
                        new IllegalStateException("offline")));
        TileLogger.record(4, 4, "missing", 1, Blocks.router.id, 0, ConfigCodec.encode(null),
                ActionKind.BUILD_COMPLETED);
        service.showHistory(player.player(), 4, 4, 10);
        assertThat(player.receivedMessages()).anyMatch(message -> message.contains("missing"));
        verify(playerDataRepository, never()).findByUuid(anyString());
        verify(sessionService, never()).getOrLoadFromDb(anyString());
    }

    @Test
    void historyPacketsCarryVersionAndHonorTheEncodedByteBudget(HeadlessWorld world) {
        var player = world.addPlayer("Viewer", Team.sharded);
        var direct = new Async(mock(org.xcore.plugin.concurrent.StorageExecutor.class), Runnable::run);
        service = new TileLoggerService(
                playerDataRepository, sessionService, findService, Bundle.INSTANCE, direct);
        for (int i = 0; i < 40; i++)
            TileLogger.record(4, 4, "", 1, Blocks.router.id, 0, ConfigCodec.encode("🧱".repeat(300)),
                    ActionKind.CONFIGURE);
        service.registerPackets();
        try {
            mindustry.core.NetServer.serverPacketReliable(player.player(), "tilelogger_hello", "2");
            var request = new HistoryRequest();
            request.requestId = 42;
            request.x = 4;
            request.y = 4;
            mindustry.core.NetServer.serverPacketReliable(
                    player.player(), "tilelogger_history_request", HistoryWire.write(request));
            var packet = player.sentPackets(mindustry.gen.ClientPacketReliableCallPacket.class)
                                 .stream()
                                 .filter(p -> p.type.equals("tilelogger_history_v2"))
                                 .findFirst()
                                 .orElseThrow();
            assertThat(packet.contents.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                    .isLessThanOrEqualTo(8192);
            var page = HistoryWire.read(HistoryPage.class, packet.contents);
            assertThat(page.version).isEqualTo(2);
            assertThat(page.requestId).isEqualTo(42);
            assertThat(page.entries).isNotEmpty();
            assertThat(page.truncated).isTrue();
            assertThat(page.nextCursor).isNotBlank();
        } finally {
            service.unregisterPackets();
        }
    }
}
