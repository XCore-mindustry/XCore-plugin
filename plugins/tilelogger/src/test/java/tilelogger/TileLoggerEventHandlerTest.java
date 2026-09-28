package tilelogger;

import arc.Events;
import com.ospx.flubundle.Bundle;
import mindustry.content.UnitTypes;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.net.Administration.PlayerInfo;
import mindustry.world.Tile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xcore.plugin.session.SessionService;
import org.xcore.testkit.fixtures.HeadlessWorld;
import org.xcore.testkit.fixtures.MockPlayer;
import org.xcore.testkit.fixtures.junit.HeadlessWorldExtension;
import org.xcore.testkit.fixtures.junit.WithHeadlessWorld;
import tilelogger.event.TileLoggerEventHandler;
import tilelogger.service.TileLoggerService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.*;

@ExtendWith({HeadlessWorldExtension.class, MockitoExtension.class})
@WithHeadlessWorld(width = 32, height = 32)
class TileLoggerEventHandlerTest {

    @Mock
    private TileLoggerService service;

    @Mock
    private SessionService sessionService;

    private TileLoggerEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TileLoggerEventHandler(service, Bundle.INSTANCE, sessionService);
        handler.init();
    }

    @Test
    @DisplayName("init registers BlockBuildEndEvent: calls logBuild for non-breaking player builds")
    void onBlockBuildEndEventCallsLogBuild(HeadlessWorld world) {
        MockPlayer player = world.addPlayer("Builder", Team.sharded);
        player.spawnUnit(UnitTypes.dagger);
        Tile tile = world.tile(4, 4);
        PlayerInfo info = player.player().getInfo();

        when(service.unitToPlayerInfo(player.unit())).thenReturn(info);

        Events.fire(new EventType.BlockBuildEndEvent(tile, player.unit(), Team.sharded, false, null));

        verify(service).logBuild(tile, info);

        // Verify breaking builds are ignored
        clearInvocations(service);
        Events.fire(new EventType.BlockBuildEndEvent(tile, player.unit(), Team.sharded, true, null));
        verify(service, never()).logBuild(any(), any());
    }

    @Test
    @DisplayName("BlockBuildBeginEvent: calls logDestroy when breaking is true")
    void onBlockBuildBeginEventCallsLogDestroy(HeadlessWorld world) {
        MockPlayer player = world.addPlayer("Breaker", Team.sharded);
        player.spawnUnit(UnitTypes.dagger);
        Tile tile = world.tile(6, 6);
        PlayerInfo info = player.player().getInfo();

        when(service.unitToPlayerInfo(player.unit())).thenReturn(info);

        Events.fire(new EventType.BlockBuildBeginEvent(tile, Team.sharded, player.unit(), true));

        verify(service).logDestroy(tile, info);

        // Verify non-breaking build start does not log destroy
        clearInvocations(service);
        Events.fire(new EventType.BlockBuildBeginEvent(tile, Team.sharded, player.unit(), false));
        verify(service, never()).logDestroy(any(), any());
    }

    @Test
    @DisplayName("TapEvent: calls sendTileHistory when player has inspect mode enabled in PlayerConfig")
    void onTapEventCallsSendTileHistoryWhenInspectModeEnabled(HeadlessWorld world) {
        MockPlayer player = world.addPlayer("Inspector", Team.sharded);
        Tile tile = world.tile(12, 14);

        PlayerConfig config = new PlayerConfig();
        config.historySize = 6;

        when(service.getPlayerConfig(player.player())).thenReturn(config);
        when(service.useAdminTools(player.player())).thenReturn(true);

        Events.fire(new EventType.TapEvent(player.player(), tile));

        short expectedX = (short) tile.centerX();
        short expectedY = (short) tile.centerY();
        verify(service).sendTileHistory(expectedX, expectedY, player.player());
        verify(service).showHistory(player.player(), expectedX, expectedY, 6);

        // When tile is null, no history is requested
        clearInvocations(service);
        Events.fire(new EventType.TapEvent(player.player(), null));
        verify(service, never()).sendTileHistory(anyShort(), anyShort(), any());
    }
}
