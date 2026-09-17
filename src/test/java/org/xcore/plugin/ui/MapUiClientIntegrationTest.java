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
}
