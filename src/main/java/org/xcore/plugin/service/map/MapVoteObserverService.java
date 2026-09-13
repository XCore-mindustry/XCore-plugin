package org.xcore.plugin.service.map;

import arc.Core;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.menu.map.MapUiEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks open Map details sessions and notifies them of live RTV vote progress.
 */
@Singleton
public class MapVoteObserverService {

    private final SessionService sessionService;
    private final Map<String, String> openDetailsMap = new ConcurrentHashMap<>(); // playerUuid -> mapId

    @Inject
    public MapVoteObserverService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public void registerViewing(String playerUuid, String mapId) {
        if (playerUuid != null && mapId != null) {
            openDetailsMap.put(playerUuid, mapId);
        }
    }

    public void unregisterViewing(String playerUuid) {
        if (playerUuid != null) {
            openDetailsMap.remove(playerUuid);
        }
    }

    @SuppressWarnings("unchecked")
    public void notifyVoteProgress(String mapId, int votes, int required, int remainingSeconds) {
        openDetailsMap.forEach((uuid, viewedMapId) -> {
            if (viewedMapId.equals(mapId)) {
                Session s = sessionService.get(uuid);
                if (s != null && s.activeUiSession() != null) {
                    Core.app.post(() -> {
                        if (s.activeUiSession() != null) {
                            ((org.xcore.ui.runtime.UiSession<?, Object>) s.activeUiSession()).dispatch(
                                    new MapUiEvent.RtvVoteUpdated(mapId, votes, required, remainingSeconds)
                            );
                        }
                    });
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    public void notifyVoteEnded(String mapId) {
        openDetailsMap.forEach((uuid, viewedMapId) -> {
            if (viewedMapId.equals(mapId)) {
                Session s = sessionService.get(uuid);
                if (s != null && s.activeUiSession() != null) {
                    Core.app.post(() -> {
                        if (s.activeUiSession() != null) {
                            ((org.xcore.ui.runtime.UiSession<?, Object>) s.activeUiSession()).dispatch(new MapUiEvent.RtvVoteEnded());
                        }
                    });
                }
            }
        });
    }
}
