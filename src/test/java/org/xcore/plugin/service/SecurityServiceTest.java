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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    @DisplayName("checkMute returns false when mute is missing")
    void checkMute_returnsFalse_whenMuteMissing() {
        var player = mockPlayer("uuid-1");
        when(muteDataRepository.findByUuid("uuid-1")).thenReturn(null);

        var result = securityService.checkMute(player);

        assertThat(result.muted()).isFalse();
        assertThat(result.muteData()).isNull();
        assertThat(result.remaining()).isEqualTo(java.time.Duration.ZERO);
        verify(muteDataRepository).findByUuid("uuid-1");
    }

    @Test
    @DisplayName("checkMute returns false and deletes when mute expired")
    void checkMute_returnsFalse_andDeletesMute_whenMuteExpired() {
        var player = mockPlayer("uuid-2");
        when(muteDataRepository.findByUuid("uuid-2")).thenReturn(
                MuteData.builder()
                        .expireDate(Instant.now().minusSeconds(30))
                        .adminName("admin")
                        .reason("reason")
                        .build()
        );

        var result = securityService.checkMute(player);

        assertThat(result.muted()).isFalse();
        assertThat(result.muteData()).isNull();
        verify(muteDataRepository).delete("uuid-2");
    }

    @Test
    @DisplayName("checkMute returns true with data when mute active")
    void checkMute_returnsTrue_withData_whenMuteActive() {
        var player = mockPlayer("uuid-3");
        var expireDate = Instant.now().plusSeconds(120);
        when(muteDataRepository.findByUuid("uuid-3")).thenReturn(
                MuteData.builder()
                        .expireDate(expireDate)
                        .adminName("admin")
                        .reason("rule violation")
                        .build()
        );

        var result = securityService.checkMute(player);

        assertThat(result.muted()).isTrue();
        assertThat(result.muteData()).isNotNull();
        assertThat(result.muteData().adminName).isEqualTo("admin");
        assertThat(result.muteData().reason).isEqualTo("rule violation");
        assertThat(result.remaining()).isPositive();
    }

    @Test
    @DisplayName("isMuted returns false when mute is missing")
    void isMuted_returnsFalse_whenMuteMissing() {
        var player = mockPlayer("uuid-4");
        when(muteDataRepository.findByUuid("uuid-4")).thenReturn(null);

        var result = securityService.isMuted(player);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isMuted returns true when mute active but does not send message")
    void isMuted_returnsTrue_andNoMessage_whenMuteActive() {
        var player = mockPlayer("uuid-5");
        var localization = mock(Localization.class);
        var session = mockSessionWithData();
        when(session.locale()).thenReturn(localization);
        when(sessionService.get(player)).thenReturn(session);
        when(muteDataRepository.findByUuid("uuid-5")).thenReturn(
                MuteData.builder()
                        .expireDate(Instant.now().plusSeconds(120))
                        .adminName("admin")
                        .reason("rule violation")
                        .build()
        );

        var result = securityService.isMuted(player);

        assertThat(result).isTrue();
        verify(localization, never()).send(eq("you-are-muted"), anyMap());
    }

    @Test
    @DisplayName("checkAndNotifyMuted returns false when not muted")
    void checkAndNotifyMuted_returnsFalse_whenNotMuted() {
        var player = mockPlayer("uuid-6");
        when(muteDataRepository.findByUuid("uuid-6")).thenReturn(null);

        var result = securityService.checkAndNotifyMuted(player);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("checkAndNotifyMuted returns true and sends message when muted and session present")
    void checkAndNotifyMuted_returnsTrue_andSendsMessage_whenMuted_andSessionPresent() {
        var player = mockPlayer("uuid-7");
        var localization = mock(Localization.class);
        var session = mockSessionWithData();
        when(session.locale()).thenReturn(localization);
        when(sessionService.get(player)).thenReturn(session);
        when(muteDataRepository.findByUuid("uuid-7")).thenReturn(
                MuteData.builder()
                        .expireDate(Instant.now().plusSeconds(120))
                        .adminName("admin")
                        .reason("rule violation")
                        .build()
        );

        var result = securityService.checkAndNotifyMuted(player);

        assertThat(result).isTrue();
        verify(localization).send(eq("you-are-muted"), argThat(args -> hasMuteMessageArgs(args, "admin", "rule violation")));
    }

    @Test
    @DisplayName("checkAndNotifyMuted returns true but does not send message when muted and session missing")
    void checkAndNotifyMuted_returnsTrue_andNoMessage_whenMuted_andSessionMissing() {
        var player = mockPlayer("uuid-8");
        when(sessionService.get(player)).thenReturn(null);
        when(muteDataRepository.findByUuid("uuid-8")).thenReturn(
                MuteData.builder()
                        .expireDate(Instant.now().plusSeconds(120))
                        .adminName("admin")
                        .reason("rule violation")
                        .build()
        );

        var result = securityService.checkAndNotifyMuted(player);

        assertThat(result).isTrue();
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

    private static boolean hasMuteMessageArgs(Map<String, Object> args, String adminName, String reason) {
        return adminName.equals(args.get("adminName"))
                && reason.equals(args.get("reason"))
                && args.get("days") instanceof Long
                && args.get("hours") instanceof Integer
                && args.get("minutes") instanceof Integer
                && args.get("seconds") instanceof Integer;
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
