package org.xcore.plugin.ui.menu;

import com.ospx.flubundle.Bundle;
import jakarta.inject.Provider;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import mindustry.ui.builder.UiBuilder.NodeBuilder;
import mindustry.ui.builder.UiDslWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.MindustryMenuGateway;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TopMenuCustomProviderTest {

    private SessionService sessionService;
    private MenuService menuService;
    private MindustryMenuGateway gateway;
    private TopMenuService topMenuService;
    private PlayerMenu playerMenu;
    private TopCategoryRegistry registry;
    private TopMenu topMenu;
    private Session session;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        sessionService = mock(SessionService.class);
        gateway = mock(MindustryMenuGateway.class);
        playerMenu = mock(PlayerMenu.class);

        var secretsConfig = new TomlSecretsConfig();
        Provider<SessionService> sessionProvider = mock(Provider.class);
        when(sessionProvider.get()).thenReturn(sessionService);
        menuService = new MenuService(sessionProvider, gateway);

        var tomlConfig = new TomlXcoreConfig();
        tomlConfig.server.name = "mini-pvp";
        var playerRepo = mock(PlayerDataRepository.class);
        var cacheService = mock(TopMenuCacheService.class);

        registry = new TopCategoryRegistry();
        topMenuService = new TopMenuService(tomlConfig, playerRepo, cacheService, registry);
        topMenu = new TopMenu(secretsConfig, sessionService, menuService, topMenuService, playerMenu, registry);
        topMenu.init();

        Player player = Player.create();
        player.con = mock(NetConnection.class);
        PlayerData data = new PlayerData();
        data.uuid = "viewer-1";
        data.nickname = "Viewer";

        session = new Session(
                secretsConfig,
                mock(Bundle.class),
                menuService,
                playerRepo,
                player,
                data
        );

        Localization localization = mock(Localization.class);
        when(localization.t(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.t(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.format(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.format(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.getLocale()).thenReturn(Locale.US);
        session.localization = localization;

        when(sessionService.get("viewer-1")).thenReturn(session);
    }

    @Test
    @DisplayName("custom top category renders entries and buttons dynamically in reactive UI")
    void customCategory_rendersDynamicList() {
        TopCategoryProvider customProvider = new TopCategoryProvider() {
            @Override
            public String id() {
                return "hexed-elo";
            }

            @Override
            public String displayName(Localization local) {
                return "Hexed ELO";
            }

            @Override
            public int priority() {
                return 100;
            }

            @Override
            public LeaderboardPage loadPage(LeaderboardPageRequest request) {
                LeaderboardEntry e1 = new LeaderboardEntry(
                        "player-1", 1, "Alice", "1600",
                        Map.of(),
                        "[gold]1.[] Alice — 1,600 ELO"
                );
                return new LeaderboardPage(1, List.of(e1), false, null, 1L, 1);
            }
        };

        registry.register(customProvider);

        topMenu.topById("viewer-1", "hexed-elo", 1);

        assertThat(session.activeUiSession()).isNotNull();
        assertThat(session.activeUiSession().model()).isInstanceOf(TopUiController.TopModel.class);
        var model = (TopUiController.TopModel) session.activeUiSession().model();
        assertThat(model.selectedCategoryId()).isEqualTo("hexed-elo");
        assertThat(model.entries()).hasSize(1);
        assertThat(model.entries().getFirst().displayName()).isEqualTo("Alice");

        ArgumentCaptor<NodeBuilder<?>> dslCaptor = ArgumentCaptor.forClass(NodeBuilder.class);
        verify(gateway).menuBuilder(eq(session.player), anyInt(), anyLong(), any(), anyBoolean(), anyBoolean(), anyBoolean(), dslCaptor.capture());

        String dsl = UiDslWriter.write(dslCaptor.getValue());
        assertThat(dsl).contains("Alice");
        assertThat(dsl).contains("Hexed ELO");
    }

    @Test
    @DisplayName("category tabs dynamically include registered custom categories")
    void categories_showsDynamicProviders() {
        TopCategoryProvider customProvider = new TopCategoryProvider() {
            @Override
            public String id() {
                return "custom-ladder";
            }

            @Override
            public String displayName(Localization local) {
                return "Ladder";
            }

            @Override
            public int priority() {
                return 100;
            }

            @Override
            public LeaderboardPage loadPage(LeaderboardPageRequest request) {
                return LeaderboardPage.empty(1);
            }
        };

        registry.register(customProvider);

        topMenu.categoriesById("viewer-1", "custom-ladder");

        assertThat(session.activeUiSession()).isNotNull();
        var model = (TopUiController.TopModel) session.activeUiSession().model();
        assertThat(model.categories().stream().anyMatch(c -> c.id().equals("custom-ladder"))).isTrue();

        ArgumentCaptor<NodeBuilder<?>> dslCaptor = ArgumentCaptor.forClass(NodeBuilder.class);
        verify(gateway).menuBuilder(eq(session.player), anyInt(), anyLong(), any(), anyBoolean(), anyBoolean(), anyBoolean(), dslCaptor.capture());

        String dsl = UiDslWriter.write(dslCaptor.getValue());
        assertThat(dsl).contains("Ladder");
    }
}
