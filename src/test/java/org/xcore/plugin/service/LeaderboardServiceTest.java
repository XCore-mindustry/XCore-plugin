package org.xcore.plugin.service;

import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import static org.mockito.Mockito.*;

class LeaderboardServiceTest {
    private SessionService sessions;
    private LeaderboardService service;
    private Player player;
    private Session session;

    @BeforeEach
    void setUp() {
        Groups.init();
        player = Player.create();
        player.con = mock(NetConnection.class);
        Groups.player.add(player);
        sessions = mock(SessionService.class);
        session = mock(Session.class);
        session.data = PlayerData.builder().leaderboard(true).build();
        when(sessions.get(player)).thenReturn(session);
        service = new LeaderboardService(sessions);
    }

    @AfterEach
    void tearDown() {
        service.stop();
        Groups.clear();
    }

    @Test
    void refreshesUseTheSamePopupId() {
        try (var calls = mockStatic(Call.class)) {
            service.refresh((builder, target, locale) -> builder.append("first"));
            service.refresh((builder, target, locale) -> builder.append("second"));

            calls.verify(() -> Call.infoPopup(player.con, "first", "xcore-leaderboard", 11f, 8, 0, 2, 50, 0));
            calls.verify(() -> Call.infoPopup(player.con, "second", "xcore-leaderboard", 11f, 8, 0, 2, 50, 0));
        }
    }

    @Test
    void disabledPreferenceDeletesThePopupWithAReliablePacketOnce() {
        try (var calls = mockStatic(Call.class)) {
            service.refresh((builder, target, locale) -> builder.append("scores"));
            session.data.leaderboard = false;

            service.refresh((builder, target, locale) -> { throw new AssertionError("disabled"); });
            service.refresh((builder, target, locale) -> { throw new AssertionError("disabled"); });

            calls.verify(() -> Call.infoPopupReliable(player.con, null, "xcore-leaderboard", 0f, 8, 0, 2, 50, 0));
        }
    }

    @Test
    void hideDeletesThePopupImmediatelyWithoutWaitingForRefresh() {
        try (var calls = mockStatic(Call.class)) {
            service.refresh((builder, target, locale) -> builder.append("scores"));

            service.hide(player);

            calls.verify(() -> Call.infoPopupReliable(player.con, null, "xcore-leaderboard", 0f, 8, 0, 2, 50, 0));
        }
    }

    @Test
    void missingSessionClearsPreviouslyShownScores() {
        try (var calls = mockStatic(Call.class)) {
            service.refresh((builder, target, locale) -> builder.append("scores"));
            when(sessions.get(player)).thenReturn(null);

            service.refresh((builder, target, locale) -> { throw new AssertionError("missing session"); });

            calls.verify(() -> Call.infoPopupReliable(player.con, null, "xcore-leaderboard", 0f, 8, 0, 2, 50, 0));
        }
    }
}
