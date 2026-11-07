package org.xcore.plugin.service;

import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Administration;
import org.bson.types.ObjectId;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.GameDataRepository;
import org.xcore.plugin.integration.gamehistory.MatchHistoryRecord;
import org.xcore.plugin.model.*;
import org.xcore.plugin.model.enums.FinishReason;
import org.xcore.plugin.model.enums.GameStatsCategory;
import org.xcore.plugin.model.enums.VictoryType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static mindustry.Vars.state;

@Singleton
public class GameDataService {

    private final TomlXcoreConfig config;
    private final GameDataRepository gameDataRepository;

    private volatile GameData currentBag;

    private final Map<String, PlayerGameStats> playerStatsCache = new HashMap<>();

    @Inject
    public GameDataService(TomlXcoreConfig config, GameDataRepository gameDataRepository) {
        this.config = config;
        this.gameDataRepository = gameDataRepository;
    }

    public void startNewGame(MapData map, String mode, EventData event) {
        if (map == null) return;
        begin(map.id, mode, event);
    }

    /**
     * Starts capturing a match whose map record is still being looked up, so a slow or failing
     * lookup loses no participation; {@link #attachMap} fills the map in once it is known.
     */
    public GameData startGameWithoutMap(String mode) {
        begin(null, mode, null);
        return currentBag;
    }

    /** Fills in the map of {@code game} while it is still the running match. */
    public boolean attachMap(GameData game, MapData map) {
        if (game == null || map == null || game != currentBag || game.map != null) return false;
        game.map = map.id;
        return true;
    }

    private void begin(ObjectId mapId, String mode, EventData event) {
        var now = System.currentTimeMillis();

        if (externalOwner()) return;

        currentBag = GameData.builder()
            .serverId(config.server.name)
            .map(mapId)
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

        var stats = playerStatsCache.get(player.uuid());
        if (stats == null) {
            stats = PlayerGameStats.builder()
                    .nickname(player.plainName())
                    .uuid(player.uuid())
                    .participantUuid(player.uuid())
                    .joinTime(now)
                    .initialTeam(player.team().name)
                    .finalTeam(player.team().name)
                    .build();
            playerStatsCache.put(stats.uuid, stats);
            currentBag.getPlayerStats().add(stats);
        }
        // A reconnect continues the same row with a new presence interval.
        if (!stats.presenceIntervals.isEmpty() && stats.presenceIntervals.getLast().leaveTime == 0) return;
        stats.leaveTime = 0;
        stats.finalTeam = player.team().name;
        stats.presenceIntervals.add(TeamGameData.builder().team(player.team().name).joinTime(now).build());
        stats.teams.add(TeamGameData.builder().team(player.team().name).joinTime(now).build());
    }

    public void recordLeave(Player player) {
        var stats = playerStatsCache.get(player.uuid());
        if (stats == null || stats.presenceIntervals.isEmpty() || stats.presenceIntervals.getLast().leaveTime != 0) return;
        long now = System.currentTimeMillis();
        closePresence(stats, now);
        stats.leaveTime = now;
        stats.finalTeam = player.team().name;
        if (!stats.teams.isEmpty()) stats.teams.getLast().leaveTime = now;
    }

    private static void closePresence(PlayerGameStats stats, long end) {
        if (!stats.presenceIntervals.isEmpty()) {
            var interval = stats.presenceIntervals.getLast();
            if (interval.leaveTime == 0) {
                interval.leaveTime = end;
                stats.presentMs += Math.max(0, end - interval.joinTime);
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
        finishGameAsync(winner, finishReason).exceptionally(error -> {
            Log.err("[Stats] Failed to persist completed match", error);
            return Optional.empty();
        });
    }

    /**
     * Legacy synchronous persistence API. Internal gameplay uses finishGameAsync.
     * Finishes the current game and returns the persisted snapshot to integrations
     * that need to correlate a match with an external settlement operation.
     * The legacy void method above intentionally remains for compatibility.
     */
    @Deprecated
    public Optional<GameData> finishGameResult(Team winner, FinishReason finishReason) {
        var result = finishSnapshot(winner, finishReason);
        result.ifPresent(gameDataRepository::saveOnce);
        return result;
    }

    public CompletionStage<Optional<GameData>> finishGameAsync(Team winner, FinishReason reason) {
        var result = finishSnapshot(winner, reason);
        if (result.isEmpty()) return CompletableFuture.completedFuture(result);
        return gameDataRepository.saveOnceAsync(result.get()).thenApply(saved -> saved ? result : Optional.empty());
    }

    private Optional<GameData> finishSnapshot(Team winner, FinishReason finishReason) {
        var now = System.currentTimeMillis();

        if (currentBag == null) return Optional.empty();

        syncFinalTeams();
        currentBag.setEndGameTime(now);
        currentBag.setWavesReached(state.wave);
        currentBag.setWinningTeam(winner == null ? null : winner.name);
        currentBag.setVictoryType(resolveVictoryType(winner));
        currentBag.setFinishReason(finishReason == null ? FinishReason.NATURAL : finishReason);
        currentBag.setCountedInStats(shouldCountInStats(winner, currentBag.getFinishReason(), currentBag.getStatsCategory()));
        markWinners(winner);
        markPlayersPlayedToEnd();

        GameData finished = currentBag;
        for (var stats : finished.playerStats) {
            closePresence(stats, now);
            if (!stats.teams.isEmpty() && stats.teams.getLast().leaveTime == 0) stats.teams.getLast().leaveTime = now;
        }
        currentBag = null;
        playerStatsCache.clear();
        return Optional.of(finished);
    }

    private void syncFinalTeams() {
        if (currentBag == null || Groups.player == null) {
            return;
        }

        Groups.player.each(player -> {
            var stats = playerStatsCache.get(player.uuid());
            if (stats != null) {
                stats.setFinalTeam(player.team().name);
                // V1 outcomes use the final team; historical team intervals remain separate.
                if (!stats.presenceIntervals.isEmpty() && stats.presenceIntervals.getLast().leaveTime == 0) {
                    stats.presenceIntervals.getLast().team = player.team().name;
                }
            }
        });
    }

    /** Team assignments happen in many places (observer mode, admin commands, game modes), so they are sampled. */
    public void syncTeams() {
        if (currentBag == null || Groups.player == null) return;
        Groups.player.each(this::syncTeam);
    }

    public void syncTeam(Player player) {
        var stats = playerStatsCache.get(player.uuid());
        if (stats == null || stats.teams.isEmpty()) return;
        var current = stats.teams.getLast();
        String team = player.team().name;
        if (current.leaveTime != 0 || team.equals(current.team)) return;
        long now = System.currentTimeMillis();
        current.leaveTime = now;
        stats.teams.add(TeamGameData.builder().team(team).joinTime(now).build());
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
        if (eventGame || "event".equals(config.server.name)) {
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

    /** Until statistics are enabled, the legacy rule stands: record only when no lifecycle game is running. */
    private boolean acceptsExternalHistory() {
        return externalOwner() || !config.statistics.enabled && currentBag == null;
    }

    /** The owner switch is ignored while statistics are off, so it can be set ahead of the rollout. */
    private boolean externalOwner() {
        return config.statistics.enabled && "external".equals(config.statistics.historyOwner);
    }

    /** Records a plugin-owned match without taking over the legacy current-game lifecycle. */
    public boolean recordMatch(MatchHistoryRecord record) {
        if (record == null || !acceptsExternalHistory()) return false;
        return gameDataRepository.saveOnce(buildGameData(record));
    }

    public CompletionStage<Boolean> recordMatchAsync(MatchHistoryRecord record) {
        if (record == null || !acceptsExternalHistory()) {
            return CompletableFuture.completedFuture(false);
        }
        return gameDataRepository.saveOnceAsync(buildGameData(record));
    }

    private GameData buildGameData(MatchHistoryRecord record) {
        GameStatsCategory statsCategory = record.mode() != null && record.mode().toLowerCase(Locale.ROOT).contains("hexed")
                ? GameStatsCategory.HEXED : GameStatsCategory.PVP;
        GameData game = GameData.builder()
                .serverId(config.server.name)
                .gameMode(record.mode())
                .matchId(record.matchId())
                .serverName(resolveServerName())
                .statsCategory(statsCategory)
                .ranked(record.ranked())
                .countedInStats(record.ranked() && "NATURAL".equals(record.finishReason())
                        && record.winnerUuid() != null)
                .startGameTime(record.startedAt())
                .endGameTime(record.endedAt())
                .winningTeam(record.winnerUuid())
                .finishReason(resolveFinishReason(record.finishReason()))
                .victoryType(record.winnerUuid() == null ? VictoryType.INTERRUPTED : VictoryType.PVP_WIN)
                .build();
        for (var participant : record.participants()) {
            game.playerStats.add(PlayerGameStats.builder()
                    .uuid(participant.uuid())
                    .participantUuid(participant.uuid())
                    .nickname(participant.nickname() == null ? "Unknown" : participant.nickname())
                    .placement(participant.placement())
                    .isWinner(participant.winner())
                    .playedToEnd(participant.playedToEnd())
                    .joinTime(record.startedAt())
                    .leaveTime(record.endedAt())
                    .build());
        }
        return game;
    }

    private FinishReason resolveFinishReason(String value) {
        try {
            return FinishReason.valueOf(value);
        } catch (Exception ignored) {
            return FinishReason.SCRIPT;
        }
    }

    public GameData getCurrent() { return currentBag; }
}
