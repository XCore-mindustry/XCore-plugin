package org.xcore.plugin.service.map;

import com.ospx.flubundle.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.menu.map.MapUiController;
import org.xcore.plugin.ui.menu.map.MapUiEvent;
import org.xcore.plugin.ui.menu.map.MapUiModel;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.UiController;
import org.xcore.ui.runtime.UiSession;
import org.xcore.ui.runtime.UpdateResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MapVoteObserverServiceTest {
    private static final String UUID = "player";
    private static final String MAP = "map";
    private final SessionService sessions = mock(SessionService.class);
    private final ArrayDeque<Runnable> queued = new ArrayDeque<>();
    private final Session session = new Session(null, mock(Bundle.class), null, null, null, new PlayerData(UUID, true));
    private MapVoteObserverService observer;

    @BeforeEach
    void setUp() {
        observer = new MapVoteObserverService(sessions, queued::add);
        when(sessions.get(UUID)).thenReturn(session);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void queuedNotificationDoesNotReachReplacementUi(boolean ended) {
        RecordingController original = openMap();
        observer.registerViewing(UUID, MAP);
        notifyVote(ended);
        RecordingController replacement = openMap();
        drain();
        assertTrue(original.events.isEmpty());
        assertTrue(replacement.events.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void disconnectedSessionIsPrunedWhenQueuedNotificationRuns(boolean ended) {
        RecordingController controller = openMap();
        observer.registerViewing(UUID, MAP);
        notifyVote(ended);
        when(sessions.get(UUID)).thenReturn(null);
        drain();
        assertTrue(controller.events.isEmpty());
        when(sessions.get(UUID)).thenReturn(session);
        notifyVote(ended);
        assertTrue(queued.isEmpty(), "Disconnected registration must have been removed");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void validNotificationIsDispatchedOnlyWhenMainQueueRuns(boolean ended) {
        RecordingController controller = openMap();
        observer.registerViewing(UUID, MAP);
        notifyVote(ended);
        assertEquals(1, queued.size());
        assertTrue(controller.events.isEmpty());
        drain();
        assertEquals(List.of(ended ? new MapUiEvent.RtvVoteEnded()
                : new MapUiEvent.RtvVoteUpdated(MAP, 2, 5, 30)), controller.events);
    }

    @Test
    void oldCallbackCannotDispatchToOrRemoveNewRegistration() {
        RecordingController original = openMap();
        observer.registerViewing(UUID, MAP);
        notifyVote(false);
        RecordingController replacement = openMap();
        observer.registerViewing(UUID, MAP);
        drain();
        assertTrue(original.events.isEmpty());
        assertTrue(replacement.events.isEmpty());
        notifyVote(true);
        drain();
        assertEquals(List.of(new MapUiEvent.RtvVoteEnded()), replacement.events);
    }

    @Test
    void replacingPlayerSessionInvalidatesRegistrationEvenWithSameUi() {
        RecordingController controller = openMap();
        observer.registerViewing(UUID, MAP);
        notifyVote(false);
        Session reconnected = new Session(null, mock(Bundle.class), null, null, null, new PlayerData(UUID, true));
        reconnected.setActiveUiSession(session.activeUiSession());
        when(sessions.get(UUID)).thenReturn(reconnected);
        drain();
        assertTrue(controller.events.isEmpty());
    }

    @Test
    void registeringWithoutActiveUiDoesNotSubscribeFutureUi() {
        observer.registerViewing(UUID, MAP);
        RecordingController controller = openMap();
        notifyVote(false);
        drain();
        assertTrue(controller.events.isEmpty());
    }

    @Test
    void unregisterCancelsAlreadyQueuedNotification() {
        RecordingController controller = openMap();
        observer.registerViewing(UUID, MAP);
        notifyVote(false);
        observer.unregisterViewing(UUID);
        drain();
        assertTrue(controller.events.isEmpty());
    }

    @Test
    void disconnectedRegistrationIsPrunedBeforeQueuing() {
        openMap();
        observer.registerViewing(UUID, MAP);
        when(sessions.get(UUID)).thenReturn(null);
        notifyVote(false);
        assertTrue(queued.isEmpty());
        when(sessions.get(UUID)).thenReturn(session);
        notifyVote(false);
        assertTrue(queued.isEmpty());
    }

    @Test
    void nonMapUiCannotRegisterForMapEvents() {
        UiController<String, String> otherController = mock(UiController.class);
        session.setActiveUiSession(UiSession.start(otherController, "not a map", mock(ControllerContext.class),
                mock(UiSession.DeliveryGateway.class)));
        observer.registerViewing(UUID, MAP);
        notifyVote(false);
        assertTrue(queued.isEmpty());
    }

    @Test
    void switchingToBrowserPrunesQueuedRegistration() {
        RecordingController controller = openMap();
        observer.registerViewing(UUID, MAP);
        notifyVote(false);
        controller.browser = true;
        ((UiSession<?, MapUiEvent>) session.activeUiSession()).dispatch(new MapUiEvent.BackToBrowser());
        controller.events.clear();
        drain();
        assertTrue(controller.events.isEmpty());
        notifyVote(false);
        assertTrue(queued.isEmpty());
    }

    @Test
    void reRegisteringSameUiSupersedesAlreadyQueuedEvent() {
        RecordingController controller = openMap();
        observer.registerViewing(UUID, MAP);
        notifyVote(false);
        observer.registerViewing(UUID, MAP);
        drain();
        assertTrue(controller.events.isEmpty());
        notifyVote(true);
        drain();
        assertEquals(List.of(new MapUiEvent.RtvVoteEnded()), controller.events);
    }

    private RecordingController openMap() {
        RecordingController controller = new RecordingController();
        session.setActiveUiSession(UiSession.start(controller, model(), mock(ControllerContext.class),
                mock(UiSession.DeliveryGateway.class)));
        return controller;
    }

    private void notifyVote(boolean ended) {
        if (ended) observer.notifyVoteEnded(MAP);
        else observer.notifyVoteProgress(MAP, 2, 5, 30);
    }

    private void drain() {
        while (!queued.isEmpty()) queued.remove().run();
    }

    private static MapUiModel model() {
        return new MapUiModel(MapUiModel.ViewMode.DETAILS, UUID, false,
                "", 1, 1, List.of(), 1,
                MAP, "Map", "Author", "", 10, 10, "survival", false,
                0, 0, "", "", "", "", 0, 0, 0, 0, 0, 0, null,
                false, null, false, 0, 0, 0, false, 0);
    }

    private static final class RecordingController extends MapUiController {
        private final List<MapUiEvent> events = new ArrayList<>();
        private boolean browser;

        private RecordingController() {
            super(null, null, null, null, null);
        }

        @Override
        public UpdateResult<MapUiModel> update(MapUiModel model, MapUiEvent event, ControllerContext ctx) {
            events.add(event);
            return UpdateResult.of(browser ? model.withMode(MapUiModel.ViewMode.BROWSER) : model);
        }
    }
}
