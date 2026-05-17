package org.xcore.plugin.session;

import com.ospx.flubundle.Bundle;
import mindustry.game.Team;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.ui.MenuService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SessionObserverStateTest {

    @Test
    @DisplayName("beginObserving stores observer state and return team")
    void beginObserving_storesObserverStateAndReturnTeam() {
        Session session = session();

        session.beginObserving(Team.crux);

        assertThat(session.observing()).isTrue();
        assertThat(session.observerReturnTeam()).isEqualTo(Team.crux);
    }

    @Test
    @DisplayName("endObserving clears observer state and returns prior team")
    void endObserving_clearsObserverStateAndReturnsPriorTeam() {
        Session session = session();
        session.beginObserving(Team.sharded);

        Team returnTeam = session.endObserving();

        assertThat(returnTeam).isEqualTo(Team.sharded);
        assertThat(session.observing()).isFalse();
        assertThat(session.observerReturnTeam()).isNull();
    }

    @Test
    @DisplayName("observing state remains false until beginObserving is called")
    void observingState_remainsFalseUntilBeginObservingIsCalled() {
        Session session = session();

        boolean observing = session.observing();

        assertThat(observing).isFalse();
        assertThat(session.observerReturnTeam()).isNull();
    }

    @Test
    @DisplayName("endObserving returns null when observer session had no return team")
    void endObserving_returnsNullWhenObserverSessionHadNoReturnTeam() {
        Session session = session();
        session.beginObserving(null);

        Team returnTeam = session.endObserving();

        assertThat(returnTeam).isNull();
        assertThat(session.observing()).isFalse();
        assertThat(session.observerReturnTeam()).isNull();
    }

    private Session session() {
        Player player = Player.create();
        player.con = mock(NetConnection.class);
        PlayerData data = new PlayerData("uuid-1", true);
        return new Session(
                new GlobalConfig(),
                mock(Bundle.class),
                mock(MenuService.class),
                mock(PlayerDataRepository.class),
                player,
                data
        );
    }
}
