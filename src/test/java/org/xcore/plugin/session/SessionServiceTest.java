package org.xcore.plugin.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
        when(playerDataRepository.updateAdminStatus("uuid-1", true, false)).thenReturn(true);

        SessionService service = new SessionService(
                mock(SessionFactory.class),
                playerDataRepository
        );

        Session session = mock(Session.class);
        session.data = PlayerData.builder()
                .uuid("uuid-1")
                .admin(false)
                .adminConfirmed(true)
                .build();

        boolean result = service.updateAdminStatus(session, true, false);

        assertThat(result).isTrue();
        assertThat(session.data.admin).isTrue();
        assertThat(session.data.adminConfirmed).isFalse();
        verify(playerDataRepository).updateAdminStatus("uuid-1", true, false);
    }
}
