package org.xcore.plugin.ui.menu;

import com.ospx.flubundle.Bundle;
import com.ospx.flubundle.Localizer;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import mindustry.ui.builder.MenuResult;
import mindustry.ui.builder.UiDslWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.InlineStorageExecutor;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.integration.top.LeaderboardEntry;
import org.xcore.plugin.integration.top.LeaderboardPage;
import org.xcore.plugin.integration.top.LeaderboardPageRequest;
import org.xcore.plugin.integration.top.TopCategoryProvider;
import org.xcore.plugin.integration.top.TopCategoryRegistry;
import org.xcore.plugin.integration.top.TopScope;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.kit.LayoutAssert;
import org.xcore.plugin.ui.kit.Screen;
import org.xcore.ui.VNode;
import org.xcore.ui.VNodeCompiler;
import org.xcore.ui.VNodes;
import org.xcore.ui.LocalizerResolver;
import org.xcore.ui.runtime.UiSession;
import org.xcore.ui.runtime.UpdateResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TopUiControllerTest {

    private TopMenu topMenu;
    private TopCategoryRegistry registry;
    private PlayerMenu playerMenu;
    private SessionService sessionService;
    private Async async;
    private Deque<Runnable> mainThread;
    private Session session;
    private PlayerData viewerData;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        topMenu = mock(TopMenu.class);
        registry = new TopCategoryRegistry();
        playerMenu = mock(PlayerMenu.class);
        sessionService = mock(SessionService.class);
        mainThread = new ArrayDeque<>();
        // Storage work runs inline; what it hands to the game thread waits until the test runs it.
        async = spy(new Async(InlineStorageExecutor.create(), mainThread::add));

        viewerData = new PlayerData("viewer-uuid", true);
        viewerData.pid = 1;
        viewerData.nickname = "Alice";
        viewerData.totalPlayTime = 300;
        viewerData.hexedPoints = 25;

        Bundle bundle = mock(Bundle.class);
        Localizer localizer = mock(Localizer.class);
        when(localizer.locale()).thenReturn(Locale.ENGLISH);
        when(localizer.format(anyString())).thenAnswer(i -> i.getArgument(0));
        when(localizer.format(anyString(), anyMap())).thenAnswer(i -> i.getArgument(0));
        when(bundle.localizer(any(java.util.function.Supplier.class))).thenReturn(localizer);

        Player player = Player.create();
        player.con = mock(NetConnection.class);
        player.con.mobile = false;

        session = new Session(
                new TomlSecretsConfig(),
                bundle,
                null,
                mock(PlayerDataRepository.class),
                player,
                viewerData
        );
        when(sessionService.get("viewer-uuid")).thenReturn(session);
    }

    private void registerMockProvider(String id, int priority, List<LeaderboardEntry> entries, boolean hasNext, String nextCursor, Integer selfRank) {
        TopCategoryProvider provider = mock(TopCategoryProvider.class);
        when(provider.id()).thenReturn(id);
        when(provider.displayName(any())).thenReturn(id);
        when(provider.priority()).thenReturn(priority);
        when(provider.loadPage(any(LeaderboardPageRequest.class))).thenAnswer(inv -> {
            LeaderboardPageRequest req = inv.getArgument(0);
            return new LeaderboardPage(
                    req.page(),
                    entries,
                    hasNext,
                    nextCursor,
                    (long) entries.size() * 3,
                    selfRank
            );
        });
        registry.register(provider);
    }

    /**
     * Sends {@code event} to a dialog showing {@code model} and, once the page it asks for
     * has been read, the {@link TopUiController.TopEvent.Loaded} that follows.
     */
    @SuppressWarnings("unchecked")
    private UpdateResult<TopUiController.TopModel> loadThrough(TopUiController controller,
                                                               TopUiController.TopModel model,
                                                               TopUiController.TopEvent event) {
        UiSession<TopUiController.TopModel, TopUiController.TopEvent> ui = mock(UiSession.class);
        when(ui.model()).thenReturn(model);
        session.setActiveUiSession(ui);

        UpdateResult<TopUiController.TopModel> pending = controller.update(model, event, null);
        // Until the page arrives the dialog keeps showing what it had.
        assertThat(pending.isNoop()).isTrue();
        verify(ui, never()).dispatch(any());

        while (!mainThread.isEmpty()) {
            mainThread.poll().run();
        }
        ArgumentCaptor<TopUiController.TopEvent> dispatched = ArgumentCaptor.forClass(TopUiController.TopEvent.class);
        verify(ui).dispatch(dispatched.capture());
        assertThat(dispatched.getValue()).isInstanceOf(TopUiController.TopEvent.Loaded.class);
        return controller.update(model, dispatched.getValue(), null);
    }

    @Test
    @DisplayName("initialModel loads first category by priority and populates entries")
    void initialModel_loadsFirstCategoryByPriority() {
        List<LeaderboardEntry> entries = List.of(
                new LeaderboardEntry("p1", 1, "Bob", "1500", Map.of(), ""),
                new LeaderboardEntry("p2", 2, "Charlie", "1450", Map.of(), "")
        );
        registerMockProvider("MINI_PVP", 20, entries, true, "cursor_1", 5);
        registerMockProvider("PLAYTIME", 10, List.of(), false, null, null);

        TopUiController controller = new TopUiController(topMenu, registry, playerMenu, sessionService, async, session);
        TopUiController.TopModel model = controller.createInitialModel(null, null, 1, null, null);

        assertThat(model.selectedCategoryId()).isEqualTo("MINI_PVP");
        assertThat(model.categories()).hasSize(2);
        assertThat(model.entries()).hasSize(2);
        assertThat(model.currentPage()).isEqualTo(1);
        assertThat(model.hasNext()).isTrue();
        assertThat(model.nextCursor()).isEqualTo("cursor_1");
        assertThat(model.selfRank()).isEqualTo(5);
    }

    @Test
    @DisplayName("update SelectCategory switches category and loads page 1")
    void update_selectCategory_switchesCategory() {
        registerMockProvider("MINI_PVP", 20, List.of(new LeaderboardEntry("p1", 1, "P1", "10", Map.of(), "")), false, null, null);
        List<LeaderboardEntry> playtimeEntries = List.of(
                new LeaderboardEntry("p2", 1, "TimeLord", "5000", Map.of(), "")
        );
        registerMockProvider("PLAYTIME", 10, playtimeEntries, false, null, 2);

        TopUiController controller = new TopUiController(topMenu, registry, playerMenu, sessionService, async, session);
        TopUiController.TopModel initial = controller.createInitialModel("MINI_PVP", null, 1, null, null);

        UpdateResult<TopUiController.TopModel> res = loadThrough(
                controller, initial, new TopUiController.TopEvent.SelectCategory("PLAYTIME"));

        assertThat(res.model().selectedCategoryId()).isEqualTo("PLAYTIME");
        assertThat(res.model().entries()).hasSize(1);
        assertThat(res.model().entries().getFirst().displayName()).isEqualTo("TimeLord");
        assertThat(res.model().selfRank()).isEqualTo(2);
        assertThat(res.model().currentPage()).isEqualTo(1);
        assertThat(res.model().cursorBackStack()).isEmpty();
        // The header names the category, so the whole dialog is redrawn.
        assertThat(res.dirtySlots()).isEmpty();
        assertThat(res.isNoop()).isFalse();
    }

    @Test
    @DisplayName("update NextPage pushes cursor to back stack and updates page")
    void update_nextPage_pushesCursor() {
        TopCategoryProvider provider = mock(TopCategoryProvider.class);
        when(provider.id()).thenReturn("MINI_PVP");
        when(provider.displayName(any())).thenReturn("MiniPvP");
        when(provider.priority()).thenReturn(10);
        when(provider.loadPage(any(LeaderboardPageRequest.class))).thenAnswer(inv -> {
            LeaderboardPageRequest req = inv.getArgument(0);
            if (req.page() == 1) {
                return new LeaderboardPage(1, List.of(new LeaderboardEntry("p1", 1, "P1", "100", Map.of(), "")), true, "cur_page_2", 20L, 1);
            } else {
                return new LeaderboardPage(2, List.of(new LeaderboardEntry("p2", 2, "P2", "90", Map.of(), "")), false, null, 20L, 1);
            }
        });
        registry.register(provider);

        TopUiController controller = new TopUiController(topMenu, registry, playerMenu, sessionService, async, session);
        TopUiController.TopModel initial = controller.createInitialModel("MINI_PVP", null, 1, null, null);

        UpdateResult<TopUiController.TopModel> nextRes = loadThrough(
                controller, initial, new TopUiController.TopEvent.NextPage());

        assertThat(nextRes.model().currentPage()).isEqualTo(2);
        assertThat(nextRes.model().cursorBackStack()).containsExactly(TopUiController.FIRST_PAGE_CURSOR_TOKEN);
        assertThat(nextRes.model().currentCursor()).isEqualTo("cur_page_2");
        assertThat(nextRes.model().entries().getFirst().displayName()).isEqualTo("P2");
        // A client has built one layout; the patch goes to the slots of each and it applies its own.
        assertThat(nextRes.dirtySlots()).containsExactlyElementsOf(Screen.slots(
                TopUiController.SLOT_ENTRIES, TopUiController.SLOT_SELF_RANK, TopUiController.SLOT_PAGINATION));
        for (var slot : nextRes.dirtySlots()) {
            assertThat(VNodes.findSlot(controller.render(nextRes.model()), slot.path())).as(slot.path()).isNotNull();
        }

        // Now test PrevPage restores back stack and cursor
        UpdateResult<TopUiController.TopModel> prevRes = loadThrough(
                controller, nextRes.model(), new TopUiController.TopEvent.PrevPage());

        assertThat(prevRes.model().currentPage()).isEqualTo(1);
        assertThat(prevRes.model().cursorBackStack()).isEmpty();
        assertThat(prevRes.model().currentCursor()).isNull();
        assertThat(prevRes.model().entries().getFirst().displayName()).isEqualTo("P1");
    }

    /** A category with two seasons: "2" is running, "1" is over. */
    private TopCategoryProvider seasonalProvider() {
        return new TopCategoryProvider() {
            @Override
            public String id() {
                return "DUEL";
            }

            @Override
            public String displayName(org.xcore.plugin.localization.Localization local) {
                return "Duel";
            }

            @Override
            public int priority() {
                return 30;
            }

            @Override
            public List<TopScope> scopes() {
                return List.of(new TopScope("2", true, Map.of()), new TopScope("1", false, Map.of()));
            }

            @Override
            public String formatScope(TopScope scope, org.xcore.plugin.localization.Localization local) {
                return "Season " + scope.id();
            }

            @Override
            public LeaderboardPage loadPage(LeaderboardPageRequest request) {
                String season = request.scopeId() == null ? "2" : request.scopeId();
                return new LeaderboardPage(request.page(),
                        List.of(new LeaderboardEntry("p" + season, 1, "Champion of " + season, "1500", Map.of(), "")),
                        false, null, 1L, 4, "1642");
            }

            @Override
            public String formatValue(String primaryValue, org.xcore.plugin.localization.Localization local) {
                return primaryValue + " ELO";
            }
        };
    }

    @Test
    @DisplayName("a category with seasons opens on the running one and offers a switcher")
    void scopes_openOnCurrent() {
        registry.register(seasonalProvider());
        TopUiController controller = new TopUiController(topMenu, registry, playerMenu, sessionService, async, session);

        TopUiController.TopModel model = controller.createInitialModel("DUEL", null, 1, null, null);

        assertThat(model.scopes()).extracting(TopScope::id).containsExactly("2", "1");
        assertThat(model.selectedScopeId()).isEqualTo("2");
        assertThat(model.entries().getFirst().displayName()).isEqualTo("Champion of 2");
        // The viewer is not on the page, so their value comes from the page and is worded by the category.
        assertThat(model.selfRank()).isEqualTo(4);
        assertThat(model.selfPrimaryValue()).isEqualTo("1642 ELO");

        String dsl = UiDslWriter.write(new VNodeCompiler(LocalizerResolver.IDENTITY).compile(controller.render(model)));
        assertThat(dsl).contains("Season 2", "action:scope:1");
    }

    @Test
    @DisplayName("update SelectScope loads the chosen season and keeps the switcher")
    void update_selectScope_loadsSeason() {
        registry.register(seasonalProvider());
        TopUiController controller = new TopUiController(topMenu, registry, playerMenu, sessionService, async, session);
        TopUiController.TopModel initial = controller.createInitialModel("DUEL", null, 1, null, null);

        UpdateResult<TopUiController.TopModel> res = loadThrough(
                controller, initial, new TopUiController.TopEvent.SelectScope("1"));

        assertThat(res.model().selectedScopeId()).isEqualTo("1");
        assertThat(res.model().scopes()).isEqualTo(initial.scopes());
        assertThat(res.model().entries().getFirst().displayName()).isEqualTo("Champion of 1");
        assertThat(res.model().currentPage()).isEqualTo(1);
        assertThat(res.dirtySlots()).isEmpty();

        String dsl = UiDslWriter.write(new VNodeCompiler(LocalizerResolver.IDENTITY).compile(controller.render(res.model())));
        assertThat(dsl).contains("Season 1", "action:scope:2");
    }

    @Test
    @DisplayName("update SelectScope ignores the shown scope and scopes the category does not have")
    void update_selectScope_ignoresUnknown() {
        registry.register(seasonalProvider());
        TopUiController controller = new TopUiController(topMenu, registry, playerMenu, sessionService, async, session);
        TopUiController.TopModel initial = controller.createInitialModel("DUEL", null, 1, null, null);

        assertThat(controller.update(initial, new TopUiController.TopEvent.SelectScope("2"), null).isNoop()).isTrue();
        assertThat(controller.update(initial, new TopUiController.TopEvent.SelectScope("9"), null).isNoop()).isTrue();
        assertThat(controller.update(initial, new TopUiController.TopEvent.SelectScope(""), null).isNoop()).isTrue();
        assertThat(mainThread).isEmpty();
    }

    @Test
    @DisplayName("a page that arrives after the dialog moved on is dropped")
    @SuppressWarnings("unchecked")
    void load_dropsStaleResult() {
        registry.register(seasonalProvider());
        TopUiController controller = new TopUiController(topMenu, registry, playerMenu, sessionService, async, session);
        TopUiController.TopModel initial = controller.createInitialModel("DUEL", null, 1, null, null);
        UiSession<TopUiController.TopModel, TopUiController.TopEvent> ui = mock(UiSession.class);
        when(ui.model()).thenReturn(initial);
        session.setActiveUiSession(ui);

        controller.update(initial, new TopUiController.TopEvent.SelectScope("1"), null);
        // The player switched to something else before the page came back.
        when(ui.model()).thenReturn(controller.createInitialModel("DUEL", null, 1, null, null));
        mainThread.forEach(Runnable::run);

        verify(ui, never()).dispatch(any());
    }

    @Test
    @DisplayName("of two requests still loading only the latest one is applied")
    @SuppressWarnings("unchecked")
    void load_appliesOnlyTheLatestRequest() {
        registerMockProvider("MINI_PVP", 30, List.of(), false, null, null);
        registerMockProvider("PLAYTIME", 20, List.of(), false, null, null);
        registerMockProvider("HEXED", 10, List.of(), false, null, null);
        TopUiController controller = new TopUiController(topMenu, registry, playerMenu, sessionService, async, session);
        TopUiController.TopModel initial = controller.createInitialModel("MINI_PVP", null, 1, null, null);
        UiSession<TopUiController.TopModel, TopUiController.TopEvent> ui = mock(UiSession.class);
        when(ui.model()).thenReturn(initial);
        session.setActiveUiSession(ui);

        // Both taps land before either page is back, so both start from the same model.
        controller.update(initial, new TopUiController.TopEvent.SelectCategory("PLAYTIME"), null);
        controller.update(initial, new TopUiController.TopEvent.SelectCategory("HEXED"), null);
        while (!mainThread.isEmpty()) {
            mainThread.poll().run();
        }

        ArgumentCaptor<TopUiController.TopEvent> dispatched = ArgumentCaptor.forClass(TopUiController.TopEvent.class);
        verify(ui).dispatch(dispatched.capture());
        assertThat(((TopUiController.TopEvent.Loaded) dispatched.getValue()).data().query().categoryId())
                .isEqualTo("HEXED");
    }

    @Test
    @DisplayName("a category that fails to load shows an empty page instead of breaking the dialog")
    void fetch_survivesProviderFailure() {
        TopCategoryProvider broken = mock(TopCategoryProvider.class);
        when(broken.id()).thenReturn("BROKEN");
        when(broken.displayName(any())).thenReturn("Broken");
        when(broken.scopes()).thenThrow(new IllegalStateException("no scopes"));
        when(broken.loadPage(any())).thenThrow(new IllegalStateException("no page"));
        registry.register(broken);
        TopUiController controller = new TopUiController(topMenu, registry, playerMenu, sessionService, async, session);

        TopUiController.TopModel model = controller.createInitialModel("BROKEN", null, 1, null, null);

        assertThat(model.entries()).isEmpty();
        assertThat(model.scopes()).isEmpty();
        assertThat(model.selectedScopeId()).isNull();
    }

    @Test
    @DisplayName("update InspectPlayer opens profile directly for self")
    void update_inspectPlayer_self() {
        registerMockProvider("MINI_PVP", 20, List.of(), false, null, null);
        TopUiController controller = new TopUiController(topMenu, registry, playerMenu, sessionService, async, session);
        TopUiController.TopModel model = controller.createInitialModel("MINI_PVP", null, 1, null, null);

        controller.update(model, new TopUiController.TopEvent.InspectPlayer("viewer-uuid"), null);

        verify(playerMenu).openProfileUi(eq(session), eq(session.data));
        assertThat(session.hasHistory()).isTrue();
    }

    @Test
    @DisplayName("update InspectPlayer opens profile directly for online player")
    void update_inspectPlayer_online() {
        registerMockProvider("MINI_PVP", 20, List.of(), false, null, null);
        PlayerData other = new PlayerData("other-uuid", true);
        other.pid = 99;
        other.nickname = "Bob";

        Session otherSession = new Session(new TomlSecretsConfig(), session.bundle, null, null, null, other);
        when(sessionService.get("other-uuid")).thenReturn(otherSession);

        TopUiController controller = new TopUiController(topMenu, registry, playerMenu, sessionService, async, session);
        TopUiController.TopModel model = controller.createInitialModel("MINI_PVP", null, 1, null, null);

        controller.update(model, new TopUiController.TopEvent.InspectPlayer("other-uuid"), null);

        verify(playerMenu).openProfileUi(eq(session), eq(other));
        assertThat(session.hasHistory()).isTrue();
    }

    @Test
    @DisplayName("update InspectPlayer asynchronously loads offline player")
    @SuppressWarnings("unchecked")
    void update_inspectPlayer_offline() {
        registerMockProvider("MINI_PVP", 20, List.of(), false, null, null);
        PlayerData offlineTarget = new PlayerData("offline-uuid", true);
        offlineTarget.pid = 123;
        offlineTarget.nickname = "Ghost";

        when(sessionService.get("offline-uuid")).thenReturn(null);
        when(sessionService.getOrLoadFromDbAsync("offline-uuid")).thenReturn(CompletableFuture.completedFuture(offlineTarget));

        doAnswer(inv -> {
            Player p = inv.getArgument(0);
            CompletableFuture<PlayerData> future = inv.getArgument(1);
            BiConsumer<Player, PlayerData> callback = inv.getArgument(2);
            callback.accept(p, future.join());
            return null;
        }).when(async).onMainForPlayer(any(), any(), any());

        TopUiController controller = new TopUiController(topMenu, registry, playerMenu, sessionService, async, session);
        TopUiController.TopModel model = controller.createInitialModel("MINI_PVP", null, 1, null, null);
        org.xcore.ui.runtime.UiSession mockSession = mock(org.xcore.ui.runtime.UiSession.class);
        when(mockSession.model()).thenReturn(model);
        session.setActiveUiSession(mockSession);

        controller.update(model, new TopUiController.TopEvent.InspectPlayer("offline-uuid"), null);

        verify(playerMenu).openProfileUi(eq(session), eq(offlineTarget));
        assertThat(session.hasHistory()).isTrue();
    }

    @Test
    @DisplayName("formatRankBadge produces correct medal badges")
    void formatRankBadge_medals() {
        assertThat(TopUiController.formatRankBadge(1)).contains("#1").contains(String.valueOf(mindustry.gen.Iconc.star));
        assertThat(TopUiController.formatRankBadge(2)).isEqualTo("[#C0C0C0]#2[]");
        assertThat(TopUiController.formatRankBadge(3)).isEqualTo("[#D99058]#3[]");
        assertThat(TopUiController.formatRankBadge(4)).isEqualTo("[gray]#4[]");
        assertThat(TopUiController.formatRankBadge(10)).isEqualTo("[gray]#10[]");
    }

    @Test
    @DisplayName("parseEvent correctly routes actions")
    void parseEvent_routesActions() {
        TopUiController controller = new TopUiController(null, null, null, null, null, null);

        assertThat(controller.parseEvent(new MenuResult("action:close"))).isInstanceOf(TopUiController.TopEvent.Close.class);
        assertThat(controller.parseEvent(new MenuResult("action:page:next"))).isInstanceOf(TopUiController.TopEvent.NextPage.class);
        assertThat(controller.parseEvent(new MenuResult("action:page:prev"))).isInstanceOf(TopUiController.TopEvent.PrevPage.class);
        assertThat(controller.parseEvent(new MenuResult("action:refresh"))).isInstanceOf(TopUiController.TopEvent.Refresh.class);
        assertThat(controller.parseEvent(new MenuResult("action:tab:HEXED"))).isEqualTo(new TopUiController.TopEvent.SelectCategory("HEXED"));
        assertThat(controller.parseEvent(new MenuResult("action:scope:2"))).isEqualTo(new TopUiController.TopEvent.SelectScope("2"));
        assertThat(controller.parseEvent(new MenuResult("action:inspect:some-uuid"))).isEqualTo(new TopUiController.TopEvent.InspectPlayer("some-uuid"));
        assertThat(controller.parseEvent(new MenuResult((String) null))).isInstanceOf(TopUiController.TopEvent.Close.class);
    }

    @Test
    @DisplayName("render compiles valid VNode tree across states")
    void render_compilesValidTree() {
        List<LeaderboardEntry> entries = List.of(
                new LeaderboardEntry("viewer-uuid", 1, "Alice", "1500", Map.of("customNickname", "[#FF0000]Alice"), ""),
                new LeaderboardEntry("other-uuid", 2, "Bob", "1450", Map.of("rankName", "veteran"), "")
        );
        registerMockProvider("MINI_PVP", 20, entries, true, "cur_2", 1);
        registerMockProvider("PLAYTIME", 10, List.of(), false, null, null);

        TopUiController controller = new TopUiController(topMenu, registry, playerMenu, sessionService, async, session);
        TopUiController.TopModel model = controller.createInitialModel("MINI_PVP", null, 1, null, null);

        VNode tree = controller.render(model);
        assertThat(tree).isNotNull();

        VNodeCompiler compiler = new VNodeCompiler(LocalizerResolver.IDENTITY);
        var builder = compiler.compile(tree);
        String dsl = UiDslWriter.write(builder);

        assertThat(dsl).contains("table");
        assertThat(dsl).contains("action:tab:MINI_PVP");
        assertThat(dsl).contains("action:tab:PLAYTIME");
        assertThat(dsl).contains("action:inspect:viewer-uuid");
        assertThat(dsl).contains("action:inspect:other-uuid");
    }

    /** A full page of the kind that is hardest to fit: long names, every tag, ranks of four digits. */
    private TopUiController.TopModel crowdedPage(String language) {
        session.localization = LayoutAssert.localization(language);

        List<LeaderboardEntry> entries = new ArrayList<>();
        for (int i = 0; i < TopUiController.PLAYERS_PER_PAGE; i++) {
            entries.add(new LeaderboardEntry(i == 3 ? "viewer-uuid" : "uuid-" + i, 1238 + i,
                    "Player" + i, "2 147 483 очков",
                    Map.of("customNickname", "[#ff8800]ОченьДлинныйНикнеймИгрокаБезПробелов" + i,
                            "activeBadge", "developer", "admin", "true",
                            "leagueName", "DIAMOND", "rankName", "veteran"), ""));
        }
        for (String[] category : new String[][]{{"MINI_PVP", "Mini-PvP"}, {"HEXED", "HexedCore"},
                {"PLAYTIME", "Время игры"}, {"HEXED_LEGACY", "Старый Hexed"}}) {
            TopCategoryProvider provider = mock(TopCategoryProvider.class);
            when(provider.id()).thenReturn(category[0]);
            when(provider.displayName(any())).thenReturn(category[1]);
            when(provider.priority()).thenReturn(100 - registry.all().size());
            when(provider.scopes()).thenReturn(List.of(new TopScope("3", true, Map.of()), new TopScope("2", false, Map.of())));
            when(provider.formatScope(any(), any())).thenAnswer(inv ->
                    "Сезон " + inv.<TopScope>getArgument(0).id() + " · до 1 января 2027");
            when(provider.formatValue(any(LeaderboardEntry.class), any())).thenAnswer(inv ->
                    inv.<LeaderboardEntry>getArgument(0).primaryValue());
            when(provider.loadPage(any(LeaderboardPageRequest.class))).thenReturn(
                    new LeaderboardPage(124, entries, true, "next", 123_456L, 1241));
            registry.register(provider);
        }
        TopUiController controller = new TopUiController(topMenu, registry, playerMenu, sessionService, async, session);
        return controller.createInitialModel("MINI_PVP", null, 124, "cursor", new ArrayDeque<>(List.of("prev")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ru", "uk", "en"})
    @DisplayName("nothing in the leaderboard is left to find its own width, and every text fits its place")
    void window_isLaidOutForEveryScreen(String language) {
        TopUiController.TopModel model = crowdedPage(language);
        TopUiController controller = new TopUiController(topMenu, registry, playerMenu, sessionService, async, session);

        for (Screen screen : Screen.ALL) {
            VNode window = controller.window(model, screen);
            LayoutAssert.assertLaidOut(window, screen);
            assertThat(LayoutAssert.actions(window))
                    .contains("action:tab:HEXED", "action:scope:2", "action:inspect:uuid-0",
                            "action:page:prev", "action:page:next", "action:refresh");
        }
        LayoutAssert.assertFitsPacket(controller.render(model), "a crowded page in " + language);
        assertThat(LayoutAssert.allText(controller.render(model))).doesNotContain("top-menu-");
    }

    @Test
    @DisplayName("an empty category says so instead of showing an empty list")
    void window_showsEmptyCategory() {
        registerMockProvider("PLAYTIME", 10, List.of(), false, null, null);
        TopUiController controller = new TopUiController(topMenu, registry, playerMenu, sessionService, async, session);
        TopUiController.TopModel model = controller.createInitialModel("PLAYTIME", null, 1, null, null);

        for (Screen screen : Screen.ALL) {
            VNode window = controller.window(model, screen);
            LayoutAssert.assertLaidOut(window, screen);
            assertThat(LayoutAssert.allText(window)).contains("top-menu-empty", "top-menu-unranked");
            // Neither arrow of the pager can be pressed on the only page.
            assertThat(LayoutAssert.actions(window)).doesNotContain("action:page:prev", "action:page:next");
        }
    }

    @Test
    @DisplayName("resolveNickname preserves valid hex colors and handles empty nicknames and truncation")
    void resolveNickname_handlesColorsAndLimits() {
        LeaderboardEntry normal = new LeaderboardEntry("u1", 5, "NormalPlayer", "1000", Map.of(), "");
        assertThat(TopUiController.resolveNickname(normal, Map.of(), 24, LayoutAssert.localization("en"))).isEqualTo("NormalPlayer");

        LeaderboardEntry withGlyphAndColor = new LeaderboardEntry("u2", 58, " [#FFFFFFFF]TR|@XDictator", "1000", Map.of(), "");
        // Should keep colors intact, never escaping to [[#FFFFFFFF]
        String resolved = TopUiController.resolveNickname(withGlyphAndColor, Map.of(), 24, LayoutAssert.localization("en"));
        assertThat(resolved).contains("[#FFFFFFFF]");
        assertThat(resolved).doesNotContain("[[");

        // Empty nickname falls back to Player #rank
        LeaderboardEntry empty = new LeaderboardEntry("u3", 55, "   ", "1000", Map.of(), "");
        assertThat(TopUiController.resolveNickname(empty, Map.of(), 24, LayoutAssert.localization("en"))).isEqualTo("Player #55");

        // Stripped whitespace / color-only nickname falls back to Player #rank
        LeaderboardEntry colorOnly = new LeaderboardEntry("u4", 77, "[#123456]   []", "1000", Map.of(), "");
        assertThat(TopUiController.resolveNickname(colorOnly, Map.of(), 24, LayoutAssert.localization("en"))).isEqualTo("Player #77");

        // Nickname exceeding maxPlainLen truncates cleanly on visible plain text
        LeaderboardEntry longNick = new LeaderboardEntry("u5", 10, "VeryLongNicknameExceedingLimitHere", "1000", Map.of(), "");
        String truncated = TopUiController.resolveNickname(longNick, Map.of(), 10, LayoutAssert.localization("en"));
        assertThat(truncated).isEqualTo("VeryLongNi...");
    }
}
