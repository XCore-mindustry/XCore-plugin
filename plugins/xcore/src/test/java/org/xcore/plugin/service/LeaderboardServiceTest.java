package org.xcore.plugin.service;

import mindustry.game.Team;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.testkit.fixtures.HeadlessWorld;
import org.xcore.testkit.fixtures.MockNetConnection.InfoPopup;
import org.xcore.testkit.fixtures.MockPlayer;
import org.xcore.testkit.fixtures.junit.WithHeadlessWorld;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.xcore.testkit.fixtures.MockNetConnection.PopupVariant.KEYED;
import static org.xcore.testkit.fixtures.MockNetConnection.PopupVariant.KEYED_RELIABLE;

@WithHeadlessWorld
class LeaderboardServiceTest {
    private static final InfoPopup DELETE =
            new InfoPopup(null, 0f, 8, "xcore-leaderboard", KEYED_RELIABLE, 0, 2, 50, 0);

    private SessionService sessions;
    private LeaderboardService service;
    private MockPlayer player;
    private Session session;

    @BeforeEach
    void setUp(HeadlessWorld world) {
        player = world.addPlayer("Scorer", Team.sharded);
        sessions = mock(SessionService.class);
        session = mock(Session.class);
        session.data = PlayerData.builder().leaderboard(true).build();
        when(sessions.get(player.player())).thenReturn(session);
        service = new LeaderboardService(sessions);
    }

    @AfterEach
    void tearDown() {
        service.stop();
    }

    private static InfoPopup shown(String text) {
        return new InfoPopup(text, 11f, 8, "xcore-leaderboard", KEYED, 0, 2, 50, 0);
    }

    @Test
    void refreshesUseTheSamePopupId() {
        service.refresh((builder, target, locale) -> builder.append("first"));
        service.refresh((builder, target, locale) -> builder.append("second"));

        assertThat(player.con().infoPopups()).containsExactly(shown("first"), shown("second"));
    }

    @Test
    void disabledPreferenceDeletesThePopupWithAReliablePacketOnce() {
        service.refresh((builder, target, locale) -> builder.append("scores"));
        session.data.leaderboard = false;

        service.refresh((builder, target, locale) -> { throw new AssertionError("disabled"); });
        service.refresh((builder, target, locale) -> { throw new AssertionError("disabled"); });

        assertThat(player.con().infoPopups()).containsExactly(shown("scores"), DELETE);
    }

    @Test
    void hideDeletesThePopupImmediatelyWithoutWaitingForRefresh() {
        service.refresh((builder, target, locale) -> builder.append("scores"));

        service.hide(player.player());

        assertThat(player.con().lastInfoPopup()).isEqualTo(DELETE);
    }

    @Test
    void missingSessionClearsPreviouslyShownScores() {
        service.refresh((builder, target, locale) -> builder.append("scores"));
        when(sessions.get(player.player())).thenReturn(null);

        service.refresh((builder, target, locale) -> { throw new AssertionError("missing session"); });

        assertThat(player.con().infoPopups()).containsExactly(shown("scores"), DELETE);
    }

    @Test
    void startRefreshesAtOnceThenEveryIntervalUntilStopped(HeadlessWorld world) {
        var timer = world.timer();
        int[] ticks = {0};
        service.start((builder, target, locale) -> builder.append("tick ").append(++ticks[0]));

        timer.runDue();
        timer.advance(LeaderboardService.REFRESH_SECONDS * 2);
        assertThat(player.infoPopups()).containsExactly("tick 1", "tick 2", "tick 3");

        service.stop();
        timer.advance(60f);
        assertThat(ticks[0]).isEqualTo(3);
    }
}
