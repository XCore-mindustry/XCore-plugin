package org.xcore.plugin.rating.season;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.rating.ladder.LadderStanding;
import org.xcore.plugin.rating.ladder.LadderStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Turns a finished season's leaderboard into its permanent record. */
@Singleton
public class SeasonFinalizer {
    private final LadderStore standings;
    private final PlayerDataRepository players;
    private final SeasonSchedule schedule;

    @Inject
    public SeasonFinalizer(LadderStore standings, PlayerDataRepository players, SeasonSchedule schedule) {
        this.standings = standings;
        this.players = players;
        this.schedule = schedule;
    }

    /**
     * Blocking. Freezes every standing's final rank and snapshots the podium. Safe to repeat.
     *
     * @return the season as it should be archived
     */
    public Season archive(Season season) {
        int participants = standings.assignFinalRanks(season.ladderId(), season.number());
        List<LadderStanding> leaders = standings.leaders(season.ladderId(), season.number(),
                schedule.podiumMinMatches(), schedule.podiumSize());

        Map<String, PlayerData> profiles = new HashMap<>();
        for (PlayerData profile : players.findByUuids(leaders.stream().map(LadderStanding::uuid).toList())) {
            profiles.put(profile.uuid, profile);
        }

        List<SeasonPodiumEntry> podium = new ArrayList<>(leaders.size());
        for (LadderStanding leader : leaders) {
            PlayerData profile = profiles.get(leader.uuid());
            podium.add(new SeasonPodiumEntry(
                    podium.size() + 1,
                    leader.uuid(),
                    profile != null ? profile.pid : -1,
                    profile != null && profile.nickname != null ? profile.nickname : "Unknown",
                    leader.rating(),
                    leader.league().name(),
                    leader.matches(),
                    leader.wins(),
                    profile != null && profile.discordId != null ? profile.discordId : "",
                    profile != null && profile.discordUsername != null ? profile.discordUsername : ""));
        }
        return season.archive(podium, new SeasonSummary(participants, season.matches()));
    }
}
