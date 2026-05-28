package org.xcore.plugin.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.common.CustomGatherers;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.LeaderboardCursor;
import org.xcore.plugin.model.LeaderboardSlice;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.enums.TopCategory;

import java.util.List;

@Singleton
public class TopMenuService {

    private final TomlXcoreConfig config;
    private final PlayerDataRepository playerDataRepository;
    private final TopMenuCacheService topMenuCacheService;

    @Inject
    public TopMenuService(TomlXcoreConfig config,
                          PlayerDataRepository playerDataRepository,
                          TopMenuCacheService topMenuCacheService) {
        this.config = config;
        this.playerDataRepository = playerDataRepository;
        this.topMenuCacheService = topMenuCacheService;
    }

    public TopCategory resolveDefaultCategory() {
        if (isMiniPvPServer()) {
            return TopCategory.MINI_PVP;
        }

        if (isMiniHexedServer()) {
            return TopCategory.HEXED;
        }

        return TopCategory.PLAYTIME;
    }

    private boolean isMiniPvPServer() {
        return "mini-pvp".equals(config.server.name);
    }

    private boolean isMiniHexedServer() {
        return "mini-hexed".equals(config.server.name);
    }

    public void invalidateLeaderboardCache() {
        topMenuCacheService.invalidateAll();
    }

    public TopCursorPage loadCursorPage(TopCategory category,
                                        LeaderboardCursor cursor,
                                        int page,
                                        int pageSize,
                                        PlayerData viewerData) {
        TopCategory resolvedCategory = category == null ? resolveDefaultCategory() : category;
        int safePageSize = Math.max(1, pageSize);
        int safePage = Math.max(1, page);
        long version = topMenuCacheService.currentVersion();
        TopCursorPage loaded = loadCursorPage(version, resolvedCategory, cursor, safePage, safePageSize, viewerData);
        long currentVersion = topMenuCacheService.currentVersion();
        if (currentVersion != version) {
            return loadCursorPage(currentVersion, resolvedCategory, cursor, safePage, safePageSize, viewerData);
        }

        return loaded;
    }

    long cachedTotalEntries(long version) {
        Long cached = topMenuCacheService.getTotalEntries(version);
        if (cached != null) {
            return cached;
        }

        long loaded = playerDataRepository.countTopEntries();
        topMenuCacheService.putTotalEntries(version, loaded);
        return loaded;
    }

    TopCursorPage loadCursorPage(long version,
                                 TopCategory category,
                                 LeaderboardCursor cursor,
                                 int page,
                                 int pageSize,
                                 PlayerData viewerData) {
        long totalEntries = cachedTotalEntries(version);
        Integer selfRank = playerDataRepository.findTopRank(category, viewerData);

        if (totalEntries <= 0) {
            return new TopCursorPage(category, page, 1, pageSize, 0, selfRank, List.of(), cursor, null, false);
        }

        var pagination = CustomGatherers.calculatePagination(totalEntries, pageSize);
        int currentPage = pagination.clampPage(page);
        LeaderboardSlice<PlayerData> slice = cachedTopSlice(version, category, pageSize, cursor);

        return new TopCursorPage(
                category,
                currentPage,
                pagination.totalPages(),
                pageSize,
                totalEntries,
                selfRank,
                slice.items(),
                cursor,
                slice.nextCursor(),
                slice.hasNext()
        );
    }

    LeaderboardSlice<PlayerData> cachedTopSlice(long version,
                                                TopCategory category,
                                                int pageSize,
                                                LeaderboardCursor cursor) {
        LeaderboardSlice<PlayerData> cached = topMenuCacheService.getTopSlice(version, category, pageSize, cursor);
        if (cached != null) {
            return cached;
        }

        LeaderboardSlice<PlayerData> loaded = playerDataRepository.findTopSlice(category, cursor, pageSize);
        topMenuCacheService.putTopSlice(version, category, pageSize, cursor, loaded);
        return loaded;
    }

    public record TopCursorPage(
            TopCategory category,
            int currentPage,
            int totalPages,
            int pageSize,
            long totalEntries,
            Integer selfRank,
            List<PlayerData> players,
            LeaderboardCursor currentCursor,
            LeaderboardCursor nextCursor,
            boolean hasNext
    ) {
        public int displayRank(int zeroBasedIndexOnPage) {
            return ((currentPage - 1) * pageSize) + zeroBasedIndexOnPage + 1;
        }
    }
}
