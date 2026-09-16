package org.xcore.plugin.ui.menu.map;

import java.util.List;

/**
 * Immutable state model for the reactive Map UI (both Browser and Details modes).
 */
public record MapUiModel(
        ViewMode mode,
        String playerUuid,
        boolean isAdmin,

        // --- Browser State ---
        String searchQuery,
        int page,
        int totalPages,
        List<MapSummary> displayedMaps,
        int totalMapsCount,

        // --- Details Identity & Hero ---
        String selectedMapId,
        String mapName,
        String mapAuthor,
        String mapDescription,
        int width,
        int height,
        String gamemodeName,
        boolean isCurrentMap,

        // --- Details Telemetry Matrix ---
        long playedTimes,
        long playedTimesYear,
        String lastPlayedFormatted,
        String minGameTime,
        String avgGameTime,
        String maxGameTime,
        int reputation,
        double popularity,
        double interest,
        int likes,
        int dislikes,
        int approvalRatePercent,
        Boolean playerVote, // null = none, true = like, false = dislike

        // --- Preview Texture State ---
        boolean previewLoading,
        String previewTextureRegion,

        // --- Live RTV Voting State ---
        boolean rtvActive,
        int rtvVotes,
        int rtvVotesRequired,
        int rtvRemainingSeconds,
        boolean adminForceConfirming,
        long adminConfirmExpireMillis
) {
    public enum ViewMode {
        BROWSER,
        DETAILS
    }

    public record MapSummary(
            String id,
            String name,
            String author,
            int width,
            int height,
            int likes,
            int dislikes,
            boolean isCurrent
    ) {}

    public MapUiModel withMode(ViewMode newMode) {
        return new MapUiModel(
                newMode, playerUuid, isAdmin,
                searchQuery, page, totalPages, displayedMaps, totalMapsCount,
                selectedMapId, mapName, mapAuthor, mapDescription, width, height, gamemodeName, isCurrentMap,
                playedTimes, playedTimesYear, lastPlayedFormatted, minGameTime, avgGameTime, maxGameTime,
                reputation, popularity, interest, likes, dislikes, approvalRatePercent, playerVote,
                previewLoading, previewTextureRegion,
                rtvActive, rtvVotes, rtvVotesRequired, rtvRemainingSeconds,
                adminForceConfirming, adminConfirmExpireMillis
        );
    }

    public MapUiModel withSearchQuery(String query) {
        return new MapUiModel(
                mode, playerUuid, isAdmin,
                query, page, totalPages, displayedMaps, totalMapsCount,
                selectedMapId, mapName, mapAuthor, mapDescription, width, height, gamemodeName, isCurrentMap,
                playedTimes, playedTimesYear, lastPlayedFormatted, minGameTime, avgGameTime, maxGameTime,
                reputation, popularity, interest, likes, dislikes, approvalRatePercent, playerVote,
                previewLoading, previewTextureRegion,
                rtvActive, rtvVotes, rtvVotesRequired, rtvRemainingSeconds,
                adminForceConfirming, adminConfirmExpireMillis
        );
    }

    public MapUiModel withPagination(int newPage, int newTotalPages, List<MapSummary> maps) {
        return withPagination(newPage, newTotalPages, maps, totalMapsCount);
    }

    public MapUiModel withPagination(int newPage, int newTotalPages, List<MapSummary> maps, int newTotalMapsCount) {
        return new MapUiModel(
                mode, playerUuid, isAdmin,
                searchQuery, newPage, newTotalPages, maps, newTotalMapsCount,
                selectedMapId, mapName, mapAuthor, mapDescription, width, height, gamemodeName, isCurrentMap,
                playedTimes, playedTimesYear, lastPlayedFormatted, minGameTime, avgGameTime, maxGameTime,
                reputation, popularity, interest, likes, dislikes, approvalRatePercent, playerVote,
                previewLoading, previewTextureRegion,
                rtvActive, rtvVotes, rtvVotesRequired, rtvRemainingSeconds,
                adminForceConfirming, adminConfirmExpireMillis
        );
    }

    public MapUiModel withPreview(String region, boolean loading) {
        return new MapUiModel(
                mode, playerUuid, isAdmin,
                searchQuery, page, totalPages, displayedMaps, totalMapsCount,
                selectedMapId, mapName, mapAuthor, mapDescription, width, height, gamemodeName, isCurrentMap,
                playedTimes, playedTimesYear, lastPlayedFormatted, minGameTime, avgGameTime, maxGameTime,
                reputation, popularity, interest, likes, dislikes, approvalRatePercent, playerVote,
                loading, region,
                rtvActive, rtvVotes, rtvVotesRequired, rtvRemainingSeconds,
                adminForceConfirming, adminConfirmExpireMillis
        );
    }

    public MapUiModel withReputation(Boolean newVote, int newReputation, int newLikes, int newDislikes, int newApprovalRate) {
        return new MapUiModel(
                mode, playerUuid, isAdmin,
                searchQuery, page, totalPages, displayedMaps, totalMapsCount,
                selectedMapId, mapName, mapAuthor, mapDescription, width, height, gamemodeName, isCurrentMap,
                playedTimes, playedTimesYear, lastPlayedFormatted, minGameTime, avgGameTime, maxGameTime,
                newReputation, popularity, interest, newLikes, newDislikes, newApprovalRate, newVote,
                previewLoading, previewTextureRegion,
                rtvActive, rtvVotes, rtvVotesRequired, rtvRemainingSeconds,
                adminForceConfirming, adminConfirmExpireMillis
        );
    }

    public MapUiModel withRtvStatus(boolean active, int votes, int required, int seconds) {
        return new MapUiModel(
                mode, playerUuid, isAdmin,
                searchQuery, page, totalPages, displayedMaps, totalMapsCount,
                selectedMapId, mapName, mapAuthor, mapDescription, width, height, gamemodeName, isCurrentMap,
                playedTimes, playedTimesYear, lastPlayedFormatted, minGameTime, avgGameTime, maxGameTime,
                reputation, popularity, interest, likes, dislikes, approvalRatePercent, playerVote,
                previewLoading, previewTextureRegion,
                active, votes, required, seconds,
                adminForceConfirming, adminConfirmExpireMillis
        );
    }

    public MapUiModel withAdminConfirm(boolean confirming, long expireMillis) {
        return new MapUiModel(
                mode, playerUuid, isAdmin,
                searchQuery, page, totalPages, displayedMaps, totalMapsCount,
                selectedMapId, mapName, mapAuthor, mapDescription, width, height, gamemodeName, isCurrentMap,
                playedTimes, playedTimesYear, lastPlayedFormatted, minGameTime, avgGameTime, maxGameTime,
                reputation, popularity, interest, likes, dislikes, approvalRatePercent, playerVote,
                previewLoading, previewTextureRegion,
                rtvActive, rtvVotes, rtvVotesRequired, rtvRemainingSeconds,
                confirming, expireMillis
        );
    }
}
