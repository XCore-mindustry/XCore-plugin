package org.xcore.plugin.service;

import io.avaje.inject.BeanScope;
import io.avaje.inject.spi.AvajeModule;
import io.avaje.inject.spi.Builder;
import mindustry.gen.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.database.repository.MuteDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.MuteData;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SecurityServiceTest {

    private BeanScope scope;
    private SecurityService securityService;
    private MuteDataRepository muteDataRepository;
    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        scope = BeanScope.builder()
                .modules(new SecurityServiceModule())
                .forTesting()
                .mock(MuteDataRepository.class)
                .mock(SessionService.class)
                .build();

        securityService = scope.get(SecurityService.class);
        muteDataRepository = scope.get(MuteDataRepository.class);
        sessionService = scope.get(SessionService.class);
    }

    @AfterEach
    void tearDown() {
        scope.close();
    }

    @Test
    @DisplayName("isMuted returns false when session is missing")
    void isMuted_returnsFalse_whenSessionMissing() {
        var player = mockPlayer("uuid-1");
        when(sessionService.get(player)).thenReturn(null);

        var result = securityService.isMuted(player);

        assertThat(result).isFalse();
        verifyNoInteractions(muteDataRepository);
    }

    @Test
    @DisplayName("isMuted returns false when mute is missing")
    void isMuted_returnsFalse_whenMuteMissing() {
        var player = mockPlayer("uuid-2");
        var session = mockSessionWithData();
        when(sessionService.get(player)).thenReturn(session);
        when(muteDataRepository.findByUuid("uuid-2")).thenReturn(null);

        var result = securityService.isMuted(player);

        assertThat(result).isFalse();
        verify(muteDataRepository).findByUuid("uuid-2");
    }

    @Test
    @DisplayName("isMuted returns false and deletes mute when mute expired")
    void isMuted_returnsFalse_andDeletesMute_whenMuteExpired() {
        var player = mockPlayer("uuid-3");
        var session = mockSessionWithData();
        when(sessionService.get(player)).thenReturn(session);
        when(muteDataRepository.findByUuid("uuid-3")).thenReturn(
                MuteData.builder()
                        .expireDate(Instant.now().minusSeconds(30))
                        .adminName("admin")
                        .reason("reason")
                        .build()
        );

        var result = securityService.isMuted(player);

        assertThat(result).isFalse();
        verify(muteDataRepository).delete("uuid-3");
    }

    @Test
    @DisplayName("isMuted returns true and sends message when mute active")
    void isMuted_returnsTrue_andSendsMessage_whenMuteActive() {
        var player = mockPlayer("uuid-4");
        var localization = mock(Localization.class);
        var session = mockSessionWithData();
        when(session.locale()).thenReturn(localization);

        when(sessionService.get(player)).thenReturn(session);
        when(muteDataRepository.findByUuid("uuid-4")).thenReturn(
                MuteData.builder()
                        .expireDate(Instant.now().plusSeconds(120))
                        .adminName("admin")
                        .reason("rule violation")
                        .build()
        );

        var result = securityService.isMuted(player);

        assertThat(result).isTrue();
        verify(localization).send(eq("you-are-muted"), anyMap());
    }

    private static Player mockPlayer(String uuid) {
        var player = mock(Player.class);
        when(player.uuid()).thenReturn(uuid);
        return player;
    }

    private static Session mockSessionWithData() {
        var session = mock(Session.class);
        session.data = PlayerData.builder().uuid("session-uuid").build();
        return session;
    }

    private static final class SecurityServiceModule implements AvajeModule {
        @Override
        public Class<?>[] classes() {
            return new Class<?>[]{SecurityService.class};
        }

        @Override
        public void build(Builder builder) {
            if (builder.isBeanAbsent(SecurityService.class)) {
                builder.register(new SecurityService(
                        builder.get(MuteDataRepository.class),
                        () -> builder.get(SessionService.class)
                ));
            }
        }
    }
}
