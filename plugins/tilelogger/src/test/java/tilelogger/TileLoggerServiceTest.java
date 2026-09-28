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

@ExtendWith({HeadlessWorldExtension.class, MockitoExtension.class})
@WithHeadlessWorld(width = 32, height = 32)
class TileLoggerServiceTest {

    @Mock
    private PlayerDataRepository playerDataRepository;

    @Mock
    private SessionService sessionService;

    @Mock
    private FindService findService;

    @Mock
    private Async async;

    private TileLoggerService service;

    @BeforeEach
    void setUp() {
        service = new TileLoggerService(
                playerDataRepository,
                sessionService,
                findService,
                Bundle.INSTANCE,
                async
        );
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
                .anyMatch(msg -> msg.contains("Filled") || msg.contains("tilelogger-fill-success") || msg.contains(Blocks.copperWall.name));
    }

    @Test
    @DisplayName("rollbackBlacklist contains all core blocks")
    void rollbackBlacklistContainsCoreBlocks() {
        assertThat(TileLoggerService.rollbackBlacklist)
                .containsExactlyInAnyOrder(
                        Blocks.coreShard,
                        Blocks.coreFoundation,
                        Blocks.coreNucleus,
                        Blocks.coreCitadel,
                        Blocks.coreBastion,
                        Blocks.coreAcropolis
                );
    }

    @Test
    @DisplayName("playerConfig returns defaults, tracks updates, and cleans up on remove")
    void playerConfigLifecycle(HeadlessWorld world) {
        MockPlayer player = world.addPlayer("ConfigUser", Team.sharded);

        PlayerConfig defaultConfig = service.getPlayerConfig(player.player());
        assertThat(defaultConfig).isNotNull();
        assertThat(defaultConfig.historySize).isZero();
        assertThat(defaultConfig.selectState).isZero();
        assertThat(defaultConfig.rect).isNotNull();

        defaultConfig.historySize = 10;
        defaultConfig.selectState = 2;
        defaultConfig.rect.set((short) 1, (short) 2, (short) 3, (short) 4);

        PlayerConfig updatedConfig = service.getPlayerConfig(player.player());
        assertThat(updatedConfig).isSameAs(defaultConfig);
        assertThat(updatedConfig.historySize).isEqualTo(10);
        assertThat(updatedConfig.selectState).isEqualTo(2);

        service.removePlayerConfig(player.uuid());

        PlayerConfig freshConfig = service.getPlayerConfig(player.player());
        assertThat(freshConfig).isNotSameAs(defaultConfig);
        assertThat(freshConfig.historySize).isZero();
        assertThat(freshConfig.selectState).isZero();

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
}
