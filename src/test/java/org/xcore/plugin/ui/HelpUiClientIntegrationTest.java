package org.xcore.plugin.ui;

import arc.util.CommandHandler;
import com.ospx.flubundle.Bundle;
import jakarta.inject.Provider;
import mindustry.Vars;
import mindustry.core.GameState;
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
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.menu.HelpMenu;
import org.xcore.testkit.ui.DeterministicUiLoop;
import org.xcore.testkit.ui.UiWireMessage;

import java.util.Locale;
import java.util.Map;

import static mindustry.Vars.state;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class HelpUiClientIntegrationTest {

    private GameState originalState;
    private NetServer originalNetServer;
    private DeterministicUiLoop loop;
    private DeterministicUiLoop.CoreAppScope coreApp;
    private MenuService menuService;
    private Session session;
    private SessionService sessionService;
    private HelpMenu helpMenu;

    @BeforeEach
    void setUp() {
        originalState = state;
        state = new GameState();

        loop = new DeterministicUiLoop();

        coreApp = loop.installAsCoreApp();

        PlayerData data = new PlayerData("test-player-uuid", true);
        data.pid = 42;
        data.nickname = "Explorer";

        Bundle bundle = mock(Bundle.class);
        com.ospx.flubundle.Localizer localizer = mock(com.ospx.flubundle.Localizer.class);
        when(localizer.locale()).thenReturn(Locale.ENGLISH);
        when(localizer.format(anyString())).thenAnswer(i -> i.getArgument(0));
        when(localizer.format(anyString(), anyMap())).thenAnswer(i -> i.getArgument(0));
        when(bundle.localizer(any(java.util.function.Supplier.class))).thenReturn(localizer);

        sessionService = mock(SessionService.class);
        Provider<SessionService> sessionProvider = () -> sessionService;

        MindustryMenuGateway gateway = new LoopMenuGateway(loop);

        menuService = new MenuService(sessionProvider, gateway);
        menuService.init();

        Player player = Player.create();
        player.con = mock(NetConnection.class);

        session = new Session(
                new TomlSecretsConfig(),
                bundle,
                menuService,
                mock(PlayerDataRepository.class),
                player,
                data
        );
        org.xcore.plugin.localization.Localization localization = mock(org.xcore.plugin.localization.Localization.class);
        when(localization.t(anyString())).thenAnswer(i -> i.getArgument(0));
        when(localization.t(anyString(), anyMap())).thenAnswer(i -> i.getArgument(0));
        when(localization.format(anyString(), anyMap())).thenAnswer(i -> i.getArgument(0));
        when(localization.getLocale()).thenReturn(Locale.ENGLISH);
        session.localization = localization;
        session.sender = mock(XCoreSender.class);
        when(sessionService.get(anyString())).thenReturn(session);

        loop.onChoose(choose -> menuService.onMenuBuilderResult(session, choose.toMenuResult()));

        CloudService cloudService = mock(CloudService.class);
        HelpHandler<XCoreSender> helpHandler = mock(HelpHandler.class);
        IndexCommandResult<XCoreSender> indexResult = mock(IndexCommandResult.class);
        when(cloudService.getHelpHandler()).thenReturn(helpHandler);
        when(helpHandler.queryRootIndex(any())).thenReturn(indexResult);
        when(indexResult.entries()).thenReturn(java.util.List.of());
        when(cloudService.isCommandDisabled(any(Command.class))).thenReturn(false);
        when(cloudService.isCommandDisabled(anyString())).thenReturn(false);

        helpMenu = new HelpMenu(new TomlSecretsConfig(), sessionService, () -> cloudService, menuService);

        originalNetServer = Vars.netServer;
        NetServer netServer = mock(NetServer.class);
        netServer.clientCommands = new CommandHandler("/");
        netServer.clientCommands.register("hub", "Connect to server", (args, p) -> {});
        netServer.clientCommands.register("votekick", "<player> [reason]", "Kick a player", (args, p) -> {});
        netServer.clientCommands.register("sync", "Resynchronize state", (args, p) -> {});
        Vars.netServer = netServer;
    }

    @AfterEach
    void tearDown() {
        state = originalState;
        Vars.netServer = originalNetServer;
        coreApp.close();
    }

    @Test
    @DisplayName("Opening HelpMenu sends Show wire message with clean command cards")
    void openHelp_sendsShowWireMessage() {
        helpMenu.open(session);
        assertThat(loop.stepServerToClient()).isTrue();

        var showMsg = loop.transcript().lastShow();
        String dsl = showMsg.dsl();
        System.out.println("=== DSL OUTPUT ===\n" + dsl + "\n=================");

        assertThat(dsl).contains("help-ui-title");
        assertThat(dsl).contains("action:cmd:hub");
        assertThat(dsl).contains("action:cmd:votekick");
        assertThat(dsl).contains("action:cmd:sync");

        // Ensure no double slashes like //hub or //sync
        assertThat(dsl).doesNotContain("//hub");
        assertThat(dsl).doesNotContain("//sync");
    }

    @Test
    @DisplayName("Clicking command card opens details view with parameters and action buttons")
    void clickCard_opensDetailsView() {
        helpMenu.open(session);
        assertThat(loop.stepServerToClient()).isTrue();
        int menuId = menuService.getMenuBuilderId();

        // Click on "hub" command card
        loop.client().click(menuId, "action:cmd:hub");
        assertThat(loop.stepClientToServer()).isTrue();

        // Server processes and rerenders details view
        assertThat(loop.stepServerToClient()).isTrue();

        var showMsg = loop.transcript().lastShow();
        String dsl = showMsg.dsl();

        assertThat(dsl).contains("action:back");
        assertThat(dsl).contains("action:run:hub");
        assertThat(dsl).contains("action:copy:hub");
        assertThat(dsl).doesNotContain("//hub");
    }

    @Test
    void search_deliversFilteredSlotsAndPreservesQueryAfterDetails() {
        helpMenu.open(session);
        assertThat(loop.stepServerToClient()).isTrue();
        int menuId = menuService.getMenuBuilderId();
        loop.client().click(menuId, "action:search", Map.of("field_help_search", "resynchronize"));
        assertThat(loop.stepClientToServer()).isTrue();
        loop.deliverToClient();
        assertThat(loop.client().isVisible(menuId)).isTrue();
        var updates = loop.transcript().updates();
        assertThat(updates).isNotEmpty();
        String patched = updates.stream().map(UiWireMessage.Update::dsl)
                .collect(java.util.stream.Collectors.joining("\n"));
        assertThat(patched).contains("resynchronize", "action:cmd:sync").doesNotContain("action:cmd:hub");

        loop.client().click(menuId, "action:cmd:sync");
        assertThat(loop.stepClientToServer()).isTrue();
        loop.deliverToClient();
        loop.client().click(menuId, "action:back");
        assertThat(loop.stepClientToServer()).isTrue();
        loop.deliverToClient();
        var last = loop.transcript().lastShow();
        assertThat(last.dsl())
                .contains("resynchronize", "action:cmd:sync").doesNotContain("action:cmd:hub");
    }

    @Test
    @DisplayName("Executing command closes help menu cleanly and posts command without immediate hide collision")
    void executeCommand_closesHelpAndPostsCommand() {
        java.util.concurrent.atomic.AtomicBoolean hubExecuted = new java.util.concurrent.atomic.AtomicBoolean(false);
        Vars.netServer.clientCommands.register("customrun", "Custom command", (args, p) -> hubExecuted.set(true));

        helpMenu.open(session);
        assertThat(loop.stepServerToClient()).isTrue();
        int menuId = menuService.getMenuBuilderId();

        // Click on "customrun" card to open details
        loop.client().click(menuId, "action:cmd:customrun");
        assertThat(loop.stepClientToServer()).isTrue();
        assertThat(loop.stepServerToClient()).isTrue();

        // Click "Run" button
        loop.client().click(menuId, "action:run:customrun");
        assertThat(loop.stepClientToServer()).isTrue();

        // Server closes the UI and posts execution
        assertThat(loop.stepServerToClient()).isTrue();

        // Step the posted server runnables
        loop.stepServerPost();
        assertThat(hubExecuted.get()).isTrue();
    }
}
