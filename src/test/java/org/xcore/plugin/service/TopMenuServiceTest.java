package org.xcore.plugin.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.LeaderboardCursor;
import org.xcore.plugin.model.LeaderboardSlice;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.enums.TopCategory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TopMenuServiceTest {

    @Test
    @DisplayName("loadCursorPage resolves default category from config")
    void loadCursorPage_resolvesDefaultCategoryFromConfig() {
        PlayerDataRepository repository = mock(PlayerDataRepository.class);
        TopMenuCacheService cacheService = mock(TopMenuCacheService.class);
        when(cacheService.currentVersion()).thenReturn(1L, 1L);
        when(cacheService.getTotalEntries(1L)).thenReturn(1L);
        when(repository.findTopRank(TopCategory.MINI_PVP, null)).thenReturn(3);

        PlayerData player = player("player-1", 10);
        LeaderboardSlice<PlayerData> slice = new LeaderboardSlice<>(List.of(player), false, null);
        when(cacheService.getTopSlice(1L, TopCategory.MINI_PVP, 10, null)).thenReturn(slice);

        TopMenuService service = new TopMenuService(config("mini-pvp"), repository, cacheService);

        TopMenuService.TopCursorPage page = service.loadCursorPage(null, null, 1, 10, null);

        assertThat(page.category()).isEqualTo(TopCategory.MINI_PVP);
        assertThat(page.players()).containsExactly(player);
        assertThat(page.currentPage()).isEqualTo(1);
    }

    @Test
    @DisplayName("loadCursorPage returns empty page when leaderboard has no entries")
    void loadCursorPage_returnsEmptyPageWhenNoEntries() {
        PlayerDataRepository repository = mock(PlayerDataRepository.class);
        TopMenuCacheService cacheService = mock(TopMenuCacheService.class);
        when(cacheService.currentVersion()).thenReturn(1L, 1L);
        when(cacheService.getTotalEntries(1L)).thenReturn(0L);
        when(repository.findTopRank(TopCategory.PLAYTIME, null)).thenReturn(null);

        TopMenuService service = new TopMenuService(config("survival"), repository, cacheService);

        TopMenuService.TopCursorPage page = service.loadCursorPage(null, null, 4, 10, null);

        assertThat(page.category()).isEqualTo(TopCategory.PLAYTIME);
        assertThat(page.currentPage()).isEqualTo(4);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.totalEntries()).isZero();
        assertThat(page.players()).isEmpty();
        assertThat(page.hasNext()).isFalse();
        verify(repository, never()).countTopEntries();
        verify(cacheService, never()).getTopSlice(1L, TopCategory.PLAYTIME, 10, null);
    }

    @Test
    @DisplayName("loadCursorPage caches count and first slice on misses")
    void loadCursorPage_cachesCountAndFirstSliceOnMisses() {
        PlayerDataRepository repository = mock(PlayerDataRepository.class);
        TopMenuCacheService cacheService = mock(TopMenuCacheService.class);
        when(cacheService.currentVersion()).thenReturn(5L, 5L);
        when(cacheService.getTotalEntries(5L)).thenReturn(null);
        when(repository.countTopEntries()).thenReturn(15L);
        when(repository.findTopRank(TopCategory.PLAYTIME, null)).thenReturn(7);

        PlayerData player = player("player-1", 10);
        LeaderboardCursor nextCursor = new LeaderboardCursor(120, 0, 10);
        LeaderboardSlice<PlayerData> slice = new LeaderboardSlice<>(List.of(player), true, nextCursor);
        when(cacheService.getTopSlice(5L, TopCategory.PLAYTIME, 10, null)).thenReturn(null);
        when(repository.findTopSlice(TopCategory.PLAYTIME, null, 10)).thenReturn(slice);

        TopMenuService service = new TopMenuService(config("survival"), repository, cacheService);

        TopMenuService.TopCursorPage page = service.loadCursorPage(TopCategory.PLAYTIME, null, 1, 10, null);

        assertThat(page.players()).containsExactly(player);
        assertThat(page.nextCursor()).isEqualTo(nextCursor);
        verify(repository, times(1)).countTopEntries();
        verify(cacheService, times(1)).putTotalEntries(5L, 15L);
        verify(repository, times(1)).findTopSlice(TopCategory.PLAYTIME, null, 10);
        verify(cacheService, times(1)).putTopSlice(5L, TopCategory.PLAYTIME, 10, null, slice);
    }

    @Test
    @DisplayName("loadCursorPage reuses cached slice without hitting repository")
    void loadCursorPage_reusesCachedSliceWithoutHittingRepository() {
        PlayerDataRepository repository = mock(PlayerDataRepository.class);
        TopMenuCacheService cacheService = mock(TopMenuCacheService.class);
        LeaderboardCursor cursor = new LeaderboardCursor(1500, 0, 22);
        when(cacheService.currentVersion()).thenReturn(2L, 2L);
        when(cacheService.getTotalEntries(2L)).thenReturn(20L);

        PlayerData player = player("top-1", 100);
        LeaderboardSlice<PlayerData> slice = new LeaderboardSlice<>(List.of(player), false, null);
        when(cacheService.getTopSlice(2L, TopCategory.MINI_PVP, 10, cursor)).thenReturn(slice);
        when(repository.findTopRank(TopCategory.MINI_PVP, null)).thenReturn(4);

        TopMenuService service = new TopMenuService(config("mini-pvp"), repository, cacheService);

        TopMenuService.TopCursorPage page = service.loadCursorPage(TopCategory.MINI_PVP, cursor, 2, 10, null);

        assertThat(page.selfRank()).isEqualTo(4);
        assertThat(page.players()).containsExactly(player);
        assertThat(page.currentCursor()).isEqualTo(cursor);
        verify(repository, never()).countTopEntries();
        verify(repository, never()).findTopSlice(TopCategory.MINI_PVP, cursor, 10);
        verify(repository, times(1)).findTopRank(TopCategory.MINI_PVP, null);
    }

    @Test
    @DisplayName("invalidateLeaderboardCache delegates to cache service")
    void invalidateLeaderboardCache_delegatesToCacheService() {
        PlayerDataRepository repository = mock(PlayerDataRepository.class);
        TopMenuCacheService cacheService = mock(TopMenuCacheService.class);
        TopMenuService service = new TopMenuService(config("survival"), repository, cacheService);

        service.invalidateLeaderboardCache();

        verify(cacheService, times(1)).invalidateAll();
    }

    private static TomlXcoreConfig config(String server) {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = server;
        return config;
    }

    private static PlayerData player(String uuid, int pid) {
        PlayerData data = new PlayerData(uuid, true);
        data.pid = pid;
        data.nickname = uuid;
        return data;
    }
}
