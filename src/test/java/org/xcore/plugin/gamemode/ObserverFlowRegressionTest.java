package org.xcore.plugin.gamemode;

import arc.Core;
import arc.Settings;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.core.NetServer;
import mindustry.game.Rules;
import mindustry.game.Team;
import mindustry.gen.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.gamemode.hexed.MiniHexedService;
import org.xcore.plugin.service.GameDataService;
import org.xcore.plugin.service.LeaderboardService;
import org.xcore.plugin.service.MapStatsService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.service.TopMenuCacheService;
import org.xcore.plugin.service.network.RedisObserverStateStore;
import org.xcore.plugin.session.ObserverService;
import org.xcore.plugin.session.SessionFactory;
import org.xcore.plugin.session.SessionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObserverFlowRegressionTest {

    private NetServer previousNetServer;
    private Settings previousSettings;
    private GameState previousState;

    @BeforeEach
    void setUp() {
        previousNetServer = Vars.netServer;
        previousSettings = Core.settings;
        previousState = Vars.state;

        Core.settings = mock(Settings.class);
        Vars.state = new GameState();
        Vars.state.rules = new Rules();
        Team.sharded.data().players.clear();
    }

    @AfterEach
    void tearDown() {
        Team.sharded.data().players.clear();
        Vars.netServer = previousNetServer;
        Core.settings = previousSettings;
        Vars.state = previousState;
    }

    @Test
    @DisplayName("restore keeps observer off derelict team and assigns observer team")
    void restore_keepsObserverOffDerelictTeam() {
        SessionService sessionService = mock(SessionService.class);
        RedisObserverStateStore observerStateStore = mock(RedisObserverStateStore.class);

        when(observerStateStore.get("uuid-1")).thenReturn(
                new RedisObserverStateStore.CachedObserverState(Team.crux.id, System.currentTimeMillis())
        );
        when(observerStateStore.resolveReturnTeam(org.mockito.ArgumentMatchers.any())).thenReturn(Team.crux);

        ObserverService observerService = new ObserverService(sessionService, observerStateStore);

        Player player = Player.create();
        player.con = mock(mindustry.net.NetConnection.class);
        player.con.uuid = "uuid-1";

        observerService.restore(player);

        assertThat(player.team()).isNotEqualTo(Team.derelict);
        assertThat(player.team().id).isEqualTo(255);
    }

    @Test
    @DisplayName("hexed killTeam routes eliminated players through observer service")
    void hexedKillTeam_routesEliminatedPlayersThroughObserverService() {
        Vars.state.rules.waves = true;
        Vars.state.rules.waveTeam = Team.sharded;

        SessionService sessionService = mock(SessionService.class);
        ObserverService observerService = mock(ObserverService.class);
        MiniHexedService service = new MiniHexedService(
                mock(TomlXcoreConfig.class),
                sessionService,
                mock(PlayerDataRepository.class),
                mock(NetworkService.class),
                mock(com.ospx.flubundle.Bundle.class),
                mock(LeaderboardService.class),
                mock(PlayerDisplayService.class),
                mock(GameDataService.class),
                mock(MapStatsService.class),
                mock(TopMenuCacheService.class),
                observerService
        );

        Player player = mock(Player.class);
        when(player.coloredName()).thenReturn("[green]ObserverTarget[]");
        Team.sharded.data().players.add(player);

        service.killTeam(Team.sharded);

        verify(sessionService).broadcast(eq("hexed-eliminated"), anyMap());
        verify(observerService).enter(player);
    }
}