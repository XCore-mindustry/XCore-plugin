package org.xcore.plugin.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.database.repository.GameDataRepository;
import org.xcore.plugin.model.*;

import java.util.HashMap;
import java.util.Map;

@Singleton
public class GameDataService {

    private final GameDataRepository gameDataRepository;
    private final PlayerSessionService playerSessionService;

    private GameData currentBag;

    private final Map<String, PlayerGameStats> playerStatsCache = new HashMap<>();

    @Inject
    public GameDataService(GameDataRepository gameDataRepository, PlayerSessionService playerSessionService) {
        this.gameDataRepository = gameDataRepository;
        this.playerSessionService = playerSessionService;
    }

    public void startNewGame(MapData map, String mode, EventData event) {
        var now = System.currentTimeMillis();

        if (map == null) return;

        currentBag = new GameData(map.id, mode);
        if (event != null) {
            currentBag.setEvent(event.id).setEvent(true);
        }
        currentBag.setStartGameTime(now);
        playerStatsCache.clear();
    }

    public void addPlayer(Player player) {
        var now = System.currentTimeMillis();

        if (currentBag == null) return;

        PlayerGameStats stats = PlayerGameStats.builder()
                .nickname(player.plainName())
                .uuid(player.uuid())
                .joinTime(now )
                .initialTeam(player.team().name)
                .finalTeam(player.team().name)
                .build();

        stats.teams.add(TeamGameData.builder().team(player.team().name).joinTime(now ).build());

        playerStatsCache.put(stats.uuid, stats);
        currentBag.getPlayerStats().add(stats);
    }

    public void recordLeave(Player player) {
        var now = System.currentTimeMillis();
        PlayerGameStats stats = playerStatsCache.get(player.uuid());

        if (stats != null) {
            stats.setLeaveTime(now);
            stats.finalTeam = player.team().name;

            if (!stats.teams.isEmpty()) {
                stats.teams.getLast().setLeaveTime(now);
            }
        }
    }

    public PlayerGameStats getStats(String uuid) {
        return playerStatsCache.get(uuid);
    }

    public void finishGame() {
        var now = System.currentTimeMillis();

        if (currentBag == null) return;

        currentBag.setEndGameTime(now);

        gameDataRepository.save(currentBag);
        currentBag = null;
        playerStatsCache.clear();
    }

    public GameData getCurrent() { return currentBag; }
}