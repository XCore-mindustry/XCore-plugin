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
import org.xcore.plugin.config.TomlSecretsConfig;
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
    private TomlSecretsConfig secretsConfig;
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

        secretsConfig = new TomlSecretsConfig();
        secretsConfig.pagination.commandsPerPage = 2;

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

        helpMenu = new HelpMenu(secretsConfig, sessionService, cloudProvider, menuService);

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

        verify(gateway, never()).menuBuilder(any(), anyInt(), anyLong(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    @DisplayName("help with sender sets session sender and opens UI")
    void helpWithSender_setsSessionSenderAndOpensUi() {
        registerLegacyCommands("help", "info", "rules");
        session.sender = null;

        XCoreSender sender = mock(XCoreSender.class);
        Player mockPlayer = session.player;
        when(sender.player()).thenReturn(mockPlayer);
        when(sender.session()).thenReturn(session);

        helpMenu.help(sender, 1);

        assertThat(session.sender).isEqualTo(sender);
        verify(gateway).menuBuilder(eq(session.player), anyInt(), anyLong(), nullable(String.class), anyBoolean(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    @DisplayName("commands are categorized correctly and admin commands are hidden for non-admin players")
    void commandsCategorization_hidesAdminCommandsForRegularPlayers() {
        registerLegacyCommands("help", "votekick", "msg", "hub", "ban");

        // Non-admin player
        session.player.admin = false;
        when(session.sender.isPlayer()).thenReturn(true);
        when(session.sender.player()).thenReturn(session.player);

        var nonAdminItems = helpMenu.buildHelpCommandItems(session, session.sender);
        assertThat(nonAdminItems).anyMatch(c -> c.name().equals("help") && c.category() == org.xcore.plugin.ui.menu.help.HelpCategory.GENERAL);
        assertThat(nonAdminItems).anyMatch(c -> c.name().equals("votekick") && c.category() == org.xcore.plugin.ui.menu.help.HelpCategory.VOTES);
        assertThat(nonAdminItems).anyMatch(c -> c.name().equals("msg") && c.category() == org.xcore.plugin.ui.menu.help.HelpCategory.SOCIAL);
        assertThat(nonAdminItems).anyMatch(c -> c.name().equals("hub") && c.category() == org.xcore.plugin.ui.menu.help.HelpCategory.GAME);
        // ban must be excluded!
        assertThat(nonAdminItems).noneMatch(c -> c.name().equals("ban"));

        // Admin player
        session.player.admin = true;
        var adminItems = helpMenu.buildHelpCommandItems(session, session.sender);
        assertThat(adminItems).anyMatch(c -> c.name().equals("ban") && c.category() == org.xcore.plugin.ui.menu.help.HelpCategory.ADMIN);
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
                secretsConfig,
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
