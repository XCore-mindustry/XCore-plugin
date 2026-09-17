package org.xcore.plugin.ui.menu.map;

import com.ospx.flubundle.Bundle;
import arc.files.Fi;
import arc.struct.ObjectMap;
import arc.struct.StringMap;
import mindustry.core.GameState;
import mindustry.maps.Map;
import mindustry.ui.builder.MenuResult;
import mindustry.ui.builder.UiDslWriter;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.MapService;
import org.xcore.plugin.service.map.MapPreviewService;
import org.xcore.plugin.service.map.MapVoteObserverService;
import org.xcore.plugin.session.Session;
import org.xcore.ui.LocalizerResolver;
import org.xcore.ui.VNode;
import org.xcore.ui.VNodeCompiler;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.UpdateResult;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static mindustry.Vars.state;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MapUiControllerTest {

    private MapService mapService;
    private MapDataRepository mapDataRepository;
    private MapPreviewService previewService;
    private MapVoteObserverService observerService;
    private Session session;
    private GameState originalState;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        originalState = state;
        state = new GameState();

        mapService = mock(MapService.class);
        mapDataRepository = mock(MapDataRepository.class);
        previewService = mock(MapPreviewService.class);
        observerService = mock(MapVoteObserverService.class);

        PlayerData data = new PlayerData("test-uuid", true);
        data.mapVotes = new HashMap<>();

        Bundle bundle = mock(Bundle.class);
        com.ospx.flubundle.Localizer localizer = mock(com.ospx.flubundle.Localizer.class);
        when(localizer.locale()).thenReturn(Locale.ENGLISH);
        when(localizer.format(anyString())).thenAnswer(i -> i.getArgument(0));
        when(localizer.format(anyString(), anyMap())).thenAnswer(i -> i.getArgument(0));
        when(bundle.localizer(any(java.util.function.Supplier.class))).thenReturn(localizer);

        session = new Session(
                new TomlSecretsConfig(),
                bundle,
                null,
                mock(PlayerDataRepository.class),
                null,
                data
        );
    }

    @AfterEach
    void tearDown() {
        state = originalState;
    }

    @Test
    void resolvingMapDefersHashPersistenceUntilFileIsRead() throws Exception {
        var file = mock(Fi.class);
        when(file.name()).thenReturn("test.msav");
        when(file.read()).thenReturn(new java.io.ByteArrayInputStream(new byte[]{97, 98, 99}));
        var engineMap = new Map(file, 1, 1, new StringMap(), true);
        var data = new MapData("test", "test.msav", "author", "survival");
        data.id = new ObjectId();
        when(mapService.findMapByFileName("test.msav")).thenReturn(engineMap);
        when(mapDataRepository.findExistingAsync(anyString(), eq("test.msav"), anyString(), anyString()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(data));
        var tasks = new java.util.ArrayDeque<Runnable>();
        var executor = mock(org.xcore.plugin.concurrent.StorageExecutor.class, CALLS_REAL_METHODS);
        doAnswer(call -> { tasks.add(call.getArgument(0)); return null; }).when(executor).execute(any());
        var hashService = new org.xcore.plugin.service.map.MapContentHashService(executor);
        when(mapDataRepository.updateMapContentHashAsync(any(), anyString(), any()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(true));
        var controller = new MapUiController(mapService, mapDataRepository, previewService, observerService, session, hashService);
        var resolve = MapUiController.class.getDeclaredMethod("resolveMapData", String.class);
        resolve.setAccessible(true);

        var resolved = (java.util.concurrent.CompletionStage<?>) resolve.invoke(controller, "test.msav");
        assertThat(resolved.toCompletableFuture().join()).isSameAs(data);
        verify(file, never()).read();
        verify(mapDataRepository, never()).updateMapContentHashAsync(any(), anyString(), any());
        tasks.remove().run();
        verify(mapDataRepository).updateMapContentHashAsync(eq(data.id), eq("test.msav"),
                eq(org.xcore.plugin.map.domain.MapContentHash.fromHex("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")));
        assertThat(data.contentHash).isNull(); // worker must not mutate shared state
    }

    @Test
    @SuppressWarnings("unchecked")
    void previewCompletionNeverDispatchesIntoReplacementDialog() {
        var map = new Map(new Fi("arena.msav"), 10, 10, StringMap.of("name", "Arena"), true);
        when(mapService.findMapByFileName("arena.msav")).thenReturn(map);
        session.player = mindustry.gen.Player.create();
        var original = mock(org.xcore.ui.runtime.UiSession.class);
        var replacement = mock(org.xcore.ui.runtime.UiSession.class);
        session.setActiveUiSession(original);
        var controller = new MapUiController(mapService, mapDataRepository, previewService, observerService, session);
        controller.requestPreviewAsync(session, "arena.msav");
        var callback = org.mockito.ArgumentCaptor.forClass(java.util.function.BiConsumer.class);
        verify(previewService).requestPreview(any(), eq(map), callback.capture());
        session.setActiveUiSession(replacement);
        callback.getValue().accept("texture", null);
        verify(replacement, never()).dispatch(any());
        verify(original, never()).dispatch(any());
    }

    @Test
    void openingCardThroughUiSessionShowsSavedStatsWhenReadCompletesImmediately() {
        var engineMap = new Map(new Fi("arena.msav"), 150, 200,
                StringMap.of("name", "Arena", "author", "Author"), true);
        when(mapService.findMapByFileName("arena.msav")).thenReturn(engineMap);
        var saved = new MapData("Arena", "arena.msav", "Author", "survival");
        saved.id = new ObjectId();
        saved.playedTimes = 25;
        saved.like = 18;
        saved.dislike = 2;
        saved.averageGameTime = 1_200_000L;
        when(mapDataRepository.findExistingAsync("Arena", "arena.msav", "Author", "survival"))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(saved));
        var followUps = new java.util.ArrayDeque<Runnable>();
        var context = new ControllerContext() {
            public String playerId() { return "test-uuid"; }
            public void close() {}
            public void post(Runnable task) { followUps.add(task); }
        };
        var controller = new MapUiController(mapService, mapDataRepository, previewService, observerService, session);
        var ui = org.xcore.ui.runtime.UiSession.start(controller, createTestBrowserModel(), context,
                mock(org.xcore.ui.runtime.UiSession.DeliveryGateway.class), LocalizerResolver.IDENTITY);
        session.setActiveUiSession(ui);

        ui.handle(new MenuResult("action:select_map:arena.msav"));
        while (!followUps.isEmpty()) followUps.remove().run();

        assertThat(ui.model().mode()).isEqualTo(MapUiModel.ViewMode.DETAILS);
        assertThat(ui.model().selectedMapId()).isEqualTo("arena.msav");
        assertThat(ui.model().playedTimes()).isEqualTo(25);
        assertThat(ui.model().likes()).isEqualTo(18);
        assertThat(ui.model().dislikes()).isEqualTo(2);
        assertThat(ui.model().avgGameTime()).isNotEqualTo("-");
        verify(mapDataRepository, never()).findOrCreate(anyString(), anyString(), anyString(), anyString());
    }

    private MapUiModel createTestBrowserModel() {
        return new MapUiModel(
                MapUiModel.ViewMode.BROWSER,
                "test-uuid",
                false,
                "",
                1,
                2,
                List.of(
                        new MapUiModel.MapSummary("map-1", "Desert Crossing", "Anuke", 200, 200, 10, 2, false),
                        new MapUiModel.MapSummary("map-2", "Glacier Pass", "Delta", 300, 300, 5, 0, true)
                ),
                2,
                "", "", "", "", 0, 0, "", false,
                0, 0, "", "", "", "",
                0, 0.0, 0.0, 0, 0, 0, null,
                false, null,
                false, 0, 0, 0,
                false, 0L
        );
    }

    private MapUiModel createTestDetailsModel(String mapId) {
        return new MapUiModel(
                MapUiModel.ViewMode.DETAILS,
                "test-uuid",
                true,
                "",
                1,
                1,
                List.of(),
                1,
                mapId,
                "Desert Crossing",
                "Anuke",
                "A rough battlefield.",
                250, 250,
                "Survival",
                false,
                42, 10,
                "2h ago",
                "8m", "24m", "1h 12m",
                35, 12.5, 4.0,
                14, 2, 88,
                null,
                false, "net-xcore_test123",
                false, 0, 0, 0,
                false, 0L
        );
    }

    @Test
    @DisplayName("render in BROWSER mode compiles 520f shell with search and map table")
    void render_browserMode_compilesSearchAndTable() {
        MapUiController controller = new MapUiController(mapService, mapDataRepository, previewService, observerService, session);
        MapUiModel model = createTestBrowserModel();

        VNode root = controller.render(model);
        VNodeCompiler compiler = new VNodeCompiler(LocalizerResolver.IDENTITY);
        String dsl = UiDslWriter.write(compiler.compile(root));

        assertThat(dsl).contains("background: pane");
        assertThat(dsl).contains("width: 520");
        assertThat(dsl).contains("id: field_search");
        assertThat(dsl).contains("id: slot_map_table");
        assertThat(dsl).contains("Desert Crossing");
        assertThat(dsl).contains("action:select_map:map-1");
    }

    @Test
    @DisplayName("render in DETAILS mode compiles 2-column hero, telemetry matrix, and RTV button")
    void render_detailsMode_compilesHeroAndTelemetry() {
        MapUiController controller = new MapUiController(mapService, mapDataRepository, previewService, observerService, session);
        MapUiModel model = createTestDetailsModel("map-1");

        VNode root = controller.render(model);
        VNodeCompiler compiler = new VNodeCompiler(LocalizerResolver.IDENTITY);
        String dsl = UiDslWriter.write(compiler.compile(root));

        assertThat(dsl).contains("background: pane");
        assertThat(dsl).contains("width: 520");
        assertThat(dsl).contains("id: slot_preview");
        assertThat(dsl).contains("net-xcore_test123");
        assertThat(dsl).contains("align: left");
        assertThat(dsl).contains("id: slot_reputation");
        assertThat(dsl).contains("id: slot_rtv");
        assertThat(dsl).contains("action:rtv");
        assertThat(dsl).contains("action:back_to_list");
    }

    @Test
    @DisplayName("update on ToggleReputation updates like count optimistically and patches slot_reputation")
    void update_toggleReputation_updatesOptimistically() {
        MapUiController controller = new MapUiController(mapService, mapDataRepository, previewService, observerService, session);
        MapUiModel model = createTestDetailsModel("map-1");

        UpdateResult<MapUiModel> result = controller.update(
                model, new MapUiEvent.ToggleReputation(true), null
        );

        assertThat(result.model().playerVote()).isTrue();
        assertThat(result.model().likes()).isEqualTo(15);
        assertThat(result.dirtySlots()).containsExactly(MapUiController.SLOT_REPUTATION);
        assertThat(result.fullRerender()).isFalse();
    }

    @Test
    @DisplayName("update on PreviewReady patches slot_preview with new texture region")
    void update_previewReady_patchesSlotPreview() {
        MapUiController controller = new MapUiController(mapService, mapDataRepository, previewService, observerService, session);
        MapUiModel model = createTestDetailsModel("map-1").withPreview(null, true);

        UpdateResult<MapUiModel> result = controller.update(
                model, new MapUiEvent.PreviewReady("map-1", "net-xcore_streamed999"), null
        );

        assertThat(result.model().previewLoading()).isFalse();
        assertThat(result.model().previewTextureRegion()).isEqualTo("net-xcore_streamed999");
        assertThat(result.dirtySlots()).containsExactly(MapUiController.SLOT_PREVIEW);
    }

    @Test
    @DisplayName("update on RtvVoteUpdated patches slot_rtv with live vote progress")
    void update_rtvVoteUpdated_patchesSlotRtv() {
        MapUiController controller = new MapUiController(mapService, mapDataRepository, previewService, observerService, session);
        MapUiModel model = createTestDetailsModel("map-1");

        UpdateResult<MapUiModel> result = controller.update(
                model, new MapUiEvent.RtvVoteUpdated("map-1", 3, 5, 24), null
        );

        assertThat(result.model().rtvActive()).isTrue();
        assertThat(result.model().rtvVotes()).isEqualTo(3);
        assertThat(result.model().rtvVotesRequired()).isEqualTo(5);
        assertThat(result.model().rtvRemainingSeconds()).isEqualTo(24);
        assertThat(result.dirtySlots()).containsExactly(MapUiController.SLOT_RTV);
    }

    @Test
    @DisplayName("update on AdminForceRtvClick triggers 2-click confirmation pattern")
    void update_adminForceRtv_twoClickConfirmation() {
        MapUiController controller = new MapUiController(mapService, mapDataRepository, previewService, observerService, session);
        MapUiModel model = createTestDetailsModel("map-1");

        // 1st click: switches to confirming
        UpdateResult<MapUiModel> firstClick = controller.update(
                model, new MapUiEvent.AdminForceRtvClick(), null
        );

        assertThat(firstClick.model().adminForceConfirming()).isTrue();
        assertThat(firstClick.dirtySlots()).containsExactly(MapUiController.SLOT_RTV);

        // 2nd click: executes force switch and closes dialog
        ControllerContext ctx = mock(ControllerContext.class);
        UpdateResult<MapUiModel> secondClick = controller.update(
                firstClick.model(), new MapUiEvent.AdminForceRtvClick(), ctx
        );

        assertThat(secondClick.close()).isTrue();
        verify(ctx).close();
    }

    @Test
    @DisplayName("parseEvent correctly routes actions from MenuResult")
    void parseEvent_routesActionsCorrectly() {
        MapUiController controller = new MapUiController(null, null, null, null, null);

        MenuResult searchRes = new MenuResult("action:search");
        searchRes.values.put("field_search", "Rift");
        assertThat(controller.parseEvent(searchRes)).isEqualTo(new MapUiEvent.SearchChanged("Rift"));

        MenuResult selectRes = new MenuResult("action:select_map:map-abc");
        assertThat(controller.parseEvent(selectRes)).isEqualTo(new MapUiEvent.OpenMapDetails("map-abc"));

        MenuResult likeRes = new MenuResult("action:like");
        assertThat(controller.parseEvent(likeRes)).isEqualTo(new MapUiEvent.ToggleReputation(true));

        MenuResult rtvRes = new MenuResult("action:rtv");
        assertThat(controller.parseEvent(rtvRes)).isEqualTo(new MapUiEvent.TriggerRtv());

        MenuResult backRes = new MenuResult("action:back_to_list");
        assertThat(controller.parseEvent(backRes)).isEqualTo(new MapUiEvent.BackToBrowser());

        MenuResult prevRes = new MenuResult("action:page:prev");
        assertThat(controller.parseEvent(prevRes)).isEqualTo(new MapUiEvent.PrevPage());

        MenuResult nextRes = new MenuResult("action:page:next");
        assertThat(controller.parseEvent(nextRes)).isEqualTo(new MapUiEvent.NextPage());
    }

    @Test
    @DisplayName("pagination navigates between pages with 10 maps per page and updates totalMapsCount")
    void pagination_navigatesPagesWithTenMapsPerPage() {
        arc.struct.Seq<mindustry.maps.Map> mockMaps = new arc.struct.Seq<>();
        for (int i = 1; i <= 25; i++) {
            mindustry.maps.Map m = mock(mindustry.maps.Map.class);
            when(m.plainName()).thenReturn("Map " + i);
            when(m.author()).thenReturn("Author " + i);
            mockMaps.add(m);
        }
        when(mapService.getAvailableMaps()).thenReturn(mockMaps);

        MapUiController controller = new MapUiController(mapService, mapDataRepository, previewService, observerService, session);
        assertThat(controller.mapsPerPage()).isEqualTo(10);

        MapUiModel initial = controller.createInitialBrowserModel(session, 1);
        assertThat(initial.page()).isEqualTo(1);
        assertThat(initial.totalPages()).isEqualTo(3);
        assertThat(initial.totalMapsCount()).isEqualTo(25);
        assertThat(initial.displayedMaps()).hasSize(10);
        assertThat(initial.displayedMaps().get(0).name()).isEqualTo("Map 1");

        // Next page advances to page 2 (items 11-20)
        UpdateResult<MapUiModel> page2Result = controller.update(initial, new MapUiEvent.NextPage(), null);
        MapUiModel page2 = page2Result.model();
        assertThat(page2.page()).isEqualTo(2);
        assertThat(page2.displayedMaps()).hasSize(10);
        assertThat(page2.displayedMaps().get(0).name()).isEqualTo("Map 11");

        // Next page advances to page 3 (remaining 5 items)
        UpdateResult<MapUiModel> page3Result = controller.update(page2, new MapUiEvent.NextPage(), null);
        MapUiModel page3 = page3Result.model();
        assertThat(page3.page()).isEqualTo(3);
        assertThat(page3.displayedMaps()).hasSize(5);
        assertThat(page3.displayedMaps().get(0).name()).isEqualTo("Map 21");

        // Next page clamps at totalPages (3)
        UpdateResult<MapUiModel> clampedResult = controller.update(page3, new MapUiEvent.NextPage(), null);
        assertThat(clampedResult.model().page()).isEqualTo(3);

        // Prev page goes back to page 2
        UpdateResult<MapUiModel> prevResult = controller.update(page3, new MapUiEvent.PrevPage(), null);
        assertThat(prevResult.model().page()).isEqualTo(2);
    }

    @Test
    @DisplayName("render in BROWSER mode resolves localized text via LocalizerResolver")
    void render_browserMode_resolvesLocalizedText() {
        MapUiController controller = new MapUiController(mapService, mapDataRepository, previewService, observerService, session);
        MapUiModel model = createTestBrowserModel();

        VNode root = controller.render(model);
        LocalizerResolver resolver = (key, args) -> switch (key) {
            case "commands-maps-title" -> "Карты";
            case "map-ui-search-hint" -> "Поиск карты или автора...";
            case "map-ui-prev" -> "< Назад";
            case "map-ui-next" -> "Вперед >";
            case "map-ui-page-info" -> "Страница " + args.get("page") + " из " + args.get("total");
            case "map-ui-by" -> "от " + args.get("author");
            case "close" -> "Закрыть";
            default -> key;
        };

        VNodeCompiler compiler = new VNodeCompiler(resolver);
        String dsl = UiDslWriter.write(compiler.compile(root));

        assertThat(dsl).contains("Карты");
        assertThat(dsl).contains("Поиск карты или автора...");
        assertThat(dsl).contains("< Назад");
        assertThat(dsl).contains("Вперед >");
        assertThat(dsl).contains("Страница 1 из 2");
        assertThat(dsl).contains("от Anuke");
        assertThat(dsl).contains("Закрыть");
    }

    @Test
    @DisplayName("render in DETAILS mode resolves localized telemetry, actions, and back button")
    void render_detailsMode_resolvesLocalizedTelemetryAndActions() {
        MapUiController controller = new MapUiController(mapService, mapDataRepository, previewService, observerService, session);
        MapUiModel model = createTestDetailsModel("map-1");

        VNode root = controller.render(model);
        LocalizerResolver resolver = (key, args) -> switch (key) {
            case "map-ui-by" -> "от " + args.get("author");
            case "map-ui-mode" -> "Режим: " + args.get("mode");
            case "map-ui-dimensions" -> "Размеры: " + args.get("width") + " x " + args.get("height");
            case "map-ui-col-duration" -> "ДЛИТЕЛЬНОСТЬ";
            case "map-ui-col-popularity" -> "ПОПУЛЯРНОСТЬ";
            case "map-ui-col-community" -> "СООБЩЕСТВО";
            case "map-ui-btn-like" -> "👍 Нравится (" + args.get("count") + ")";
            case "map-ui-btn-dislike" -> "👎 Не нравится (" + args.get("count") + ")";
            case "map-ui-rtv-start" -> "ГОЛОСОВАНИЕ ЗА СМЕНУ КАРТЫ (RTV)";
            case "map-maps-back" -> "← К списку карт";
            default -> key;
        };

        VNodeCompiler compiler = new VNodeCompiler(resolver);
        String dsl = UiDslWriter.write(compiler.compile(root));

        assertThat(dsl).contains("от Anuke");
        assertThat(dsl).contains("Режим: Survival");
        assertThat(dsl).contains("Размеры: 250 x 250");
        assertThat(dsl).contains("ДЛИТЕЛЬНОСТЬ");
        assertThat(dsl).contains("ПОПУЛЯРНОСТЬ");
        assertThat(dsl).contains("СООБЩЕСТВО");
        assertThat(dsl).contains("👍 Нравится (14)");
        assertThat(dsl).contains("👎 Не нравится (2)");
        assertThat(dsl).contains("ГОЛОСОВАНИЕ ЗА СМЕНУ КАРТЫ (RTV)");
        assertThat(dsl).contains("← К списку карт");
    }

    @Test
    @DisplayName("OpenMapDetails from browser pulls existing map statistics and populates telemetry")
    void openMapDetails_fromBrowser_pullsStatisticsAndPopulatesTelemetry() {
        Map mindustryMap = new Map(
                new Fi("in_research_of_power.msav"),
                150,
                200,
                StringMap.of("name", "in research of power...", "author", "uylol", "description", "wasnt worth it"),
                true
        );
        when(mapService.findMapByFileName("in_research_of_power.msav")).thenReturn(mindustryMap);

        ObjectId mapObjectId = new ObjectId();
        MapData persistedData = MapData.builder()
                .id(mapObjectId)
                .name("in research of power...")
                .fileName("in_research_of_power.msav")
                .author("uylol")
                .gameMode("survival")
                .playedTimes(25)
                .playedTimesYear(10)
                .like(18)
                .dislike(2)
                .reputation(40)
                .minimumGameTime(300_000L)
                .averageGameTime(1_200_000L)
                .maximumGameTime(3_600_000L)
                .lastPlayedTime(System.currentTimeMillis() - 3600_000L)
                .build();

        var pendingDetails = new java.util.concurrent.CompletableFuture<MapData>();
        when(mapDataRepository.findExistingAsync(eq("in research of power..."), eq("in_research_of_power.msav"), eq("uylol"), anyString()))
                .thenReturn(pendingDetails);

        session.player = mindustry.gen.Player.create();
        MapUiController controller = new MapUiController(mapService, mapDataRepository, previewService, observerService, session);
        MapUiModel browserModel = createTestBrowserModel();

        UpdateResult<MapUiModel> result = controller.update(browserModel, new MapUiEvent.OpenMapDetails("in_research_of_power.msav"), null);

        assertThat(result.model().playedTimes()).isZero();
        verify(mapDataRepository, never()).findOrCreate(anyString(), anyString(), anyString(), anyString());
        pendingDetails.complete(persistedData);
        MapUiModel details = controller.update(result.model(), new MapUiEvent.DetailsReady("in_research_of_power.msav", persistedData), null).model();
        assertThat(details.mode()).isEqualTo(MapUiModel.ViewMode.DETAILS);
        assertThat(details.mapName()).isEqualTo("in research of power...");
        assertThat(details.mapAuthor()).isEqualTo("uylol");
        assertThat(details.mapDescription()).isEqualTo("wasnt worth it");
        assertThat(details.width()).isEqualTo(150);
        assertThat(details.height()).isEqualTo(200);
        assertThat(details.playedTimes()).isEqualTo(25);
        assertThat(details.playedTimesYear()).isEqualTo(10);
        assertThat(details.likes()).isEqualTo(18);
        assertThat(details.dislikes()).isEqualTo(2);
        assertThat(details.reputation()).isEqualTo(40);
        assertThat(details.minGameTime()).isNotEqualTo("-");
        assertThat(details.avgGameTime()).isNotEqualTo("-");
        assertThat(details.maxGameTime()).isNotEqualTo("-");
        assertThat(details.selectedMapId()).isEqualTo("in_research_of_power.msav");

        verify(observerService).registerViewing("test-uuid", "in_research_of_power.msav");
        verify(previewService).requestPreview(any(), eq(mindustryMap), any());
    }

    @Test
    void browserRendersWithoutWaitingForMongo() {
        var engineMap = new Map(new Fi("arena.msav"), 10, 10, StringMap.of("name", "Arena"), true);
        when(mapService.getAvailableMaps()).thenReturn(arc.struct.Seq.with(engineMap));
        var pending = new java.util.concurrent.CompletableFuture<List<MapData>>();
        when(mapDataRepository.findAllAsync()).thenReturn(pending);
        var cache = new org.xcore.plugin.service.map.MapSummaryCache(mapDataRepository);
        cache.refresh();
        var controller = new MapUiController(mapService, mapDataRepository, previewService, observerService, session, null, cache);
        var initial = controller.createInitialBrowserModel(session, 1);
        assertThat(initial.displayedMaps().getFirst().likes()).isZero();
        verify(mapDataRepository, never()).findAllAsMap();
        var data = new MapData("Arena", "arena.msav", "unknown", "survival");
        data.like = 5;
        pending.complete(List.of(data));
        var result = controller.update(initial, new MapUiEvent.SummariesReady(), null);
        assertThat(result.model().displayedMaps().getFirst().likes()).isEqualTo(5);
        assertThat(result.dirtySlots()).containsExactly(MapUiController.SLOT_MAP_TABLE);
    }

    @Test
    @DisplayName("loadAllMapSummaries resolves likes and dislikes even with color tags in map name")
    void loadAllMapSummaries_resolvesStatsWithColorTagsInName() {
        Map mindustryMap = new Map(
                new Fi("power.msav"),
                150,
                200,
                StringMap.of("name", "[gold]Power Grid[]", "author", "uylol"),
                true
        );
        arc.struct.Seq<Map> available = arc.struct.Seq.with(mindustryMap);
        when(mapService.getAvailableMaps()).thenReturn(available);

        ObjectMap<String, MapData> allStats = new ObjectMap<>();
        allStats.put(MapDataRepository.genKey("Power Grid", "uylol", "survival"), MapData.builder()
                .name("Power Grid")
                .author("uylol")
                .gameMode("survival")
                .like(12)
                .dislike(3)
                .build());
        when(mapDataRepository.findAllAsync()).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(List.of(allStats.values().next())));
        var cache = new org.xcore.plugin.service.map.MapSummaryCache(mapDataRepository);
        cache.refresh().toCompletableFuture().join();
        MapUiController controller = new MapUiController(mapService, mapDataRepository, previewService, observerService, session, null, cache);
        MapUiModel browser = controller.createInitialBrowserModel(session, 1);
        verify(mapDataRepository, never()).findAllAsMap();

        assertThat(browser.displayedMaps()).hasSize(1);
        MapUiModel.MapSummary summary = browser.displayedMaps().get(0);
        assertThat(summary.name()).isEqualTo("Power Grid");
        assertThat(summary.likes()).isEqualTo(12);
        assertThat(summary.dislikes()).isEqualTo(3);
    }
}
