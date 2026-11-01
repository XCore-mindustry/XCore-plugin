package org.xcore.plugin.service.map;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.concurrent.MainThreadDispatcher;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.menu.map.MapUiEvent;
import org.xcore.plugin.ui.menu.map.MapUiModel;
import org.xcore.ui.runtime.UiSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks exact open Map UI sessions and notifies them of live RTV vote progress. */
@Singleton
public class MapVoteObserverService {

    private final SessionService sessionService;
    private final MainThreadDispatcher mainThread;
    private final Map<String, Viewing> openDetailsMap = new ConcurrentHashMap<>();

    @Inject
    public MapVoteObserverService(SessionService sessionService) {
        this(sessionService, MainThreadDispatcher.mindustry());
    }

    MapVoteObserverService(SessionService sessionService, MainThreadDispatcher mainThread) {
        this.sessionService = sessionService;
        this.mainThread = mainThread;
    }

    public void registerViewing(String playerUuid, String mapId) {
        if (playerUuid == null || mapId == null) {
            return;
        }
        Session session = sessionService.get(playerUuid);
        UiSession<?, ?> ui = session == null ? null : session.activeUiSession();
        if (ui == null || !(ui.model() instanceof MapUiModel)) {
            return;
        }
        Viewing viewing = new Viewing(session, ui, ui.token(), mapId);
        // Registration can happen inside the browser -> details reducer, before its new model is committed.
        if (isCurrentSession(playerUuid, viewing)) {
            openDetailsMap.put(playerUuid, viewing);
        }
    }

    public void unregisterViewing(String playerUuid) {
        if (playerUuid != null) {
            openDetailsMap.remove(playerUuid);
        }
    }

    public void notifyVoteProgress(String mapId, int votes, int required, int remainingSeconds) {
        notifyViewers(mapId, new MapUiEvent.RtvVoteUpdated(mapId, votes, required, remainingSeconds));
    }

    public void notifyVoteEnded(String mapId) {
        notifyViewers(mapId, new MapUiEvent.RtvVoteEnded());
    }

    private void notifyViewers(String mapId, MapUiEvent event) {
        openDetailsMap.forEach((uuid, viewing) -> {
            if (!isCurrentSession(uuid, viewing)) {
                openDetailsMap.remove(uuid, viewing);
            } else if (viewing.mapId.equals(mapId)) {
                mainThread.execute(() -> dispatch(uuid, viewing, event));
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void dispatch(String uuid, Viewing viewing, MapUiEvent event) {
        if (openDetailsMap.get(uuid) != viewing) {
            return;
        }
        if (!isCurrentSession(uuid, viewing)
                || !(viewing.ui.model() instanceof MapUiModel model)
                || model.mode() != MapUiModel.ViewMode.DETAILS
                || !viewing.mapId.equals(model.selectedMapId())) {
            openDetailsMap.remove(uuid, viewing);
            return;
        }
        ((UiSession<MapUiModel, MapUiEvent>) viewing.ui).dispatch(event);
    }

    private boolean isCurrentSession(String uuid, Viewing viewing) {
        return sessionService.get(uuid) == viewing.session
                && viewing.session.activeUiSession() == viewing.ui
                && viewing.ui.token() == viewing.token
                && viewing.ui.model() instanceof MapUiModel;
    }

    // Identity equality is intentional: re-registering the same UI must still supersede old callbacks.
    private static final class Viewing {
        private final Session session;
        private final UiSession<?, ?> ui;
        private final long token;
        private final String mapId;

        private Viewing(Session session, UiSession<?, ?> ui, long token, String mapId) {
            this.session = session;
            this.ui = ui;
            this.token = token;
            this.mapId = mapId;
        }
    }
}
