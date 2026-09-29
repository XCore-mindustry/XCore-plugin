package org.xcore.plugin.service;

import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.entities.EntityGroup;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AnnouncementServiceTest {

    private GameState originalState;
    private EntityGroup<Player> originalPlayers;

    @BeforeEach
    void setUp() {
        originalState = Vars.state;
        originalPlayers = Groups.player;

        Vars.state = new GameState();
        Vars.state.set(GameState.State.playing);

        Groups.player = new EntityGroup<>(Player.class, false, false);
    }

    @AfterEach
    void tearDown() {
        Vars.state = originalState;
        Groups.player = originalPlayers;
    }

    @Test
    @DisplayName("broadcastNext skips when disabled in config")
    void broadcastNext_skipsWhenDisabled() {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.announcements.enabled = false;

        SessionService sessionService = mock(SessionService.class);
        AnnouncementService service = new AnnouncementService(config, sessionService);

        String result = service.broadcastNext();
        assertThat(result).isNull();
        verifyNoInteractions(sessionService);
    }

    @Test
    @DisplayName("broadcastNext skips when no players online")
    void broadcastNext_skipsWhenNoPlayers() {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.announcements.enabled = true;

        SessionService sessionService = mock(SessionService.class);
        AnnouncementService service = new AnnouncementService(config, sessionService);

        String result = service.broadcastNext();
        assertThat(result).isNull();
        verifyNoInteractions(sessionService);
    }

    @Test
    @DisplayName("broadcastNext skips when not in game state")
    void broadcastNext_skipsWhenNotInGame() {
        Vars.state.set(GameState.State.menu);

        TomlXcoreConfig config = new TomlXcoreConfig();
        SessionService sessionService = mock(SessionService.class);
        AnnouncementService service = new AnnouncementService(config, sessionService);

        Player player = mock(Player.class);
        player.con = mock(NetConnection.class);
        Groups.player.add(player);

        String result = service.broadcastNext();
        assertThat(result).isNull();
        verifyNoInteractions(sessionService);
    }

    @Test
    @DisplayName("broadcastNext rotates across configured messages sequentially")
    void broadcastNext_rotatesAcrossMessages() {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.announcements.messages = List.of("msg-1", "msg-2", "msg-3");

        Player player = mock(Player.class);
        player.con = mock(NetConnection.class);
        Groups.player.add(player);

        Session session = mock(Session.class);
        session.player = player;
        Localization locale = mock(Localization.class);
        when(session.locale()).thenReturn(locale);

        SessionService sessionService = mock(SessionService.class);
        when(sessionService.getAllCachedSnapshot()).thenReturn(List.of(session));

        AnnouncementService service = new AnnouncementService(config, sessionService);

        assertThat(service.broadcastNext()).isEqualTo("msg-1");
        verify(locale).send("msg-1");

        assertThat(service.broadcastNext()).isEqualTo("msg-2");
        verify(locale).send("msg-2");

        assertThat(service.broadcastNext()).isEqualTo("msg-3");
        verify(locale).send("msg-3");

        assertThat(service.broadcastNext()).isEqualTo("msg-1");
        verify(locale, times(2)).send("msg-1");
    }

    @Test
    @DisplayName("broadcast sends specific key to all connected sessions")
    void broadcast_sendsToAllSessions() {
        TomlXcoreConfig config = new TomlXcoreConfig();
        SessionService sessionService = mock(SessionService.class);

        Player p1 = mock(Player.class);
        p1.con = mock(NetConnection.class);
        Session s1 = mock(Session.class);
        s1.player = p1;
        Localization loc1 = mock(Localization.class);
        when(s1.locale()).thenReturn(loc1);

        Player p2 = mock(Player.class);
        p2.con = mock(NetConnection.class);
        Session s2 = mock(Session.class);
        s2.player = p2;
        Localization loc2 = mock(Localization.class);
        when(s2.locale()).thenReturn(loc2);

        when(sessionService.getAllCachedSnapshot()).thenReturn(List.of(s1, s2));

        AnnouncementService service = new AnnouncementService(config, sessionService);
        int delivered = service.broadcast("announcement-hub");

        assertThat(delivered).isEqualTo(2);
        verify(loc1).send("announcement-hub");
        verify(loc2).send("announcement-hub");
    }
}
