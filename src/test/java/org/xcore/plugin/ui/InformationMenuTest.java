package org.xcore.plugin.ui;

import com.ospx.flubundle.Bundle;
import jakarta.inject.Provider;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.common.BuildInfo;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.flow.MenuMode;
import org.xcore.plugin.ui.menu.EventMenu;
import org.xcore.plugin.ui.menu.HelpMenu;
import org.xcore.plugin.ui.menu.InformationMenu;
import org.xcore.plugin.ui.menu.MapMenu;
import org.xcore.plugin.ui.menu.PlayerMenu;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InformationMenuTest {

    private SessionService sessionService;
    private MindustryMenuGateway gateway;
    private MenuService menuService;
    private InformationMenu informationMenu;
    private Session session;
    private GlobalConfig globalConfig;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        sessionService = mock(SessionService.class);
        gateway = mock(MindustryMenuGateway.class);
        Provider<SessionService> sessionProvider = mock(Provider.class);
        when(sessionProvider.get()).thenReturn(sessionService);
        menuService = new MenuService(sessionProvider, gateway);

        Config config = new Config();
        config.server = "xcore";
        globalConfig = new GlobalConfig();
        globalConfig.discordUrl = "https://discord.example";
        globalConfig.githubUrl = "https://github.example";
        globalConfig.donatelloUrl = "https://donate.example";
        globalConfig.weblateUrl = "https://translate.example";
        globalConfig.discordRedVSBlueUrl = "https://rvb.example";

        BuildInfo buildInfo = new BuildInfo();
        buildInfo.setVersion("test-version");
        Provider<MapMenu> map = mock(Provider.class);
        Provider<EventMenu> event = mock(Provider.class);
        Provider<HelpMenu> help = mock(Provider.class);
        Provider<PlayerMenu> player = mock(Provider.class);
        informationMenu = new InformationMenu(config, globalConfig, sessionService, buildInfo, map, event, help, player);

        session = session();
        when(sessionService.get("viewer-1")).thenReturn(session);
    }

    @Test
    @DisplayName("information renders through flow runtime")
    void information_rendersThroughFlowRuntime() {
        informationMenu.information("viewer-1");

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.FOLLOW_UP);
        assertThat(session.activeScreen().actionCount()).isEqualTo(6);
        verify(gateway).followUpMenu(eq(session.player), eq(0), eq("commands-info-title"), eq("commands-info-text"), any());
    }

    @Test
    @DisplayName("information includes back action only when history exists")
    void information_includesBackActionOnlyWhenHistoryExists() {
        session.pushHistory(() -> {});

        informationMenu.information("viewer-1");

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().actionCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("information back action runs previous menu")
    void information_backActionRunsPreviousMenu() {
        final boolean[] ran = {false};
        session.pushHistory(() -> ran[0] = true);
        informationMenu.information("viewer-1");

        menuService.onMenuOption(session, 5);

        verify(gateway).hideFollowUpMenu(eq(session.player), eq(0));
        assertThat(ran[0]).isTrue();
    }

    @Test
    @DisplayName("information close action clears active screen")
    void information_closeActionClearsActiveScreen() {
        informationMenu.information("viewer-1");

        menuService.onMenuOption(session, 5);

        assertThat(session.activeScreen()).isNull();
        verify(gateway).hideFollowUpMenu(eq(session.player), eq(0));
    }

    @Test
    @DisplayName("information discord action routes through gateway openUri")
    void information_discordActionRoutesThroughGatewayOpenUri() {
        informationMenu.information("viewer-1");

        menuService.onMenuOption(session, 0);

        verify(gateway).openUri(eq(session.player), eq(globalConfig.discordUrl));
    }

    private Session session() {
        Player player = Player.create();
        player.con = mock(NetConnection.class);
        PlayerData data = new PlayerData("viewer-1", true);
        data.uuid = "viewer-1";
        Session session = new Session(
                new GlobalConfig(),
                mock(Bundle.class),
                menuService,
                mock(PlayerDataRepository.class),
                player,
                data
        );
        Localization localization = mock(Localization.class);
        when(localization.t(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.t(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.getLocale()).thenReturn(Locale.US);
        session.localization = localization;
        return session;
    }
}
