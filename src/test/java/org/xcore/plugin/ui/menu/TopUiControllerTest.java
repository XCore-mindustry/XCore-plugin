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
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.integration.top.LeaderboardEntry;
import org.xcore.plugin.integration.top.LeaderboardPage;
import org.xcore.plugin.integration.top.LeaderboardPageRequest;
import org.xcore.plugin.integration.top.TopCategoryProvider;
import org.xcore.plugin.integration.top.TopCategoryRegistry;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.ui.VNode;
import org.xcore.ui.VNodeCompiler;
import org.xcore.ui.LocalizerResolver;
import org.xcore.ui.runtime.UpdateResult;

import java.util.ArrayDeque;
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
    private Session session;
    private PlayerData viewerData;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        topMenu = mock(TopMenu.class);
        registry = new TopCategoryRegistry();
        playerMenu = mock(PlayerMenu.class);
        sessionService = mock(SessionService.class);
        async = mock(Async.class);

        viewerData = new PlayerData("viewer-uuid", true);
        viewerData.pid = 1;
        viewerData.nickname = "Alice";
        viewerData.pvpRating = 1200;
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
        TopUiController.TopModel model = controller.createInitialModel(null, 1, null, null);

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
        TopUiController.TopModel initial = controller.createInitialModel("MINI_PVP", 1, null, null);

        UpdateResult<TopUiController.TopModel> res = controller.update(
                initial, new TopUiController.TopEvent.SelectCategory("PLAYTIME"), null
        );

        assertThat(res.model().selectedCategoryId()).isEqualTo("PLAYTIME");
        assertThat(res.model().entries()).hasSize(1);
        assertThat(res.model().entries().getFirst().displayName()).isEqualTo("TimeLord");
        assertThat(res.model().selfRank()).isEqualTo(2);
        assertThat(res.model().currentPage()).isEqualTo(1);
        assertThat(res.model().cursorBackStack()).isEmpty();
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
        TopUiController.TopModel initial = controller.createInitialModel("MINI_PVP", 1, null, null);

        UpdateResult<TopUiController.TopModel> nextRes = controller.update(
                initial, new TopUiController.TopEvent.NextPage(), null
        );

        assertThat(nextRes.model().currentPage()).isEqualTo(2);
        assertThat(nextRes.model().cursorBackStack()).containsExactly(TopUiController.FIRST_PAGE_CURSOR_TOKEN);
        assertThat(nextRes.model().currentCursor()).isEqualTo("cur_page_2");
        assertThat(nextRes.model().entries().getFirst().displayName()).isEqualTo("P2");
        assertThat(nextRes.dirtySlots()).containsExactly(
                TopUiController.SLOT_ENTRIES, TopUiController.SLOT_SELF_RANK, TopUiController.SLOT_PAGINATION
        );

        // Now test PrevPage restores back stack and cursor
        UpdateResult<TopUiController.TopModel> prevRes = controller.update(
                nextRes.model(), new TopUiController.TopEvent.PrevPage(), null
        );

        assertThat(prevRes.model().currentPage()).isEqualTo(1);
        assertThat(prevRes.model().cursorBackStack()).isEmpty();
        assertThat(prevRes.model().currentCursor()).isNull();
        assertThat(prevRes.model().entries().getFirst().displayName()).isEqualTo("P1");
    }

    @Test
    @DisplayName("update InspectPlayer opens profile directly for self")
    void update_inspectPlayer_self() {
        registerMockProvider("MINI_PVP", 20, List.of(), false, null, null);
        TopUiController controller = new TopUiController(topMenu, registry, playerMenu, sessionService, async, session);
        TopUiController.TopModel model = controller.createInitialModel("MINI_PVP", 1, null, null);

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
        TopUiController.TopModel model = controller.createInitialModel("MINI_PVP", 1, null, null);

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
        TopUiController.TopModel model = controller.createInitialModel("MINI_PVP", 1, null, null);
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
        TopUiController.TopModel model = controller.createInitialModel("MINI_PVP", 1, null, null);

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
}
