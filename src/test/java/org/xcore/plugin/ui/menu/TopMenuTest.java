package org.xcore.plugin.ui;

import com.ospx.flubundle.Bundle;
import jakarta.inject.Provider;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.integration.top.LeaderboardEntry;
import org.xcore.plugin.integration.top.LeaderboardPage;
import org.xcore.plugin.integration.top.LeaderboardPageRequest;
import org.xcore.plugin.integration.top.TopCategoryProvider;
import org.xcore.plugin.integration.top.TopCategoryRegistry;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.enums.TopCategory;
import org.xcore.plugin.service.TopMenuService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.menu.PlayerMenu;
import org.xcore.plugin.ui.menu.TopMenu;
import org.xcore.plugin.ui.menu.TopUiController;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TopMenuTest {

    private MindustryMenuGateway gateway;
    private MenuService menuService;
    private SessionService sessionService;
    private TopMenuService topMenuService;
    private PlayerMenu playerMenu;
    private TopCategoryRegistry registry;
    private TopMenu topMenu;
    private Session session;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        gateway = mock(MindustryMenuGateway.class);
        sessionService = mock(SessionService.class);
        topMenuService = mock(TopMenuService.class);
        playerMenu = mock(PlayerMenu.class);
        registry = new TopCategoryRegistry();

        Provider<SessionService> sessionProvider = mock(Provider.class);
        when(sessionProvider.get()).thenReturn(sessionService);
        menuService = new MenuService(sessionProvider, gateway);

        topMenu = new TopMenu(new TomlSecretsConfig(), sessionService, menuService, topMenuService, playerMenu, registry);
        topMenu.init();

        Player player = Player.create();
        player.con = mock(NetConnection.class);
        player.con.mobile = false;

        PlayerData data = new PlayerData("viewer-1", true);
        data.pid = 42;
        data.nickname = "Viewer";

        Bundle bundle = mock(Bundle.class);
        Localization local = mock(Localization.class);
        when(local.getLocale()).thenReturn(Locale.ENGLISH);
        when(local.t(eq("player-menu-time-days"), anyMap())).thenAnswer(i -> ((Map<?, ?>) i.getArgument(1)).get("value") + "d");
        when(local.t(eq("player-menu-time-hours"), anyMap())).thenAnswer(i -> ((Map<?, ?>) i.getArgument(1)).get("value") + "h");
        when(local.t(eq("player-menu-time-minutes"), anyMap())).thenAnswer(i -> ((Map<?, ?>) i.getArgument(1)).get("value") + "m");
        when(local.format(anyString())).thenAnswer(i -> i.getArgument(0));
        when(local.format(anyString(), anyMap())).thenAnswer(i -> i.getArgument(0));

        session = new Session(
                new TomlSecretsConfig(),
                bundle,
                menuService,
                mock(PlayerDataRepository.class),
                player,
                data
        );
        session.localization = local;

        when(sessionService.get("viewer-1")).thenReturn(session);
    }

    private void registerMockProvider(String id, int priority) {
        TopCategoryProvider provider = mock(TopCategoryProvider.class);
        when(provider.id()).thenReturn(id);
        when(provider.displayName(any())).thenReturn(id);
        when(provider.priority()).thenReturn(priority);
        when(provider.loadPage(any())).thenAnswer(inv -> {
            LeaderboardPageRequest req = inv.getArgument(0);
            return new LeaderboardPage(req.page(), List.of(), false, null, 0L, null);
        });
        registry.registerIfAbsent(provider);
    }

    @Test
    @DisplayName("top with default category opens reactive UI")
    void top_defaultCategory_opensReactiveUi() {
        registerMockProvider("MINI_PVP", 20);
        registry.setDefaultCategory("MINI_PVP");

        topMenu.top("viewer-1");

        verify(gateway).menuBuilder(eq(session.player), anyInt(), anyLong(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    @DisplayName("top with specified category and page opens reactive UI")
    void top_specifiedCategory_opensReactiveUi() {
        registerMockProvider("PLAYTIME", 20);

        topMenu.top("viewer-1", TopCategory.PLAYTIME, 2);

        verify(gateway).menuBuilder(eq(session.player), anyInt(), anyLong(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    @DisplayName("topById opens reactive UI with custom category id")
    void topById_opensReactiveUi() {
        registerMockProvider("CUSTOM_SEASON", 50);

        topMenu.topById("viewer-1", "CUSTOM_SEASON", 1);

        verify(gateway).menuBuilder(eq(session.player), anyInt(), anyLong(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    @DisplayName("openTopUi creates session and mounts TopUiController")
    void openTopUi_mountsController() {
        registerMockProvider("MINI_PVP", 10);

        topMenu.openTopUi(session, "MINI_PVP", 1, null, new ArrayDeque<>());

        assertThat(session.activeUiSession()).isNotNull();
        assertThat(session.activeUiSession().model()).isInstanceOf(TopUiController.TopModel.class);
        verify(gateway).menuBuilder(eq(session.player), anyInt(), anyLong(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    @DisplayName("formatPlayTime formats duration correctly")
    void formatPlayTime_formatsDuration() {
        Localization local = session.locale();
        String result = topMenu.formatPlayTime(24 * 60 + 2 * 60 + 5, local);
        assertThat(result).isEqualTo("1d 2h 5m");

        String zero = topMenu.formatPlayTime(0, local);
        assertThat(zero).isEqualTo("0m");
    }
}
