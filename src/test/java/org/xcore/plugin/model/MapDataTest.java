package org.xcore.plugin.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MapDataTest {

    @Test
    @DisplayName("registerGame initializes stats on first game")
    void registerFirstGame() {
        var map = new MapData("Map", "map.msav", "Author", "survival");

        map.registerGame(120_000, true, "pvp", "NewAuthor");

        assertThat(map.getPlayedTimes()).isEqualTo(1);
        assertThat(map.getPlayedTimesYear()).isEqualTo(1);
        assertThat(map.getAverageGameTime()).isEqualTo(120_000);
        assertThat(map.getMinimumGameTime()).isEqualTo(120_000);
        assertThat(map.getMaximumGameTime()).isEqualTo(120_000);
        assertThat(map.getPopularity()).isEqualTo(2.0);
        assertThat(map.getInterest()).isEqualTo(-2.0);
        assertThat(map.getGameMode()).isEqualTo("pvp");
        assertThat(map.getAuthor()).isEqualTo("NewAuthor");
    }

    @Test
    @DisplayName("registerGame updates aggregate stats for subsequent games")
    void registerSubsequentGames() {
        var map = new MapData("Map", "map.msav", "Author", "survival");
        map.registerGame(1_000, true, "pvp", "AuthorA");

        map.registerGame(3_000, false, "pvp", "AuthorB");

        assertThat(map.getPlayedTimes()).isEqualTo(2);
        assertThat(map.getPlayedTimesYear()).isEqualTo(2);
        assertThat(map.getAverageGameTime()).isEqualTo(2_000);
        assertThat(map.getMinimumGameTime()).isEqualTo(1_000);
        assertThat(map.getMaximumGameTime()).isEqualTo(3_000);
        assertThat(map.getPopularity()).isEqualTo(2.5);
        assertThat(map.getInterest()).isEqualTo(-2.5);
        assertThat(map.getAuthor()).isEqualTo("AuthorB");
    }

    @Test
    @DisplayName("onSkip decreases popularity and interest")
    void onSkipAdjustsScores() {
        var map = new MapData("Map", "map.msav", "Author", "survival");
        map.setPopularity(5.0);
        map.setInterest(1.5);

        map.onSkip();

        assertThat(map.getPopularity()).isEqualTo(4.0);
        assertThat(map.getInterest()).isEqualTo(1.0);
    }
}
