package org.xcore.plugin.ui.menu.map;

/**
 * Explicit discriminated sealed commands for UI side-effects (MVI / Elm architecture).
 * Isolates side-effects (database queries, network requests, RTV subscriptions) from pure state reducers.
 */
public sealed interface MapUiCmd {
    record LoadAllSummaries(String query, int page) implements MapUiCmd {}
    record LoadMapDetails(String mapId) implements MapUiCmd {}
    record RequestPreview(String mapId) implements MapUiCmd {}
    record PersistReputationVote(String mapId, boolean like, boolean isRevoking) implements MapUiCmd {}
    record SubscribeRtv(String mapId, long sessionToken) implements MapUiCmd {}
    record UnsubscribeRtv(long sessionToken) implements MapUiCmd {}
    record TriggerRtv(String mapId, boolean force) implements MapUiCmd {}
    record CloseSession() implements MapUiCmd {}
}
