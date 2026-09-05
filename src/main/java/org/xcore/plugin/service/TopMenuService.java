package org.xcore.plugin.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.common.CustomGatherers;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.integration.top.LeaderboardEntry;
import org.xcore.plugin.integration.top.LeaderboardPage;
import org.xcore.plugin.integration.top.LeaderboardPageRequest;
import org.xcore.plugin.integration.top.TopCategoryProvider;
import org.xcore.plugin.integration.top.TopCategoryRegistry;
import org.xcore.plugin.model.LeaderboardCursor;
import org.xcore.plugin.model.LeaderboardSlice;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.enums.TopCategory;
import org.xcore.plugin.service.top.BuiltInTopCategoryProvider;
import org.xcore.plugin.service.top.LeaderboardCursorCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Singleton
public class TopMenuService {

    private final TomlXcoreConfig config;
    private final PlayerDataRepository playerDataRepository;
    private final TopMenuCacheService topMenuCacheService;
    private final TopCategoryRegistry categoryRegistry;

    @Inject
    public TopMenuService(TomlXcoreConfig config,
                          PlayerDataRepository playerDataRepository,
                          TopMenuCacheService topMenuCacheService,
                          TopCategoryRegistry categoryRegistry) {
        this.config = config;
        this.playerDataRepository = playerDataRepository;
        this.topMenuCacheService = topMenuCacheService;
        this.categoryRegistry = categoryRegistry != null ? categoryRegistry : new TopCategoryRegistry();
        registerBuiltInProviders();
    }

    public TopMenuService(TomlXcoreConfig config,
                          PlayerDataRepository playerDataRepository,
                          TopMenuCacheService topMenuCacheService) {
        this(config, playerDataRepository, topMenuCacheService, new TopCategoryRegistry());
    }

    private void registerBuiltInProviders() {
        boolean isMini = isMiniPvPServer();
        categoryRegistry.registerIfAbsent(new BuiltInTopCategoryProvider(TopCategory.MINI_PVP, isMini ? 20 : 10, this));
        categoryRegistry.registerIfAbsent(new BuiltInTopCategoryProvider(TopCategory.PLAYTIME, isMini ? 10 : 20, this));
        categoryRegistry.registerIfAbsent(new BuiltInTopCategoryProvider(TopCategory.HEXED, 5, this));
    }

    public TopCategoryRegistry categoryRegistry() {
        return categoryRegistry;
    }

    public Optional<TopCategoryProvider> resolveProvider(String id) {
        return categoryRegistry.resolve(id);
    }

    public Optional<TopCategoryProvider> resolveDefaultProvider() {
        TopCategory enumDefault = resolveDefaultCategory();
        String fallbackId = enumDefault != null ? enumDefault.name() : null;
        return categoryRegistry.resolveDefault(fallbackId);
    }

    public LeaderboardPage loadPage(LeaderboardPageRequest request) {
        Objects.requireNonNull(request, "request");
        String categoryId = request.categoryId();

        TopCategory enumCat = parseEnumCategory(categoryId);
        if (enumCat != null) {
            LeaderboardCursor cursor = LeaderboardCursorCodec.decode(request.cursor());
            TopCursorPage cursorPage = loadCursorPage(enumCat, cursor, request.page(), request.pageSize(), request.viewerData());
            return adaptToLeaderboardPage(cursorPage);
        }

        TopCategoryProvider provider = categoryRegistry.resolve(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown top category provider: " + categoryId));
        return provider.loadPage(request);
    }

    private TopCategory parseEnumCategory(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return TopCategory.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private LeaderboardPage adaptToLeaderboardPage(TopCursorPage cursorPage) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        for (int i = 0; i < cursorPage.players().size(); i++) {
            PlayerData player = cursorPage.players().get(i);
            int rank = cursorPage.displayRank(i);
            entries.add(new LeaderboardEntry(
                    player.uuid,
                    rank,
                    player.nickname != null ? player.nickname : player.uuid,
                    String.valueOf(player.pvpRating),
                    java.util.Map.of(),
                    ""
            ));
        }
        return new LeaderboardPage(
                cursorPage.currentPage(),
                entries,
                cursorPage.hasNext(),
                LeaderboardCursorCodec.encode(cursorPage.nextCursor()),
                cursorPage.totalEntries(),
                cursorPage.selfRank()
        );
    }

    public TopCategory resolveDefaultCategory() {
        if (isMiniPvPServer()) {
            return TopCategory.MINI_PVP;
        }

        return TopCategory.PLAYTIME;
    }

    private boolean isMiniPvPServer() {
        return "mini-pvp".equals(config.server.name);
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
