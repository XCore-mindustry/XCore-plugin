package org.xcore.plugin.service.moderation;

import mindustry.gen.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.permission.Actor;
import org.xcore.plugin.session.Session;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModerationActorTest {

    private static Session session(String plainName, String discordId) {
        Session session = mock(Session.class);
        session.player = mock(Player.class);
        when(session.player.plainName()).thenReturn(plainName);
        session.data = new PlayerData();
        session.data.discordId = discordId;
        return session;
    }

    @Test
    @DisplayName("A player is recorded by name and Discord id")
    void player() {
        var actor = ModerationActor.of(new Actor.PlayerActor("uuid-1", session("Alice", "42")));

        assertThat(actor).isEqualTo(new ModerationActor("Alice", "42"));
    }

    @Test
    @DisplayName("The local and the relayed console are recorded apart")
    void consoles() {
        assertThat(ModerationActor.of(Actor.LOCAL_CONSOLE)).isEqualTo(ModerationActor.CONSOLE);
        assertThat(ModerationActor.of(new Actor.RemoteConsole("hub")).name()).isEqualTo("remote-console@hub");
    }

    @Test
    @DisplayName("A player whose name reads as a console, or is empty, is recorded by uuid")
    void reservedPlayerNames() {
        assertThat(ModerationActor.of(new Actor.PlayerActor("uuid-1", session("Console", null))).name()).isEqualTo("uuid-1");
        assertThat(ModerationActor.of(new Actor.PlayerActor("uuid-1", session("remote-console@hub", null))).name()).isEqualTo("uuid-1");
        assertThat(ModerationActor.of(new Actor.PlayerActor("uuid-1", session(" ", null))).name()).isEqualTo("uuid-1");
        assertThat(ModerationActor.of(new Actor.PlayerActor("uuid-1", null)).name()).isEqualTo("uuid-1");
    }

    @Test
    @DisplayName("An unknown initiator is an error, never the console")
    void noFallback() {
        assertThatThrownBy(() -> ModerationActor.of((Actor) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ModerationActor.of(" ", "42")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MuteCommand.byId(1, null, Duration.ofMinutes(1))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> BanCommand.byId(1, null, Duration.ofMinutes(1))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> UnmuteCommand.byId(1, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> UnbanCommand.byId(1, null)).isInstanceOf(NullPointerException.class);
    }
}
