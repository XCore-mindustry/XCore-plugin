package org.xcore.plugin.cloud;

import com.ospx.flubundle.Bundle;
import jakarta.inject.Provider;
import mindustry.gen.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class XCoreSenderTest {

    @Test
    @DisplayName("session returns session when player is present")
    void session_returnsSessionWhenPlayerPresent() {
        Player player = Player.create();
        MindustrySender handle = new MindustrySender.PlayerSender(player);

        SessionService sessionService = mock(SessionService.class);
        Session session = mock(Session.class);
        when(sessionService.get(player)).thenReturn(session);

        Provider<SessionService> provider = () -> sessionService;
        Bundle bundle = mock(Bundle.class);

        XCoreSender sender = new XCoreSender(handle, bundle, provider);

        assertThat(sender.session()).isSameAs(session);
        assertThat(sender.optionalSession()).contains(session);
    }

    @Test
    @DisplayName("session returns null when player is null")
    void session_returnsNullWhenPlayerIsNull() {
        MindustrySender handle = new MindustrySender.ConsoleSender();

        SessionService sessionService = mock(SessionService.class);
        Provider<SessionService> provider = () -> sessionService;
        Bundle bundle = mock(Bundle.class);

        XCoreSender sender = new XCoreSender(handle, bundle, provider);

        assertThat(sender.session()).isNull();
        assertThat(sender.optionalSession()).isEmpty();
        assertThat(sender.playerData()).isNull();
    }

    @Test
    @DisplayName("withSession executes consumer when session and data are present")
    void withSession_executesConsumerWhenPresent() {
        Player player = Player.create();
        MindustrySender handle = new MindustrySender.PlayerSender(player);

        SessionService sessionService = mock(SessionService.class);
        Session session = mock(Session.class);
        session.data = new PlayerData("uuid-1", true);
        when(sessionService.get(player)).thenReturn(session);

        Provider<SessionService> provider = () -> sessionService;
        Bundle bundle = mock(Bundle.class);

        XCoreSender sender = new XCoreSender(handle, bundle, provider);

        AtomicBoolean executed = new AtomicBoolean(false);
        boolean result = sender.withSession(s -> {
            executed.set(true);
            assertThat(s).isSameAs(session);
        });

        assertThat(result).isTrue();
        assertThat(executed).isTrue();
        assertThat(sender.playerData()).isEqualTo(session.data);
    }

    @Test
    @DisplayName("withSession returns false when session is missing")
    void withSession_returnsFalseWhenMissing() {
        MindustrySender handle = new MindustrySender.ConsoleSender();

        SessionService sessionService = mock(SessionService.class);
        Provider<SessionService> provider = () -> sessionService;
        Bundle bundle = mock(Bundle.class);

        XCoreSender sender = new XCoreSender(handle, bundle, provider);

        AtomicBoolean executed = new AtomicBoolean(false);
        boolean result = sender.withSession(s -> executed.set(true));

        assertThat(result).isFalse();
        assertThat(executed).isFalse();
    }
}
