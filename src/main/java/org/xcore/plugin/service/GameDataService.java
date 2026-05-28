package org.xcore.plugin.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Administration;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.GameDataRepository;
import org.xcore.plugin.model.*;
import org.xcore.plugin.model.enums.FinishReason;
import org.xcore.plugin.model.enums.GameStatsCategory;
import org.xcore.plugin.model.enums.VictoryType;

import java.util.HashMap;
import java.util.Map;

import static mindustry.Vars.state;

@Singleton
public class GameDataService {

    private final TomlXcoreConfig config;
    private final GameDataRepository gameDataRepository;

    private GameData currentBag;

    private final Map<String, PlayerGameStats> playerStatsCache = new HashMap<>();

    @Inject
    public GameDataService(TomlXcoreConfig config, GameDataRepository gameDataRepository) {
        this.config = config;
        this.gameDataRepository = gameDataRepository;
    }

    public void startNewGame(MapData map, String mode, EventData event) {
        var now = System.currentTimeMillis();

        if (map == null) return;

        currentBag = GameData.builder()
            .map(map.id)
            .gameMode(mode)
            .serverName(resolveServerName())
            .statsCategory(resolveStatsCategory(mode, event != null))
            .ranked(isRankedCategory(mode, event != null))
            .startGameTime(now)
            .build();

        if (event != null) {
            currentBag.event = event.id;
            currentBag.isEvent = true;
        }
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

    public void finishGame(Team winner) {
        finishGame(winner, FinishReason.NATURAL);
    }

    public void finishGame(Team winner, FinishReason finishReason) {
        var now = System.currentTimeMillis();

        if (currentBag == null) return;

        syncFinalTeams();
        currentBag.setEndGameTime(now);
        currentBag.setWavesReached(state.wave);
        currentBag.setWinningTeam(winner == null ? null : winner.name);
        currentBag.setVictoryType(resolveVictoryType(winner));
        currentBag.setFinishReason(finishReason == null ? FinishReason.NATURAL : finishReason);
        currentBag.setCountedInStats(shouldCountInStats(winner, currentBag.getFinishReason(), currentBag.getStatsCategory()));
        markWinners(winner);
        markPlayersPlayedToEnd();

        gameDataRepository.save(currentBag);
        currentBag = null;
        playerStatsCache.clear();
    }

    private void syncFinalTeams() {
        if (currentBag == null || Groups.player == null) {
            return;
        }

        Groups.player.each(player -> {
            var stats = playerStatsCache.get(player.uuid());
            if (stats != null) {
                stats.setFinalTeam(player.team().name);
            }
        });
    }

    private VictoryType resolveVictoryType(Team winner) {
        if (winner == null) {
            return VictoryType.INTERRUPTED;
        }
        if (state.rules.waves) {
            return winner == state.rules.waveTeam ? VictoryType.LOSE : VictoryType.WAVE_LIMIT;
        }
        if (winner == Team.derelict) {
            return VictoryType.INTERRUPTED;
        }
        return VictoryType.PVP_WIN;
    }

    private GameStatsCategory resolveStatsCategory(String mode, boolean eventGame) {
        var rules = state == null ? null : state.rules;

        if (isMiniHexedServer() || configIsHexedMode(mode)) {
            return GameStatsCategory.HEXED;
        }
        if (eventGame) {
            return GameStatsCategory.EVENT;
        }
        if (rules != null && rules.waves) {
            return GameStatsCategory.SURVIVAL;
        }
        if (rules != null && rules.pvp) {
            return GameStatsCategory.PVP;
        }
        return GameStatsCategory.OTHER;
    }

    private boolean isRankedCategory(String mode, boolean eventGame) {
        GameStatsCategory category = resolveStatsCategory(mode, eventGame);
        return category == GameStatsCategory.PVP || category == GameStatsCategory.HEXED;
    }

    private boolean shouldCountInStats(Team winner, FinishReason finishReason, GameStatsCategory statsCategory) {
        if (finishReason != FinishReason.NATURAL) {
            return false;
        }
        if (winner == null || winner == Team.derelict) {
            return false;
        }
        return statsCategory == GameStatsCategory.PVP
                || statsCategory == GameStatsCategory.HEXED
                || statsCategory == GameStatsCategory.SURVIVAL
                || statsCategory == GameStatsCategory.EVENT;
    }

    private boolean configIsHexedMode(String mode) {
        return mode != null && mode.toLowerCase().contains("hexed");
    }

    private boolean isMiniHexedServer() {
        return "mini-hexed".equals(config.server.name);
    }

    private String resolveServerName() {
        try {
            if (Administration.Config.serverName != null) {
                return Administration.Config.serverName.string();
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    void markWinners(Team winner) {
        if (currentBag == null) {
            return;
        }

        for (var stats : currentBag.getPlayerStats()) {
            stats.setWinner(false);
        }

        if (winner == null || winner == Team.derelict) {
            return;
        }

        String winnerName = winner.name;
        for (var stats : currentBag.getPlayerStats()) {
            stats.setWinner(winnerName.equals(stats.getFinalTeam()));
        }
    }

    void markPlayersPlayedToEnd() {
        if (currentBag == null) {
            return;
        }

        long gameEndTime = currentBag.getEndGameTime();
        for (var stats : currentBag.getPlayerStats()) {
            boolean hasLeftBeforeEnd = stats.getLeaveTime() > 0 && stats.getLeaveTime() < gameEndTime;
            stats.setPlayedToEnd(!hasLeftBeforeEnd);
        }
    }

    public void applyPlacements(Map<String, Integer> placements) {
        if (currentBag == null || placements == null || placements.isEmpty()) {
            return;
        }

        for (var stats : currentBag.getPlayerStats()) {
            stats.setPlacement(placements.get(stats.getUuid()));
        }
    }

    public GameData getCurrent() { return currentBag; }
}
