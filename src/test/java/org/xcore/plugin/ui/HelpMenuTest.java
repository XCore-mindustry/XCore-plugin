package org.xcore.plugin.ui;

import arc.util.CommandHandler;
import com.ospx.flubundle.Bundle;
import jakarta.inject.Provider;
import mindustry.Vars;
import mindustry.core.NetServer;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.incendo.cloud.Command;
import org.incendo.cloud.help.HelpHandler;
import org.incendo.cloud.help.result.IndexCommandResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.cloud.CloudService;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.flow.MenuMode;
import org.xcore.plugin.ui.menu.HelpMenu;
import org.xcore.plugin.ui.route.MenuRoute;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class HelpMenuTest {

    private SessionService sessionService;
    private MindustryMenuGateway gateway;
    private MenuService menuService;
    private HelpMenu helpMenu;
    private Session session;
    private GlobalConfig globalConfig;
    private NetServer previousNetServer;
    private CloudService cloudService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        sessionService = mock(SessionService.class);
        gateway = mock(MindustryMenuGateway.class);
        Provider<SessionService> sessionProvider = mock(Provider.class);
        when(sessionProvider.get()).thenReturn(sessionService);
        menuService = new MenuService(sessionProvider, gateway);

        globalConfig = new GlobalConfig();
        globalConfig.commandsPerPage = 2;

        cloudService = mock(CloudService.class);
        HelpHandler<XCoreSender> helpHandler = mock(HelpHandler.class);
        IndexCommandResult<XCoreSender> indexResult = mock(IndexCommandResult.class);
        when(cloudService.getHelpHandler()).thenReturn(helpHandler);
        when(helpHandler.queryRootIndex(any())).thenReturn(indexResult);
        when(indexResult.entries()).thenReturn(java.util.List.of());
        when(cloudService.isCommandDisabled(any(Command.class))).thenReturn(false);
        when(cloudService.isCommandDisabled(anyString())).thenReturn(false);

        Provider<CloudService> cloudProvider = mock(Provider.class);
        when(cloudProvider.get()).thenReturn(cloudService);

        helpMenu = new HelpMenu(globalConfig, sessionService, cloudProvider, menuService);
        helpMenu.init();

        previousNetServer = Vars.netServer;
        NetServer netServer = mock(NetServer.class);
        netServer.clientCommands = new CommandHandler("");
        Vars.netServer = netServer;

        session = session();
        when(sessionService.get("viewer-1")).thenReturn(session);
    }

    @AfterEach
    void tearDown() {
        Vars.netServer = previousNetServer;
    }

    @Test
    @DisplayName("sender missing sends error-internal and does not render menu")
    void senderMissing_sendsErrorInternalAndDoesNotRenderMenu() {
        session.sender = null;

        helpMenu.help("viewer-1", 1);

        assertThat(session.activeScreen()).isNull();
        verify(gateway, never()).menu(any(), anyInt(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("first page render shows commands and pagination")
    void firstPageRender_showsCommandsAndPagination() {
        registerLegacyCommands("help", "info", "rules");

        helpMenu.help("viewer-1", 1);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.NORMAL);
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of("help.list").withParam("page", "1"));
        assertThat(session.activeScreen().actionCount()).isEqualTo(4);
        verify(gateway).menu(eq(session.player), eq(0), eq("help-menu-title"), eq("help-menu-content"), any());
    }

    @Test
    @DisplayName("next page navigation renders second page")
    void nextPageNavigation_rendersSecondPage() {
        registerLegacyCommands("help", "info", "rules");
        helpMenu.help("viewer-1", 1);

        menuService.onMenuOption(session, 0);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of("help.list").withParam("page", "2"));
        assertThat(session.activeScreen().actionCount()).isEqualTo(3);
        verify(gateway, times(2)).menu(eq(session.player), eq(0), eq("help-menu-title"), eq("help-menu-content"), any());
    }

    @Test
    @DisplayName("previous page navigation renders first page")
    void previousPageNavigation_rendersFirstPage() {
        registerLegacyCommands("help", "info", "rules");
        helpMenu.help("viewer-1", 2);

        menuService.onMenuOption(session, 0);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of("help.list").withParam("page", "1"));
        assertThat(session.activeScreen().actionCount()).isEqualTo(4);
        verify(gateway, times(2)).menu(eq(session.player), eq(0), eq("help-menu-title"), eq("help-menu-content"), any());
    }

    @Test
    @DisplayName("opening command details renders details route")
    void openingCommandDetails_rendersDetailsRoute() {
        registerLegacyCommands("help", "info", "rules");
        helpMenu.help("viewer-1", 1);

        menuService.onMenuOption(session, 1);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route()).isEqualTo(
                MenuRoute.of("help.details").withParam("cmd", "help").withParam("returnPage", "1")
        );
        assertThat(session.activeScreen().actionCount()).isEqualTo(3);
        verify(gateway).menu(eq(session.player), eq(0), eq("help-command-title"), anyString(), any());
    }

    @Test
    @DisplayName("back from details returns to list")
    void backFromDetails_returnsToList() {
        registerLegacyCommands("help", "info", "rules");
        helpMenu.help("viewer-1", 1);
        menuService.onMenuOption(session, 1);

        menuService.onMenuOption(session, 0);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().route()).isEqualTo(MenuRoute.of("help.list").withParam("page", "1"));
        assertThat(session.activeScreen().actionCount()).isEqualTo(4);
        verify(gateway, times(2)).menu(eq(session.player), eq(0), eq("help-menu-title"), eq("help-menu-content"), any());
    }

    private void registerLegacyCommands(String... names) {
        for (String name : names) {
            Vars.netServer.clientCommands.register(name, name + " desc", (args, player) -> {});
        }
    }

    private Session session() {
        Player player = Player.create();
        player.con = mock(NetConnection.class);
        PlayerData data = new PlayerData("viewer-1", true);
        data.uuid = "viewer-1";
        Session session = new Session(
                globalConfig,
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

        XCoreSender sender = mock(XCoreSender.class);
        session.sender = sender;
        return session;
    }
}
