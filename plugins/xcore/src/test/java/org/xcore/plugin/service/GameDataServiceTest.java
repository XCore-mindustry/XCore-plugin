package org.xcore.plugin.service;

import mindustry.game.Team;
import mindustry.core.GameState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.GameDataRepository;
import org.xcore.plugin.model.GameData;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.PlayerGameStats;
import org.xcore.plugin.model.enums.FinishReason;
import org.xcore.plugin.model.enums.GameStatsCategory;
import org.xcore.plugin.model.enums.VictoryType;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static mindustry.Vars.state;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GameDataServiceTest {

    @BeforeEach
    void setUp() {
        if (mindustry.Vars.state == null) {
            mindustry.Vars.state = new GameState();
        }
        if (state.rules == null) {
            state.rules = new mindustry.game.Rules();
        }
        state.rules.pvp = false;
        state.rules.waves = false;
        state.rules.waveTeam = Team.crux;
    }

    @AfterEach
    void tearDown() {
        state.wave = 0;
        state.rules.waves = false;
        state.rules.waveTeam = Team.crux;
    }

    @Test
    @DisplayName("finishGame marks players on winning team before saving")
    void finishGameMarksWinningPlayersBeforeSaving() {
        var repository = mock(GameDataRepository.class);
        var config = config("mini-pvp");
        var service = new GameDataService(config, repository);
        var map = new MapData("Map", "map.msav", "Author", "pvp");
        state.rules.pvp = true;

        service.startNewGame(map, "pvp", null);

        GameData game = service.getCurrent();
        var winner = playerStats("winner-1", Team.sharded.name);
        var ally = playerStats("winner-2", Team.sharded.name);
        var loser = playerStats("loser", Team.crux.name);
        game.setPlayerStats(List.of(winner, ally, loser));

        service.finishGame(Team.sharded);

        assertThat(winner.isWinner()).isTrue();
        assertThat(ally.isWinner()).isTrue();
        assertThat(loser.isWinner()).isFalse();
        assertThat(service.getCurrent()).isNull();

        var captor = ArgumentCaptor.forClass(GameData.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getWinningTeam()).isEqualTo(Team.sharded.name);
        assertThat(captor.getValue().getVictoryType()).isEqualTo(VictoryType.PVP_WIN);
        assertThat(captor.getValue().getStatsCategory()).isEqualTo(GameStatsCategory.PVP);
        assertThat(captor.getValue().isCountedInStats()).isTrue();
    }

    @Test
    @DisplayName("finishGame does not mark winners for derelict team")
    void finishGameDoesNotMarkWinnersForDerelictTeam() {
        var repository = mock(GameDataRepository.class);
        var config = config("mini-pvp");
        var service = new GameDataService(config, repository);
        var map = new MapData("Map", "map.msav", "Author", "pvp");
        state.rules.pvp = true;

        service.startNewGame(map, "pvp", null);

        GameData game = service.getCurrent();
        var player = playerStats("player", Team.sharded.name);
        player.setWinner(true);
        game.setPlayerStats(List.of(player));

        service.finishGame(Team.derelict);

        assertThat(player.isWinner()).isFalse();

        var captor = ArgumentCaptor.forClass(GameData.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getWinningTeam()).isEqualTo(Team.derelict.name);
        assertThat(captor.getValue().getVictoryType()).isEqualTo(VictoryType.INTERRUPTED);
        assertThat(captor.getValue().getStatsCategory()).isEqualTo(GameStatsCategory.PVP);
        assertThat(captor.getValue().isRanked()).isTrue();
    }

    @Test
    @DisplayName("markWinners only marks players whose final team matches winner")
    void markWinnersMatchesFinalTeam() {
        var service = new GameDataService(config("server"), mock(GameDataRepository.class));
        var map = new MapData("Map", "map.msav", "Author", "pvp");

        service.startNewGame(map, "pvp", null);

        GameData game = service.getCurrent();
        var stayed = playerStats("stayed", Team.sharded.name);
        var left = playerStats("left", Team.derelict.name);
        var other = playerStats("other", Team.crux.name);
        game.setPlayerStats(List.of(stayed, left, other));

        service.markWinners(Team.sharded);

        assertThat(stayed.isWinner()).isTrue();
        assertThat(left.isWinner()).isFalse();
        assertThat(other.isWinner()).isFalse();
    }

    @Test
    @DisplayName("finishGame stores wave metadata for survival defeat")
    void finishGameStoresWaveMetadataForSurvivalDefeat() {
        var repository = mock(GameDataRepository.class);
        var service = new GameDataService(config("server"), repository);
        var map = new MapData("Map", "map.msav", "Author", "survival");

        state.wave = 42;
        state.rules.waves = true;
        state.rules.waveTeam = Team.crux;

        service.startNewGame(map, "survival", null);
        service.getCurrent().setPlayerStats(List.of(playerStats("player", Team.sharded.name)));

        service.finishGame(Team.crux);

        var captor = ArgumentCaptor.forClass(GameData.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getWavesReached()).isEqualTo(42);
        assertThat(captor.getValue().getWinningTeam()).isEqualTo(Team.crux.name);
        assertThat(captor.getValue().getVictoryType()).isEqualTo(VictoryType.LOSE);
        assertThat(captor.getValue().getStatsCategory()).isEqualTo(GameStatsCategory.SURVIVAL);
        assertThat(captor.getValue().isCountedInStats()).isTrue();
    }

    @Test
    @DisplayName("finishGame stores non-natural finish as not counted in stats")
    void finishGameStoresNonNaturalFinishAsNotCounted() {
        var repository = mock(GameDataRepository.class);
        var config = config("mini-pvp");
        var service = new GameDataService(config, repository);
        var map = new MapData("Map", "map.msav", "Author", "pvp");

        state.rules.pvp = true;
        service.startNewGame(map, "pvp", null);
        service.getCurrent().setPlayerStats(List.of(playerStats("player", Team.sharded.name)));

        service.finishGame(null, FinishReason.RTV);

        var captor = ArgumentCaptor.forClass(GameData.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getFinishReason()).isEqualTo(FinishReason.RTV);
        assertThat(captor.getValue().isCountedInStats()).isFalse();
    }

    @Test
    @DisplayName("applyPlacements stores placement on matching players")
    void applyPlacementsStoresPlacement() {
        var service = new GameDataService(config("server"), mock(GameDataRepository.class));
        var map = new MapData("Map", "map.msav", "Author", "hexed");

        service.startNewGame(map, "hexed", null);

        GameData game = service.getCurrent();
        var first = playerStats("first", Team.sharded.name);
        var second = playerStats("second", Team.crux.name);
        game.setPlayerStats(List.of(first, second));

        var placements = new HashMap<String, Integer>();
        placements.put("first", 1);
        placements.put("second", 2);

        service.applyPlacements(placements);

        assertThat(first.getPlacement()).isEqualTo(1);
        assertThat(second.getPlacement()).isEqualTo(2);
    }

    @Test
    @DisplayName("startNewGame classifies mini-hexed server as HEXED")
    void startNewGameClassifiesMiniHexedServerAsHexed() {
        var service = new GameDataService(config("mini-hexed"), mock(GameDataRepository.class));
        var map = new MapData("Map", "map.msav", "Author", "pvp");

        state.rules.pvp = true;
        service.startNewGame(map, "pvp", null);

        assertThat(service.getCurrent().getStatsCategory()).isEqualTo(GameStatsCategory.HEXED);
        assertThat(service.getCurrent().isRanked()).isTrue();
    }

    private static PlayerGameStats playerStats(String uuid, String finalTeam) {
        return PlayerGameStats.builder()
                .uuid(uuid)
                .nickname(uuid)
                .initialTeam(finalTeam)
                .finalTeam(finalTeam)
                .build();
    }

    private static TomlXcoreConfig config(String serverName) {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = serverName;
        return config;
    }
}
