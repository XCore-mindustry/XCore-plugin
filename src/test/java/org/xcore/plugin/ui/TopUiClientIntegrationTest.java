package org.xcore.plugin.ui;

import arc.Core;
import arc.mock.MockApplication;
import com.ospx.flubundle.Bundle;
import jakarta.inject.Provider;
import mindustry.core.GameState;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import mindustry.ui.builder.MenuResult;
import mindustry.ui.builder.UiBuilder.NodeBuilder;
import mindustry.ui.builder.UiDslWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.integration.top.LeaderboardEntry;
import org.xcore.plugin.integration.top.LeaderboardPage;
import org.xcore.plugin.integration.top.LeaderboardPageRequest;
import org.xcore.plugin.integration.top.TopCategoryProvider;
import org.xcore.plugin.integration.top.TopCategoryRegistry;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.TopMenuCacheService;
import org.xcore.plugin.service.TopMenuService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.menu.PlayerMenu;
import org.xcore.plugin.ui.menu.TopMenu;
import org.xcore.testkit.ui.DeterministicUiLoop;
import org.xcore.testkit.ui.UiSnapshot;
import org.xcore.testkit.ui.UiWireMessage;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static mindustry.Vars.state;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * End-to-end integration test driving MenuService, UiSession, and TopUiController
 * through DeterministicUiLoop and HeadlessMenuClient to verify adaptive category switching,
 * seamless pagination, self-rank sticky card, and player profile inspection.
 */
class TopUiClientIntegrationTest {

    private GameState originalState;
    private DeterministicUiLoop loop;
    private MenuService menuService;
    private Session session;
    private SessionService sessionService;
    private TopMenu topMenu;
    private TopCategoryRegistry registry;
    private PlayerMenu playerMenu;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        originalState = state;
        state = new GameState();

        loop = new DeterministicUiLoop();

        Core.app = new MockApplication() {
            @Override
            public void post(Runnable runnable) {
                loop.serverPost().post(runnable);
            }
        };

        PlayerData data = new PlayerData("viewer-uuid", true);
        data.pid = 42;
        data.nickname = "Alice";
        data.pvpRating = 1350;
        data.totalPlayTime = 240;

        Bundle bundle = mock(Bundle.class);
        com.ospx.flubundle.Localizer localizer = mock(com.ospx.flubundle.Localizer.class);
        when(localizer.locale()).thenReturn(Locale.ENGLISH);
        when(localizer.format(anyString())).thenAnswer(i -> i.getArgument(0));
        when(localizer.format(anyString(), anyMap())).thenAnswer(i -> i.getArgument(0));
        when(bundle.localizer(any(java.util.function.Supplier.class))).thenReturn(localizer);

        sessionService = mock(SessionService.class);
        Provider<SessionService> sessionProvider = () -> sessionService;

        MindustryMenuGateway gateway = new MindustryMenuGateway() {
            @Override public void menu(Player p, int id, String t, String c, String[][] b) {}
            @Override public void followUpMenu(Player p, int id, String t, String c, String[][] b) {}
            @Override public void hideFollowUpMenu(Player p, int id) {}
            @Override public void textInput(Player p, int id, String t, String c, int l, String d, boolean n) {}
            @Override public void openUri(Player p, String u) {}
            @Override public void copyToClipboard(Player p, String t) {}

            @Override
            public void menuBuilder(Player player, int menuId, long token, String title,
                                    boolean hideOnClick, boolean hideExisting, boolean fillScreen,
                                    NodeBuilder<?> ui) {
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

        registry = new TopCategoryRegistry();
        playerMenu = mock(PlayerMenu.class);

        var tomlConfig = new TomlXcoreConfig();
        var playerRepo = mock(PlayerDataRepository.class);
        var cacheService = mock(TopMenuCacheService.class);
        TopMenuService topMenuService = new TopMenuService(tomlConfig, playerRepo, cacheService, registry);

        topMenu = new TopMenu(new TomlSecretsConfig(), sessionService, menuService, topMenuService, playerMenu, registry,
                new org.xcore.plugin.concurrent.Async(org.xcore.plugin.concurrent.InlineStorageExecutor.create(), Runnable::run));
        topMenu.init();

        Player player = Player.create();
        player.con = mock(NetConnection.class);

        session = new Session(
                new TomlSecretsConfig(),
                bundle,
                menuService,
                playerRepo,
                player,
                data
        );

        when(sessionService.get(anyString())).thenReturn(session);

        loop.onClientMessage(msg -> {
            if (msg instanceof UiWireMessage.Choose choose) {
                var result = new MenuResult(choose.action());
                result.token = choose.token();
                menuService.onMenuBuilderResult(session, result);
            }
        });
    }

    @AfterEach
    void tearDown() {
        state = originalState;
        Core.app = null;
    }

    private void registerCategory(String id, String displayName, int priority, List<LeaderboardEntry> p1, List<LeaderboardEntry> p2) {
        TopCategoryProvider provider = new TopCategoryProvider() {
            @Override public String id() { return id; }
            @Override public String displayName(Localization local) { return displayName; }
            @Override public int priority() { return priority; }

            @Override
            public LeaderboardPage loadPage(LeaderboardPageRequest request) {
                if (request.page() == 1) {
                    boolean hasNext = p2 != null && !p2.isEmpty();
                    return new LeaderboardPage(1, p1, hasNext, hasNext ? "cursor_p2" : null, (long) p1.size() + (p2 != null ? p2.size() : 0), 1);
                } else {
                    return new LeaderboardPage(request.page(), p2 != null ? p2 : List.of(), false, null, (long) p1.size() + (p2 != null ? p2.size() : 0), 1);
                }
            }
        };
        registry.registerIfAbsent(provider);
    }

    @Test
    @DisplayName("full lifecycle: open top, switch category tabs, next page, and inspect player")
    void fullLifecycle_drivesTopMenuThroughClient() {
        List<LeaderboardEntry> pvpPage1 = List.of(
                new LeaderboardEntry("viewer-uuid", 1, "Alice", "1350", Map.of(), ""),
                new LeaderboardEntry("bob-uuid", 2, "Bob", "1200", Map.of(), "")
        );
        List<LeaderboardEntry> pvpPage2 = List.of(
                new LeaderboardEntry("charlie-uuid", 3, "Charlie", "1100", Map.of(), "")
        );
        registerCategory("CUSTOM_PVP", "CustomPvP", 100, pvpPage1, pvpPage2);

        List<LeaderboardEntry> playtimePage1 = List.of(
                new LeaderboardEntry("dave-uuid", 1, "Dave", "1000", Map.of(), "")
        );
        registerCategory("CUSTOM_PLAYTIME", "CustomPlaytime", 90, playtimePage1, null);

        // 1. Open Top Menu
        topMenu.openTopUi(session, "CUSTOM_PVP");
        int menuId = menuService.getMenuBuilderId();

        assertThat(loop.stepServerToClient()).isTrue();
        assertThat(loop.client().isVisible(menuId)).isTrue();
        assertThat(session.hasActiveUiSession()).isTrue();

        var showMsg = (UiWireMessage.Show) loop.transcript().all().stream()
                .filter(m -> m instanceof UiWireMessage.Show)
                .reduce((first, second) -> second)
                .orElseThrow();
        String initialDsl = UiDslWriter.write((NodeBuilder<?>) showMsg.body().decode());
        assertThat(initialDsl).contains("Alice");
        assertThat(initialDsl).contains("Bob");
        assertThat(initialDsl).contains("action:tab:CUSTOM_PVP");
        assertThat(initialDsl).contains("action:tab:CUSTOM_PLAYTIME");

        // 2. Client clicks Next Page -> sends patch update
        loop.client().click(menuId, "action:page:next");
        assertThat(loop.stepClientToServer()).isTrue();
        while (loop.stepServerToClient()) {}

        var updateMsg = (UiWireMessage.Update) loop.transcript().all().stream()
                .filter(m -> m instanceof UiWireMessage.Update)
                .reduce((first, second) -> second)
                .orElseThrow();
        assertThat(updateMsg).isNotNull();

        // 3. Client switches Category Tab to CustomPlaytime -> sends re-render show
        loop.client().click(menuId, "action:tab:CUSTOM_PLAYTIME");
        assertThat(loop.stepClientToServer()).isTrue();
        while (loop.stepServerToClient()) {}

        var catShowMsg = (UiWireMessage.Show) loop.transcript().all().stream()
                .filter(m -> m instanceof UiWireMessage.Show)
                .reduce((first, second) -> second)
                .orElseThrow();
        String catDsl = UiDslWriter.write((NodeBuilder<?>) catShowMsg.body().decode());
        assertThat(catDsl).contains("Dave");

        // 4. Client switches back to CustomPvP and clicks a player row to inspect profile
        loop.client().click(menuId, "action:tab:CUSTOM_PVP");
        assertThat(loop.stepClientToServer()).isTrue();
        while (loop.stepServerToClient()) {}

        loop.client().click(menuId, "action:inspect:viewer-uuid");
        assertThat(loop.stepClientToServer()).isTrue();

        verify(playerMenu).openProfileUi(eq(session), eq(session.data));
        assertThat(session.hasHistory()).isTrue();

        // 5. Client closes dialog
        loop.client().click(menuId, "action:close");
        assertThat(loop.stepClientToServer()).isTrue();
        while (loop.stepServerToClient()) {}

        assertThat(loop.client().isVisible(menuId)).isFalse();
    }
}
