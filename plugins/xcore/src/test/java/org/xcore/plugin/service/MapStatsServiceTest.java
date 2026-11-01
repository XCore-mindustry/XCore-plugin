package org.xcore.plugin.service;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.model.MapData;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MapStatsServiceTest {

    @Test
    @DisplayName("registerCompletedGame updates aggregate map stats")
    void registerCompletedGameUpdatesAggregateStats() {
        var repository = mock(MapDataRepository.class);
        var map = new MapData("Hex", "hex.msav", "OldAuthor", "pvp");
        map.id = new ObjectId();
        map.setPlayedTimes(1);
        map.setPlayedTimesYear(1);
        map.setAverageGameTime(180_000);
        map.setMinimumGameTime(180_000);
        map.setMaximumGameTime(180_000);
        when(repository.findOrCreate("Hex", "hex.msav", "Author", "pvp"))
                .thenReturn(map);
        when(repository.registerGameStats(
                org.mockito.ArgumentMatchers.eq(map.id),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble()
        )).thenReturn(true);
        var service = new MapStatsService(repository);

        boolean updated = service.registerCompletedGame("Hex", "hex.msav", "Author", "pvp", 300_000, true);

        assertThat(updated).isTrue();
        assertThat(map.getPlayedTimes()).isEqualTo(2);
        assertThat(map.getPlayedTimesYear()).isEqualTo(2);
        assertThat(map.getAverageGameTime()).isEqualTo(240_000);
        assertThat(map.getMinimumGameTime()).isEqualTo(180_000);
        assertThat(map.getMaximumGameTime()).isEqualTo(300_000);
        assertThat(map.getPopularity()).isEqualTo(2.0);
        assertThat(map.getInterest()).isEqualTo(-2.0);

        var playedTimes = ArgumentCaptor.forClass(Long.class);
        verify(repository).registerGameStats(
                org.mockito.ArgumentMatchers.eq(map.id),
                org.mockito.ArgumentMatchers.eq(300_000L),
                org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.eq("Author"),
                org.mockito.ArgumentMatchers.eq("pvp"),
                playedTimes.capture(),
                org.mockito.ArgumentMatchers.eq(240_000L),
                org.mockito.ArgumentMatchers.eq(180_000L),
                org.mockito.ArgumentMatchers.eq(300_000L),
                org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(2.0),
                org.mockito.ArgumentMatchers.eq(-2.0)
        );
        assertThat(playedTimes.getValue()).isEqualTo(2L);
    }

    @Test
    @DisplayName("registerCompletedGame ignores short games")
    void registerCompletedGameIgnoresShortGames() {
        var repository = mock(MapDataRepository.class);
        var service = new MapStatsService(repository);

        boolean updated = service.registerCompletedGame("Hex", "hex.msav", "Author", "pvp", 120_000, true);

        assertThat(updated).isFalse();
        verify(repository, never()).findOrCreate(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }
}
