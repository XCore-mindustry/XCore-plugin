package org.xcore.plugin.session;

import mindustry.game.Team;
import mindustry.gen.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;

import java.util.ArrayList;
import java.util.List;

import static com.ospx.flubundle.Bundle.args;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionServiceTest {

    @Test
    @DisplayName("updateConnectionData updates session and persists nickname with ip")
    void updateConnectionData_updatesSessionAndPersistsNicknameWithIp() {
        PlayerDataRepository playerDataRepository = mock(PlayerDataRepository.class);
        when(playerDataRepository.updateConnectionData("uuid-1", "2.2.2.2", "[red]Renamed[]")).thenReturn(true);

        SessionService service = new SessionService(
                mock(SessionFactory.class),
                playerDataRepository
        );

        Session session = mock(Session.class);
        session.data = PlayerData.builder()
                .uuid("uuid-1")
                .ip("1.1.1.1")
                .nickname("Old")
                .build();

        boolean result = service.updateConnectionData(session, "2.2.2.2", "[red]Renamed[]");

        assertThat(result).isTrue();
        assertThat(session.data.ip).isEqualTo("2.2.2.2");
        assertThat(session.data.nickname).isEqualTo("[red]Renamed[]");
        verify(playerDataRepository).updateConnectionData("uuid-1", "2.2.2.2", "[red]Renamed[]");
    }

    @Test
    @DisplayName("updateAdminStatus updates session and persists admin flags")
    void updateAdminStatus_updatesSessionAndPersistsAdminFlags() {
        PlayerDataRepository playerDataRepository = mock(PlayerDataRepository.class);
        when(playerDataRepository.updateAdminStatus("uuid-1", true, "DISCORD_ROLE")).thenReturn(true);

        SessionService service = new SessionService(
                mock(SessionFactory.class),
                playerDataRepository
        );

        Session session = mock(Session.class);
        session.data = PlayerData.builder()
                .uuid("uuid-1")
                .admin(false)
                .adminSource("NONE")
                .build();

        boolean result = service.updateAdminStatus(session, true, "DISCORD_ROLE");

        assertThat(result).isTrue();
        assertThat(session.data.admin).isTrue();
        assertThat(session.data.adminSource).isEqualTo("DISCORD_ROLE");
        verify(playerDataRepository).updateAdminStatus("uuid-1", true, "DISCORD_ROLE");
    }

    @Test
    @DisplayName("streamCached returns cached sessions")
    void streamCached_returnsCachedSessions() {
        SessionService service = new SessionService(mock(SessionFactory.class), mock(PlayerDataRepository.class));
        Session alpha = session("uuid-1", Team.sharded);
        Session beta = session("uuid-2", Team.crux);

        service.update(alpha);
        service.update(beta);

        List<Session> sessions = service.streamCached().toList();

        assertThat(sessions).containsExactlyInAnyOrder(alpha, beta);
    }

    @Test
    @DisplayName("findByTeam returns only matching online sessions")
    void findByTeam_returnsOnlyMatchingOnlineSessions() {
        SessionService service = new SessionService(mock(SessionFactory.class), mock(PlayerDataRepository.class));
        Session red = session("uuid-red", Team.sharded);
        Session blue = session("uuid-blue", Team.crux);
        Session offline = sessionWithoutPlayer("uuid-offline");

        service.update(red);
        service.update(blue);
        service.update(offline);

        List<Session> sessions = service.findByTeam(Team.sharded);

        assertThat(sessions).containsExactly(red);
    }

    @Test
    @DisplayName("forEachOnline visits only sessions with online player data")
    void forEachOnline_visitsOnlySessionsWithOnlinePlayerData() {
        SessionService service = new SessionService(mock(SessionFactory.class), mock(PlayerDataRepository.class));
        Session red = session("uuid-red", Team.sharded);
        Session offline = sessionWithoutPlayer("uuid-offline");

        service.update(red);
        service.update(offline);

        ArrayList<String> visited = new ArrayList<>();

        service.forEachOnline(session -> visited.add(session.data.uuid));

        assertThat(visited).containsExactly("uuid-red");
    }

    @Test
    @DisplayName("broadcastToTeam sends only to matching team sessions")
    void broadcastToTeam_sendsOnlyToMatchingTeamSessions() {
        SessionService service = new SessionService(mock(SessionFactory.class), mock(PlayerDataRepository.class));
        Session red = session("uuid-red", Team.sharded);
        Session blue = session("uuid-blue", Team.crux);

        service.update(red);
        service.update(blue);

        service.broadcastToTeam(Team.sharded, "team-key", args("value", 1));

        verify(red.locale()).send("team-key", args("value", 1));
        verify(blue.locale(), never()).send("team-key", args("value", 1));
    }

    private Session session(String uuid, Team team) {
        Session session = mock(Session.class);
        Player player = mock(Player.class);
        Localization localization = mock(Localization.class);

        session.data = PlayerData.builder().uuid(uuid).build();
        session.player = player;
        session.localization = localization;

        when(player.team()).thenReturn(team);
        when(session.locale()).thenReturn(localization);
        return session;
    }

    private Session sessionWithoutPlayer(String uuid) {
        Session session = mock(Session.class);
        session.data = PlayerData.builder().uuid(uuid).build();
        return session;
    }
}
