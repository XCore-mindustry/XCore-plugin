package org.xcore.plugin.ui;

import arc.Core;
import arc.files.Fi;
import arc.mock.MockApplication;
import arc.struct.Seq;
import arc.struct.StringMap;
import com.ospx.flubundle.Bundle;
import jakarta.inject.Provider;
import mindustry.core.GameState;
import mindustry.gen.Player;
import mindustry.maps.Map;
import mindustry.net.NetConnection;
import mindustry.ui.builder.MenuResult;
import mindustry.ui.builder.UiBuilder.NodeBuilder;
import mindustry.ui.builder.UiDslWriter;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.MapService;
import org.xcore.plugin.service.map.MapPreviewService;
import org.xcore.plugin.service.map.MapSummaryCache;
import org.xcore.plugin.service.map.MapVoteObserverService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.menu.MapMenu;
import org.xcore.plugin.ui.menu.map.MapUiController;
import org.xcore.plugin.ui.menu.map.MapUiModel;
import org.xcore.testkit.ui.DeterministicUiLoop;
import org.xcore.testkit.ui.UiSnapshot;
import org.xcore.testkit.ui.UiWireMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static mindustry.Vars.state;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Full end-to-end integration test driving MenuService, UiSession, and MapUiController
 * through DeterministicUiLoop and HeadlessMenuClient.
 * Covers scenarios UI-01, UI-02, UI-04, UI-08 from the UI test harness architecture.
 */
class MapUiClientIntegrationTest {

    private GameState originalState;
    private DeterministicUiLoop loop;
    private MenuService menuService;
    private MapService mapService;
    private MapDataRepository mapDataRepository;
    private MapPreviewService previewService;
    private MapVoteObserverService observerService;
    private MapSummaryCache summaryCache;
    private Session session;
    private SessionService sessionService;
    private MapMenu mapMenu;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        originalState = state;
        state = new GameState();

        loop = new DeterministicUiLoop();

        // Direct Core.app.post into loop.serverPost() for snapshot-drain turn execution
        Core.app = new MockApplication() {
            @Override
            public void post(Runnable runnable) {
                loop.serverPost().post(runnable);
            }
        };

        mapService = mock(MapService.class);
        mapDataRepository = mock(MapDataRepository.class);
        previewService = mock(MapPreviewService.class);
        observerService = mock(MapVoteObserverService.class);
        summaryCache = new MapSummaryCache(mapDataRepository);

        PlayerData data = new PlayerData("test-uuid", true);
        data.mapVotes = new HashMap<>();

        Bundle bundle = mock(Bundle.class);
        com.ospx.flubundle.Localizer localizer = mock(com.ospx.flubundle.Localizer.class);
        when(localizer.locale()).thenReturn(Locale.ENGLISH);
        when(localizer.format(anyString())).thenAnswer(i -> i.getArgument(0));
        when(localizer.format(anyString(), anyMap())).thenAnswer(i -> i.getArgument(0));
        when(bundle.localizer(any(java.util.function.Supplier.class))).thenReturn(localizer);

        sessionService = mock(SessionService.class);
        Provider<SessionService> sessionProvider = () -> sessionService;

        // Custom gateway piping into DeterministicUiLoop
        MindustryMenuGateway gateway = new MindustryMenuGateway() {
            @Override public void menu(Player p, int id, String t, String c, String[][] b) {}
            @Override public void followUpMenu(Player p, int id, String t, String c, String[][] b) {}
            @Override public void hideFollowUpMenu(Player p, int id) {}
            @Override public void textInput(Player p, int id, String t, String c, int l, String d, boolean n) {}
            @Override public void openUri(Player p, String u) {}
            @Override public void copyToClipboard(Player p, String t) {}

            @Override
            public void menuBuilder(Player player, int menuId, long token, String title,
                                    boolean hideOnClick, boolean hideExisting, boolean fillScreen, NodeBuilder<?> ui) {
                loop.sendServerToClient(new UiWireMessage.Show(menuId, token, hideExisting, UiSnapshot.capture(ui)));
            }

            @Override
            public void menuBuilderUpdate(Player player, int menuId, String tableId, NodeBuilder<?> ui) {
                loop.sendServerToClient(new UiWireMessage.Update(menuId, tableId, UiSnapshot.capture(ui)));
            }

            @Override
            public void hideMenuBuilder(Player player, int menuId) {
                loop.sendServerToClient(new UiWireMessage.Hide(menuId));
            }
        };

        menuService = new MenuService(sessionProvider, gateway);
        menuService.init();

        Player player = Player.create();
        player.con = mock(NetConnection.class);

        session = new Session(
                new TomlSecretsConfig(),
                bundle,
                menuService,
                mock(PlayerDataRepository.class),
                player,
                data
        );
        when(sessionService.get(anyString())).thenReturn(session);

        // Wire client->server packet delivery into MenuService
        loop.onClientMessage(msg -> {
            if (msg instanceof UiWireMessage.Choose choose) {
                var result = new MenuResult(choose.action());
                result.token = choose.token();
                menuService.onMenuBuilderResult(session, result);
            }
        });

        TomlXcoreConfig xcoreConfig = new TomlXcoreConfig();
        xcoreConfig.normalize();
        TomlSecretsConfig secretsConfig = new TomlSecretsConfig();
        secretsConfig.pagination.mapsPerPage = 10;

        mapMenu = new MapMenu(
                xcoreConfig, secretsConfig, sessionService,
                mapDataRepository, null, mapService, null, menuService,
                previewService, observerService, null, summaryCache
        );
    }

    @AfterEach
    void tearDown() {
        state = originalState;
        Core.app = null;
    }

    @Test
    @DisplayName("UI-01: Open browser -> click map -> details dialog opens and renders metadata")
    void ui01_openBrowserThenClickMapOpensDetails() {
        var engineMap = new Map(new Fi("arena.msav"), 100, 100, StringMap.of("name", "Arena", "author", "Anuke"), true);
        when(mapService.getAvailableMaps()).thenReturn(new Seq<>(new Map[]{engineMap}));
        when(mapService.findMapByFileName("arena.msav")).thenReturn(engineMap);

        var savedData = new MapData("Arena", "arena.msav", "Anuke", "survival");
        savedData.id = new ObjectId();
        savedData.playedTimes = 42;
        when(mapDataRepository.findExistingAsync("Arena", "arena.msav", "Anuke", "survival"))
                .thenReturn(CompletableFuture.completedFuture(savedData));

        // 1. Open maps browser UI
        mapMenu.openMapBrowserUi(session, 1);
        int menuId = menuService.getMenuBuilderId();

        // Wire queue has the Show message; client hasn't received it yet
        assertThat(loop.client().isVisible(menuId)).isFalse();

        // Step server->client: browser appears on client
        assertThat(loop.stepServerToClient()).isTrue();
        assertThat(loop.client().isVisible(menuId)).isTrue();
        assertThat(session.hasActiveUiSession()).isTrue();

        // 2. Client clicks "arena.msav"
        loop.client().click(menuId, "action:select_map:arena.msav");

        // Step client->server: server receives selection
        assertThat(loop.stepClientToServer()).isTrue();

        // Drain server post tasks:
        // Turn 1 runs requestDetailsAsync -> completes and posts to MainThreadDispatcher
        loop.stepServerPost();
        // Turn 2 executes the completion callback -> dispatches DetailsReady -> rerenders
        loop.stepServerPost();

        // Step server->client: details window replaces browser
        assertThat(loop.stepServerToClient()).isTrue();

        // Client is still visible with details
        assertThat(loop.client().isVisible(menuId)).isTrue();
        var model = (MapUiModel) session.activeUiSession().model();
        assertThat(model.mode()).isEqualTo(MapUiModel.ViewMode.DETAILS);
        assertThat(model.selectedMapId()).isEqualTo("arena.msav");
        assertThat(model.playedTimes()).isEqualTo(42);
    }

    @Test
    @DisplayName("UI-02: /maps with pending summaries shows browser immediately and patches slot on arrival")
    void ui02_mapsWithPendingSummariesShowsBrowserImmediatelyAndPatchesSlot() {
        var engineMap = new Map(new Fi("desert.msav"), 150, 150, StringMap.of("name", "Desert", "author", "Anuke"), true);
        when(mapService.getAvailableMaps()).thenReturn(new Seq<>(new Map[]{engineMap}));
        when(mapService.findMapByFileName("desert.msav")).thenReturn(engineMap);

        // Async findAllAsync that is NOT completed yet
        var pendingRows = new CompletableFuture<List<MapData>>();
        when(mapDataRepository.findAllAsync()).thenReturn(pendingRows);

        // 1. Open maps browser
        mapMenu.openMapBrowserUi(session, 1);
        int menuId = menuService.getMenuBuilderId();

        // Browser is queued IMMEDIATELY without waiting for DB
        assertThat(loop.stepServerToClient()).isTrue();
        assertThat(loop.client().isVisible(menuId)).isTrue();
        var initialModel = (MapUiModel) session.activeUiSession().model();
        assertThat(initialModel.mode()).isEqualTo(MapUiModel.ViewMode.BROWSER);

        // 2. Now DB query completes in background
        var data = new MapData("Desert", "desert.msav", "Anuke", "survival");
        data.like = 10;
        data.dislike = 2;
        pendingRows.complete(List.of(data));

        // Drain server-post (dispatcher moves result to main thread)
        loop.stepServerPost();

        // 3. Server emits Update (patching SLOT_MAP_TABLE), NOT a full Show!
        assertThat(loop.stepServerToClient()).isTrue();
        var lastMsg = loop.transcript().get(loop.transcript().size() - 1);
        assertThat(lastMsg).isInstanceOf(UiWireMessage.Update.class);
        var update = (UiWireMessage.Update) lastMsg;
        assertThat(update.targetId()).isEqualTo("slot_map_table");
    }

    @Test
    @DisplayName("UI-03: Immediate and delayed details yield equivalent model and client wire tree")
    void ui03_immediateAndDelayedDetailsYieldEquivalentModelAndTree() {
        var engineMap = new Map(new Fi("canyon.msav"), 120, 120, StringMap.of("name", "Canyon", "author", "Echo"), true);
        when(mapService.findMapByFileName("canyon.msav")).thenReturn(engineMap);
        when(mapService.getAvailableMaps()).thenReturn(new Seq<>(new Map[]{engineMap}));

        var savedData = new MapData("Canyon", "canyon.msav", "Echo", "survival");
        savedData.id = new ObjectId();
        savedData.playedTimes = 77;
        savedData.like = 30;
        savedData.dislike = 3;
        when(mapService.findPersistedMap(savedData)).thenReturn(engineMap);

        // Path 1: Immediate details
        mapMenu.openMapDetailsUi(session, savedData);
        loop.stepServerToClient();
        var immediateModel = (MapUiModel) session.activeUiSession().model();
        var immediateShow = lastShowMessage(loop);
        String immediateDsl = UiDslWriter.write(immediateShow.body().decode());

        // Reset session and client for Path 2
        int menuId = menuService.getMenuBuilderId();
        loop.client().hide(menuId);
        loop.stepClientToServer();
        session.clearActiveUiSession();

        // Path 2: Delayed details
        var pendingDetails = new CompletableFuture<MapData>();
        when(mapDataRepository.findExistingAsync(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(pendingDetails);
        when(mapService.getAvailableMaps()).thenReturn(new Seq<>(new Map[]{engineMap}));

        mapMenu.openMapBrowserUi(session, 1);
        loop.stepServerToClient();

        loop.client().click(menuId, "action:select_map:canyon.msav");
        loop.stepClientToServer();
        loop.stepServerPost(); // Turn 1: posts requestDetailsAsync
        loop.stepServerToClient(); // initial details view with defaults

        // Now async details resolve
        pendingDetails.complete(savedData);
        loop.stepServerPost(); // Turn 2: dispatches DetailsReady -> rerenders
        loop.stepServerToClient(); // delivers rerendered Show to client

        var delayedModel = (MapUiModel) session.activeUiSession().model();
        var delayedShow = lastShowMessage(loop);
        String delayedDsl = UiDslWriter.write(delayedShow.body().decode());

        assertThat(delayedModel.selectedMapId()).isEqualTo(immediateModel.selectedMapId());
        assertThat(delayedModel.playedTimes()).isEqualTo(immediateModel.playedTimes());
        assertThat(delayedModel.likes()).isEqualTo(immediateModel.likes());
        assertThat(delayedModel.dislikes()).isEqualTo(immediateModel.dislikes());
        assertThat(delayedDsl).isEqualTo(immediateDsl);
    }

    @Test
    @DisplayName("UI-05: Navigating away to map B discards late details from map A without mutating B")
    void ui05_navigatingAwayDiscardsLateDetailsFromPreviousMap() {
        var mapA = new Map(new Fi("mapA.msav"), 100, 100, StringMap.of("name", "MapA", "author", "A"), true);
        var mapB = new Map(new Fi("mapB.msav"), 150, 150, StringMap.of("name", "MapB", "author", "B"), true);
        when(mapService.getAvailableMaps()).thenReturn(new Seq<>(new Map[]{mapA, mapB}));
        when(mapService.findMapByFileName("mapA.msav")).thenReturn(mapA);
        when(mapService.findMapByFileName("mapB.msav")).thenReturn(mapB);

        var pendingA = new CompletableFuture<MapData>();
        when(mapDataRepository.findExistingAsync(eq("MapA"), eq("mapA.msav"), anyString(), anyString()))
                .thenReturn(pendingA);

        var savedB = new MapData("MapB", "mapB.msav", "B", "survival");
        savedB.id = new ObjectId();
        savedB.playedTimes = 88;
        when(mapDataRepository.findExistingAsync(eq("MapB"), eq("mapB.msav"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(savedB));

        // 1. Open browser and click map A
        mapMenu.openMapBrowserUi(session, 1);
        int menuId = menuService.getMenuBuilderId();
        loop.stepServerToClient();

        loop.client().click(menuId, "action:select_map:mapA.msav");
        loop.stepClientToServer();
        loop.stepServerPost(); // Turn 1: requestDetailsAsync for A pending
        loop.stepServerToClient(); // details for A (unresolved)

        // 2. User goes back to browser
        loop.client().click(menuId, "action:back_to_list");
        loop.stepClientToServer();
        loop.stepServerToClient(); // browser visible

        // 3. User clicks map B
        loop.client().click(menuId, "action:select_map:mapB.msav");
        loop.stepClientToServer();
        loop.stepServerPost(); // Turn 1 for B
        loop.stepServerPost(); // Turn 2 for B (DetailsReady for B)
        loop.stepServerToClient(); // details for B shown

        var modelB = (MapUiModel) session.activeUiSession().model();
        assertThat(modelB.selectedMapId()).isEqualTo("mapB.msav");
        assertThat(modelB.playedTimes()).isEqualTo(88);

        // 4. Stale future for map A completes NOW!
        var savedA = new MapData("MapA", "mapA.msav", "A", "survival");
        savedA.id = new ObjectId();
        savedA.playedTimes = 12;
        pendingA.complete(savedA);

        // Server-post runs the stale callback for A
        loop.stepServerPost();

        // Model MUST still be B with B's data!
        var finalModel = (MapUiModel) session.activeUiSession().model();
        assertThat(finalModel.selectedMapId()).isEqualTo("mapB.msav");
        assertThat(finalModel.playedTimes()).isEqualTo(88);
    }

    @Test
    @DisplayName("UI-06: A1 -> B -> A2 where A2 resolves before A1: stale A1 does not overwrite newer A2")
    void ui06_staleResponseDoesNotOverwriteNewerDetailsForSameMap() {
        var mapA = new Map(new Fi("mapA.msav"), 100, 100, StringMap.of("name", "MapA", "author", "A"), true);
        var mapB = new Map(new Fi("mapB.msav"), 150, 150, StringMap.of("name", "MapB", "author", "B"), true);
        when(mapService.getAvailableMaps()).thenReturn(new Seq<>(new Map[]{mapA, mapB}));
        when(mapService.findMapByFileName("mapA.msav")).thenReturn(mapA);
        when(mapService.findMapByFileName("mapB.msav")).thenReturn(mapB);

        var pendingA1 = new CompletableFuture<MapData>();
        var pendingA2 = new CompletableFuture<MapData>();

        // First query for A returns pendingA1, second query returns pendingA2
        when(mapDataRepository.findExistingAsync(eq("MapA"), eq("mapA.msav"), anyString(), anyString()))
                .thenReturn(pendingA1)
                .thenReturn(pendingA2);

        var savedB = new MapData("MapB", "mapB.msav", "B", "survival");
        when(mapDataRepository.findExistingAsync(eq("MapB"), eq("mapB.msav"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(savedB));

        // 1. Open browser -> click A (request A1 begins)
        mapMenu.openMapBrowserUi(session, 1);
        int menuId = menuService.getMenuBuilderId();
        loop.stepServerToClient();

        loop.client().click(menuId, "action:select_map:mapA.msav");
        loop.stepClientToServer();
        loop.stepServerPost(); // Turn 1: requestDetailsAsync attaches to pendingA1
        loop.stepServerToClient();

        // 2. User goes back to browser, then to B, then back to browser
        loop.client().click(menuId, "action:back_to_list");
        loop.stepClientToServer();
        loop.stepServerToClient();

        loop.client().click(menuId, "action:select_map:mapB.msav");
        loop.stepClientToServer();
        loop.stepServerPost();
        loop.stepServerToClient();

        loop.client().click(menuId, "action:back_to_list");
        loop.stepClientToServer();
        loop.stepServerToClient();

        // 3. User clicks A again (request A2 begins)
        loop.client().click(menuId, "action:select_map:mapA.msav");
        loop.stepClientToServer();
        loop.stepServerPost(); // Turn 1: requestDetailsAsync attaches to pendingA2
        loop.stepServerToClient();

        // 4. Request A2 completes FIRST with updated stats (playedTimes = 100)
        var freshA = new MapData("MapA", "mapA.msav", "A", "survival");
        freshA.id = new ObjectId();
        freshA.playedTimes = 100;
        pendingA2.complete(freshA);

        loop.stepServerPost(); // Turn 2: dispatches DetailsReady for A2
        loop.stepServerToClient();

        var modelA2 = (MapUiModel) session.activeUiSession().model();
        assertThat(modelA2.selectedMapId()).isEqualTo("mapA.msav");
        assertThat(modelA2.playedTimes()).isEqualTo(100);

        // 5. Stale request A1 completes LATER with old stats (playedTimes = 10)
        var staleA = new MapData("MapA", "mapA.msav", "A", "survival");
        staleA.id = new ObjectId();
        staleA.playedTimes = 10;
        pendingA1.complete(staleA);

        loop.stepServerPost(); // Stale completion must be ignored!

        // 6. The model MUST still have playedTimes = 100, not 10!
        var finalModel = (MapUiModel) session.activeUiSession().model();
        assertThat(finalModel.playedTimes()).isEqualTo(100);
    }

    @Test
    @DisplayName("UI-07: Closing before request-post does not leak map events into a subsequent session")
    void ui07_closingBeforePostDoesNotLeakEventsIntoSubsequentSession() {
        var mapA = new Map(new Fi("mapA.msav"), 100, 100, StringMap.of("name", "MapA", "author", "A"), true);
        when(mapService.findMapByFileName("mapA.msav")).thenReturn(mapA);

        var pendingA = new CompletableFuture<MapData>();
        when(mapDataRepository.findExistingAsync(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(pendingA);

        // Open details
        var initialA = new MapData("MapA", "mapA.msav", "A", "survival");
        mapMenu.openMapDetailsUi(session, initialA);
        int menuId = menuService.getMenuBuilderId();
        loop.stepServerToClient();

        // Client explicitly closes the menu
        loop.client().click(menuId, "action:close");
        loop.stepClientToServer();
        assertThat(session.hasActiveUiSession()).isFalse();

        // Open a completely new session (e.g. settings or another dialog)
        record ForeignModel(String value) {}
        class ForeignController implements org.xcore.ui.runtime.UiController<ForeignModel, Void> {
            @Override public ForeignModel initialModel(Object ctx) { return new ForeignModel("intact"); }
            @Override public org.xcore.ui.runtime.UpdateResult<ForeignModel> update(ForeignModel m, Void e, org.xcore.ui.runtime.ControllerContext c) { return org.xcore.ui.runtime.UpdateResult.of(m); }
            @Override public org.xcore.ui.VNode render(ForeignModel m) { return org.xcore.ui.Ui.table(t -> t.label(org.xcore.ui.Text.raw(m.value()))); }
            @Override public Void parseEvent(MenuResult r) { return null; }
        }

        var newController = new ForeignController();
        var newContext = new org.xcore.ui.runtime.ControllerContext() {
            @Override public String playerId() { return "test-uuid"; }
            @Override public void close() {}
        };
        var newGateway = new org.xcore.ui.runtime.UiSession.DeliveryGateway() {
            @Override public void show(String p, long t, NodeBuilder<?> u) {}
            @Override public void update(String p, long t, String e, NodeBuilder<?> u) {}
            @Override public void hide(String p) {}
        };
        var newSession = org.xcore.ui.runtime.UiSession.start(newController, newController.initialModel(null),
                newContext, newGateway, org.xcore.ui.LocalizerResolver.IDENTITY);
        session.setActiveUiSession(newSession);

        // Now map A's async query completes while the new session is active
        var lateData = new MapData("MapA", "mapA.msav", "A", "survival");
        lateData.playedTimes = 999;
        pendingA.complete(lateData);

        // Step server post: target != session.activeUiSession() must suppress dispatch!
        loop.stepServerPost();

        // The new session is uncorrupted and still has its foreign model
        assertThat(session.activeUiSession()).isSameAs(newSession);
        assertThat(newSession.model()).isEqualTo(new ForeignModel("intact"));
    }

    @Test
    @DisplayName("UI-04: Full rerender replacement cancel does NOT close the details card")
    void ui04_fullRerenderReplacementCancelDoesNotCloseCard() {
        var engineMap = new Map(new Fi("glacier.msav"), 200, 200, StringMap.of("name", "Glacier", "author", "Delta"), true);
        when(mapService.findMapByFileName("glacier.msav")).thenReturn(engineMap);

        var savedData = new MapData("Glacier", "glacier.msav", "Delta", "survival");
        savedData.id = new ObjectId();
        savedData.playedTimes = 15;

        // Open details directly
        mapMenu.openMapDetailsUi(session, savedData);
        int menuId = menuService.getMenuBuilderId();

        // Deliver show to client
        assertThat(loop.stepServerToClient()).isTrue();
        assertThat(loop.client().isVisible(menuId)).isTrue();

        // Simulate async details arrival triggering full rerender (same token, hideExisting=true)
        session.activeUiSession().open();

        // Step server->client: second show reaches client; client replacement emits cancel for old window
        assertThat(loop.stepServerToClient()).isTrue();

        // Step client->server: old window cancel reaches server
        assertThat(loop.stepClientToServer()).isTrue();

        // Dialog MUST NOT close (d16772b fix verified end-to-end!)
        assertThat(loop.client().isVisible(menuId)).isTrue();
        assertThat(session.hasActiveUiSession()).isTrue();
    }

    @Test
    @DisplayName("UI-08: Explicit close hides dialog, clears session, and disables follow-ups")
    void ui08_explicitCloseHidesDialogAndClearsSession() {
        var engineMap = new Map(new Fi("glacier.msav"), 200, 200, StringMap.of("name", "Glacier", "author", "Delta"), true);
        when(mapService.findMapByFileName("glacier.msav")).thenReturn(engineMap);
        var savedData = new MapData("Glacier", "glacier.msav", "Delta", "survival");

        mapMenu.openMapDetailsUi(session, savedData);
        int menuId = menuService.getMenuBuilderId();
        loop.stepServerToClient();

        assertThat(loop.client().isVisible(menuId)).isTrue();
        assertThat(session.hasActiveUiSession()).isTrue();

        // Client clicks the explicit close button
        loop.client().click(menuId, "action:close");
        loop.stepClientToServer();

        // Server processed close: session cleared, hide message sent
        assertThat(session.hasActiveUiSession()).isFalse();

        // Step server->client: hide delivered to client
        loop.stepServerToClient();
        assertThat(loop.client().isVisible(menuId)).isFalse();
    }

    @Test
    @DisplayName("UI-09: Live RTV update is not wiped when delayed DetailsReady arrives")
    void ui09_liveRtvUpdateNotWipedByDelayedDetailsReady() {
        var engineMap = new Map(new Fi("arena.msav"), 100, 100, StringMap.of("name", "Arena", "author", "Anuke"), true);
        when(mapService.findMapByFileName("arena.msav")).thenReturn(engineMap);
        when(mapService.findPersistedMap(any())).thenReturn(engineMap);

        var pendingDetails = new CompletableFuture<MapData>();
        when(mapDataRepository.findExistingAsync(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(pendingDetails);
        when(mapService.getAvailableMaps()).thenReturn(new Seq<>(new Map[]{engineMap}));

        mapMenu.openMapBrowserUi(session, 1);
        int menuId = menuService.getMenuBuilderId();
        loop.stepServerToClient();

        // Click map to enter details (triggers requestDetailsAsync)
        loop.client().click(menuId, "action:select_map:arena.msav");
        loop.stepClientToServer();
        loop.stepServerPost(); // Turn 1: requestDetailsAsync starts and attaches to pendingDetails
        loop.stepServerToClient(); // initial details view rendered

        // 1. Live RTV event arrives while details are still loading
        @SuppressWarnings("unchecked")
        var ui = (org.xcore.ui.runtime.UiSession<MapUiModel, org.xcore.plugin.ui.menu.map.MapUiEvent>) session.activeUiSession();
        ui.dispatch(new org.xcore.plugin.ui.menu.map.MapUiEvent.RtvVoteUpdated("arena.msav", 4, 6, 15));
        var rtvModel = ui.model();
        assertThat(rtvModel.rtvActive()).isTrue();
        assertThat(rtvModel.rtvVotes()).isEqualTo(4);
        assertThat(rtvModel.rtvVotesRequired()).isEqualTo(6);
        assertThat(rtvModel.rtvRemainingSeconds()).isEqualTo(15);

        // 2. Now async details arrive with stats
        var savedData = new MapData("Arena", "arena.msav", "Anuke", "survival");
        savedData.id = new ObjectId();
        savedData.playedTimes = 50;
        pendingDetails.complete(savedData);

        // Turn 2: dispatches DetailsReady
        loop.stepServerPost();

        // 3. Stats must be updated, BUT live RTV progress must NOT be wiped!
        var updatedModel = ui.model();
        assertThat(updatedModel.playedTimes()).isEqualTo(50);
        assertThat(updatedModel.rtvActive()).isTrue();
        assertThat(updatedModel.rtvVotes()).isEqualTo(4);
        assertThat(updatedModel.rtvVotesRequired()).isEqualTo(6);
        assertThat(updatedModel.rtvRemainingSeconds()).isEqualTo(15);
    }

    private static UiWireMessage.Show lastShowMessage(DeterministicUiLoop loop) {
        return loop.transcript().all().stream()
                .filter(m -> m instanceof UiWireMessage.Show)
                .map(m -> (UiWireMessage.Show) m)
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("No Show message in transcript"));
    }
}
