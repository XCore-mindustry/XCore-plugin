package org.xcore.plugin.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.common.CustomGatherers;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.enums.TopCategory;

import java.util.List;

@Singleton
public class TopMenuService {

    private final Config config;
    private final PlayerDataRepository playerDataRepository;

    @Inject
    public TopMenuService(Config config, PlayerDataRepository playerDataRepository) {
        this.config = config;
        this.playerDataRepository = playerDataRepository;
    }

    public TopCategory resolveDefaultCategory() {
        if (config.isMiniPvP()) {
            return TopCategory.MINI_PVP;
        }

        if (config.isMiniHexed()) {
            return TopCategory.HEXED;
        }

        return TopCategory.PLAYTIME;
    }

    public TopPage loadPage(TopCategory category, int page, int pageSize) {
        TopCategory resolvedCategory = category == null ? resolveDefaultCategory() : category;
        int safePageSize = Math.max(1, pageSize);
        long totalEntries = playerDataRepository.countTopEntries();

        if (totalEntries <= 0) {
            return new TopPage(resolvedCategory, 1, 1, safePageSize, 0, List.of());
        }

        var pagination = CustomGatherers.calculatePagination(totalEntries, safePageSize);
        int currentPage = pagination.clampPage(page);
        List<PlayerData> players = playerDataRepository.findTopPage(resolvedCategory, safePageSize, currentPage);

        return new TopPage(resolvedCategory, currentPage, pagination.totalPages(), safePageSize, totalEntries, players);
    }

    public record TopPage(
            TopCategory category,
            int currentPage,
            int totalPages,
            int pageSize,
            long totalEntries,
            List<PlayerData> players
    ) {
        public boolean hasPrevious() {
            return currentPage > 1;
        }

        public boolean hasNext() {
            return currentPage < totalPages;
        }

        public int displayRank(int zeroBasedIndexOnPage) {
            return ((currentPage - 1) * pageSize) + zeroBasedIndexOnPage + 1;
        }
    }
}
