package org.xcore.plugin.ui;

import com.ospx.flubundle.Bundle;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.DiscordLinkService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.flow.MenuMode;
import org.xcore.plugin.ui.menu.DiscordMenu;

import jakarta.inject.Provider;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscordMenuTest {

    private SessionService sessionService;
    private MindustryMenuGateway gateway;
    private MenuService menuService;
    private DiscordMenu discordMenu;
    private Session session;
    private GlobalConfig globalConfig;
    private DiscordLinkService discordLinkService;

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

        discordLinkService = mock(DiscordLinkService.class);
        discordMenu = new DiscordMenu(config, globalConfig, sessionService, discordLinkService, menuService);
        discordMenu.init();

        session = session();
        when(sessionService.get("viewer-1")).thenReturn(session);
        when(discordLinkService.status(session)).thenReturn(DiscordLinkService.LinkStatusResult.notLinked());
        when(discordLinkService.getOrCreateActiveCode(session)).thenReturn(
                DiscordLinkService.LinkCodeResult.success("ABCD12", System.currentTimeMillis() + 10 * 60_000L)
        );
    }

    @Test
    @DisplayName("main open button routes through gateway openUri")
    void main_openButtonRoutesThroughGatewayOpenUri() {
        discordMenu.main("viewer-1");

        menuService.onMenuOption(session, 0);

        verify(gateway).openUri(eq(session.player), eq(globalConfig.discordUrl));
    }

    @Test
    @DisplayName("linking copy button routes through gateway copyToClipboard")
    void linking_copyButtonRoutesThroughGatewayCopyToClipboard() {
        discordMenu.linking("viewer-1", false);

        menuService.onMenuOption(session, 1);

        verify(gateway).copyToClipboard(eq(session.player), eq("ABCD12"));
    }

    @Test
    @DisplayName("linking renders via gateway followUpMenu and sets active screen mode FOLLOW_UP")
    void linking_rendersViaFollowUpMenu() {
        discordMenu.linking("viewer-1", false);

        verify(gateway).followUpMenu(eq(session.player), eq(menuService.getMenuId()), any(), any(), any());
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.FOLLOW_UP);
    }

    @Test
    @DisplayName("linking dismiss -1 hides follow-up and clears active screen")
    void linking_dismissHidesFollowUp() {
        discordMenu.linking("viewer-1", false);

        menuService.onMenuOption(session, -1);

        verify(gateway).hideFollowUpMenu(eq(session.player), eq(menuService.getMenuId()));
        assertThat(session.activeScreen()).isNull();
    }

    @Test
    @DisplayName("linking status action returns to main menu via normal show")
    void linking_statusActionReturnsToMain() {
        discordMenu.linking("viewer-1", false);

        menuService.onMenuOption(session, 4); // status button without history

        verify(gateway).hideFollowUpMenu(eq(session.player), eq(menuService.getMenuId()));
        verify(gateway).menu(eq(session.player), eq(menuService.getMenuId()), any(), any(), any());
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.NORMAL);
    }

    @Test
    @DisplayName("linking back action hides follow-up before restoring previous menu")
    void linking_backActionHidesFollowUpBeforeRestoringPreviousMenu() {
        AtomicBoolean previousMenuRan = new AtomicBoolean(false);
        session.pushHistory(() -> {
            previousMenuRan.set(true);
            discordMenu.main("viewer-1");
        });
        discordMenu.linking("viewer-1", false);

        menuService.onMenuOption(session, 5); // back button when history exists

        assertThat(previousMenuRan).isTrue();
        var inOrder = inOrder(gateway);
        inOrder.verify(gateway).followUpMenu(eq(session.player), eq(menuService.getMenuId()), any(), any(), any());
        inOrder.verify(gateway).hideFollowUpMenu(eq(session.player), eq(menuService.getMenuId()));
        inOrder.verify(gateway).menu(eq(session.player), eq(menuService.getMenuId()), any(), any(), any());
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.NORMAL);
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
        when(localization.t(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.getLocale()).thenReturn(Locale.US);
        session.localization = localization;
        return session;
    }
}
