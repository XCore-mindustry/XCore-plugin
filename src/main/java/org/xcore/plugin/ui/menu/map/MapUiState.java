package org.xcore.plugin.ui.menu.map;

import org.xcore.plugin.model.MapData;

import java.util.List;

/**
 * Pure discriminated sealed union representing UI state for the Map screen (Java 25 MVI).
 * Separates the disjoint states of {@link Browser}, {@link Details}, and {@link NotFound}.
 */
public sealed interface MapUiState {

    String playerUuid();
    boolean isAdmin();
    MapUiModel toModel();

    record Browser(
            String playerUuid,
            boolean isAdmin,
            String searchQuery,
            int page,
            int totalPages,
            List<MapSummary> displayedMaps,
            int totalMapsCount,
            boolean loading
    ) implements MapUiState {
        @Override
        public MapUiModel toModel() {
            List<MapUiModel.MapSummary> summaries = displayedMaps == null ? List.of() : displayedMaps.stream()
                    .map(s -> new MapUiModel.MapSummary(s.id(), s.name(), s.author(), s.width(), s.height(), s.likes(), s.dislikes(), s.isCurrent()))
                    .toList();
            return new MapUiModel(
                    MapUiModel.ViewMode.BROWSER,
                    playerUuid,
                    isAdmin,
                    searchQuery,
                    page,
                    totalPages,
                    summaries,
                    totalMapsCount,
                    "", "", "", "", 0, 0, "", false,
                    0, 0, "", "", "", "",
                    0, 0.0, 0.0, 0, 0, 0, null,
                    false, null,
                    false, 0, 0, 0,
                    false, 0L,
                    null
            );
        }
    }

    record Details(
            String playerUuid,
            boolean isAdmin,
            String mapId,
            MapIdentity identity,
            TelemetryMatrix telemetry,
            ReputationState reputation,
            PreviewState preview,
            RtvState rtv,
            AdminState admin,
            MapData resolvedDetails
    ) implements MapUiState {
        @Override
        public MapUiModel toModel() {
            return new MapUiModel(
                    MapUiModel.ViewMode.DETAILS,
                    playerUuid,
                    isAdmin,
                    "",
                    1,
                    1,
                    List.of(),
                    0,
                    mapId,
                    identity != null ? identity.name() : "",
                    identity != null ? identity.author() : "",
                    identity != null ? identity.description() : "",
                    identity != null ? identity.width() : 0,
                    identity != null ? identity.height() : 0,
                    identity != null ? identity.mode() : "",
                    identity != null && identity.isCurrent(),
                    telemetry != null ? telemetry.plays() : 0L,
                    telemetry != null ? telemetry.playsYear() : 0L,
                    telemetry != null ? telemetry.lastPlayed() : "",
                    telemetry != null ? telemetry.minTime() : "-",
                    telemetry != null ? telemetry.avgTime() : "-",
                    telemetry != null ? telemetry.maxTime() : "-",
                    telemetry != null ? telemetry.reputation() : 0,
                    telemetry != null ? telemetry.popularity() : 0.0,
                    telemetry != null ? telemetry.interest() : 0.0,
                    reputation != null ? reputation.likes() : 0,
                    reputation != null ? reputation.dislikes() : 0,
                    reputation != null ? reputation.approvalPercent() : 0,
                    reputation != null ? reputation.playerVote() : null,
                    preview != null && preview.loading(),
                    preview != null ? preview.textureRegion() : null,
                    rtv != null && rtv.active(),
                    rtv != null ? rtv.votes() : 0,
                    rtv != null ? rtv.required() : 0,
                    rtv != null ? rtv.remainingSeconds() : 0,
                    admin != null && admin.confirming(),
                    admin != null ? admin.confirmExpireMillis() : 0L,
                    resolvedDetails
            );
        }
    }

    record NotFound(
            String playerUuid,
            boolean isAdmin,
            String mapId,
            String reason
    ) implements MapUiState {
        @Override
        public MapUiModel toModel() {
            return new MapUiModel(
                    MapUiModel.ViewMode.DETAILS,
                    playerUuid,
                    isAdmin,
                    "", 1, 1, List.of(), 0,
                    mapId, "Not Found", "Unknown", reason != null ? reason : "",
                    0, 0, "", false,
                    0, 0, "", "-", "-", "-",
                    0, 0.0, 0.0, 0, 0, 0, null,
                    false, null, false, 0, 0, 0, false, 0L, null
            );
        }
    }

    record MapSummary(String id, String name, String author, int width, int height, int likes, int dislikes, boolean isCurrent) {}
    record MapIdentity(String name, String author, String description, int width, int height, String mode, boolean isCurrent) {}
    record TelemetryMatrix(long plays, long playsYear, String lastPlayed, String minTime, String avgTime, String maxTime, int reputation, double popularity, double interest) {}
    record ReputationState(Boolean playerVote, int likes, int dislikes, int approvalPercent, boolean pendingSync) {}
    record PreviewState(boolean loading, String textureRegion, boolean failed) {}
    record RtvState(boolean active, int votes, int required, int remainingSeconds) {}
    record AdminState(boolean isAdmin, boolean confirming, long confirmExpireMillis) {}
}
