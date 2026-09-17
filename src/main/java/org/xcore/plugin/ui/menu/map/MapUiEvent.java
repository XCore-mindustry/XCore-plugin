package org.xcore.plugin.ui.menu.map;

/**
 * Sealed event hierarchy for user actions and asynchronous engine callbacks in MapUiController.
 */
public sealed interface MapUiEvent {

    // --- Browser Actions ---
    record SearchChanged(String query) implements MapUiEvent {}
    record NextPage() implements MapUiEvent {}
    record PrevPage() implements MapUiEvent {}
    record ChangePage(int newPage) implements MapUiEvent {}
    record OpenMapDetails(String mapId) implements MapUiEvent {}

    // --- Details Actions ---
    record BackToBrowser() implements MapUiEvent {}
    record ToggleReputation(boolean like) implements MapUiEvent {}
    record TriggerRtv() implements MapUiEvent {}
    record AdminForceRtvClick() implements MapUiEvent {}
    record AdminCancelForceRtv() implements MapUiEvent {}

    // --- Async & Live Push Callbacks ---
    record SummariesReady() implements MapUiEvent {}
    record DetailsReady(String mapId, org.xcore.plugin.model.MapData data) implements MapUiEvent {}
    record DetailsFailed(String mapId) implements MapUiEvent {}
    record PreviewReady(String mapId, String textureRegion) implements MapUiEvent {}
    record PreviewFailed(String mapId) implements MapUiEvent {}
    record RtvVoteUpdated(String mapId, int votes, int required, int remainingSeconds) implements MapUiEvent {}
    record RtvVoteEnded() implements MapUiEvent {}

    // --- Window Controls ---
    record Close() implements MapUiEvent {}
}
