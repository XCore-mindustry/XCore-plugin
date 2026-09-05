package org.xcore.plugin.ui.menu;

import com.ospx.flubundle.Bundle;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void setUp() {
        sessionService = mock(SessionService.class);
        gateway = mock(MindustryMenuGateway.class);
        playerMenu = mock(PlayerMenu.class);

        var secretsConfig = new TomlSecretsConfig();
        menuService = new MenuService(null, gateway);

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
        when(localization.getLocale()).thenReturn(Locale.US);
        session.localization = localization;

        when(sessionService.get("viewer-1")).thenReturn(session);
    }

    @Test
    @DisplayName("custom top category renders entries and buttons dynamically")
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
                        Map.of("icon", ":titanium:"),
                        "[gold]1.[] :titanium: Alice — 1,600 ELO"
                );
                return new LeaderboardPage(1, List.of(e1), false, null, 1L, 1);
            }
        };

        registry.register(customProvider);

        topMenu.topById("viewer-1", "hexed-elo", 1);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().route().id()).isEqualTo("top.list");
        assertThat(session.activeScreen().route().param("category")).isEqualTo("hexed-elo");

        ArgumentCaptor<String[][]> buttonsCaptor = ArgumentCaptor.forClass(String[][].class);
        verify(gateway).followUpMenu(eq(session.player), anyInt(), any(), any(), buttonsCaptor.capture());

        String[][] buttons = buttonsCaptor.getValue();
        assertThat(buttons.length).isGreaterThanOrEqualTo(2);
        assertThat(buttons[0][0]).contains("Alice");
    }

    @Test
    @DisplayName("categories screen dynamically shows registered custom categories")
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

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().route().id()).isEqualTo("top.categories");

        ArgumentCaptor<String[][]> buttonsCaptor = ArgumentCaptor.forClass(String[][].class);
        verify(gateway).menu(eq(session.player), anyInt(), any(), any(), buttonsCaptor.capture());

        String[][] buttons = buttonsCaptor.getValue();
        boolean foundCustom = false;
        for (String[] row : buttons) {
            for (String btn : row) {
                if (btn.contains("Ladder")) {
                    foundCustom = true;
                    assertThat(btn).contains("[accent]●[]"); // selected
                }
            }
        }
        assertThat(foundCustom).isTrue();
    }
}
