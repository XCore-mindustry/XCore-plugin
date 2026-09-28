package org.xcore.plugin.gamemode;

import mindustry.game.Team;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xcore.plugin.service.network.RedisObserverStateStore;
import org.xcore.plugin.session.ObserverService;
import org.xcore.plugin.session.SessionService;
import org.xcore.testkit.fixtures.HeadlessWorld;
import org.xcore.testkit.fixtures.MockPlayer;
import org.xcore.testkit.fixtures.junit.HeadlessWorldExtension;
import org.xcore.testkit.fixtures.junit.WithHeadlessWorld;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(HeadlessWorldExtension.class)
@WithHeadlessWorld(width = 32, height = 32)
class ObserverFlowRegressionTest {

    @Test
    @DisplayName("restore keeps observer off derelict team and assigns observer team")
    void restore_keepsObserverOffDerelictTeam(HeadlessWorld world) {
        SessionService sessionService = mock(SessionService.class);
        RedisObserverStateStore observerStateStore = mock(RedisObserverStateStore.class);

        when(observerStateStore.get("uuid-1")).thenReturn(
                new RedisObserverStateStore.CachedObserverState(Team.crux.id, System.currentTimeMillis())
        );
        when(observerStateStore.resolveReturnTeam(any())).thenReturn(Team.crux);

        ObserverService observerService = new ObserverService(sessionService, observerStateStore);

        MockPlayer player = world.addPlayer(b -> b.uuid("uuid-1").name("ObserverTester"));

        observerService.restore(player.player());

        assertThat(player.team()).isNotEqualTo(Team.derelict);
        assertThat(player.team().id).isEqualTo(255);
    }
}
