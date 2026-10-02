package org.xcore.plugin.gamemode.pvp.rating;

import mindustry.game.Team;
import mindustry.gen.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.rating.RatingPolicy;
import org.xcore.plugin.session.ObserverService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MiniPvPMatchTrackerTest {

    @Test
    @DisplayName("tracks player joins, leaves, and calculates participation accurately")
    void trackingLifecycle() {
        MiniPvPMatchTracker tracker = new MiniPvPMatchTracker();
        tracker.startMatch(mock(ObserverService.class));

        Player p1 = mock(Player.class);
        when(p1.uuid()).thenReturn("uuid-1");
        when(p1.plainName()).thenReturn("Player 1");
        when(p1.team()).thenReturn(Team.sharded);

        Player p2 = mock(Player.class);
        when(p2.uuid()).thenReturn("uuid-2");
        when(p2.plainName()).thenReturn("Player 2");
        when(p2.team()).thenReturn(Team.crux);

        tracker.onPlayerJoin(p1);
        tracker.onPlayerJoin(p2);

        assertThat(tracker.participants()).hasSize(2);
        assertThat(tracker.participants().get("uuid-1").teamId()).isEqualTo(Team.sharded.id);

        tracker.onPlayerLeave(p1);
        assertThat(tracker.participants().get("uuid-1").leaveTime()).isGreaterThan(0L);

        // Core of team crux destroyed
        tracker.onTeamEliminated(Team.crux.id, 1);
        assertThat(tracker.getTeamPlacement(Team.crux.id, Team.sharded)).isEqualTo(2);
        assertThat(tracker.getTeamPlacement(Team.sharded.id, Team.sharded)).isEqualTo(1);
    }

    @Test
    @DisplayName("buildRatedTeams exempts late joiners (< 30s) and punishes losing rage-quitters")
    void buildRatedTeams_participationAndExemptions() {
        MiniPvPMatchTracker tracker = new MiniPvPMatchTracker();
        tracker.startMatch(mock(ObserverService.class));

        long started = tracker.startedAt();
        long endedAt = started + 120_000L; // 2 minute match

        // Player 1: full match on winning team
        MiniPvPMatchTracker.ParticipantInfo p1 = new MiniPvPMatchTracker.ParticipantInfo(
                "winner-full", "Winner Full", Team.sharded.id, started, 0L);

        // Player 2: joined 10 seconds before end on losing team (exempt)
        MiniPvPMatchTracker.ParticipantInfo p2 = new MiniPvPMatchTracker.ParticipantInfo(
                "loser-late", "Loser Late", Team.crux.id, endedAt - 10_000L, 0L);

        // Player 3: played 60 seconds on losing team, then disconnected before end (rage quitter)
        MiniPvPMatchTracker.ParticipantInfo p3 = new MiniPvPMatchTracker.ParticipantInfo(
                "loser-quitter", "Loser Quitter", Team.crux.id, started, started + 60_000L);

        tracker.trackParticipant(p1);
        tracker.trackParticipant(p2);
        tracker.trackParticipant(p3);

        RatingPolicy policy = RatingPolicy.teamEloV1(); // min play time 30s
        var ratedTeams = tracker.buildRatedTeams(Team.sharded, endedAt, policy, uuid -> 1000);

        assertThat(ratedTeams).hasSize(2);

        var sharded = ratedTeams.stream().filter(t -> t.teamId() == Team.sharded.id).findFirst().orElseThrow();
        var crux = ratedTeams.stream().filter(t -> t.teamId() == Team.crux.id).findFirst().orElseThrow();

        assertThat(sharded.placement()).isEqualTo(1);
        assertThat(crux.placement()).isEqualTo(2);

        var winnerFull = sharded.members().stream().filter(m -> m.uuid().equals("winner-full")).findFirst().orElseThrow();
        var loserLate = crux.members().stream().filter(m -> m.uuid().equals("loser-late")).findFirst().orElseThrow();
        var loserQuitter = crux.members().stream().filter(m -> m.uuid().equals("loser-quitter")).findFirst().orElseThrow();

        assertThat(winnerFull.participation()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
        // Late loser is exempt (participation = 0.0)
        assertThat(loserLate.participation()).isEqualTo(0.0);
        // Quitter receives full loss weight (participation = 1.0)
        assertThat(loserQuitter.participation()).isEqualTo(1.0);
    }
}
