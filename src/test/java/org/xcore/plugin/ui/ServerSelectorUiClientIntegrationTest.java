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
import org.xcore.protocol.generated.messages.server.ServerMessages.ServerHeartbeatV1;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.ServerRegistryService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.menu.ServerMenu;
import org.xcore.testkit.ui.DeterministicUiLoop;
import org.xcore.testkit.ui.UiSnapshot;
import org.xcore.testkit.ui.UiWireMessage;

import java.util.Locale;

import static mindustry.Vars.state;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end integration test driving MenuService, UiSession, and ServerSelectorUiController
 * through DeterministicUiLoop and HeadlessMenuClient to verify what the client is sent when the
 * browser opens, when a category tab is pressed and when the dialog is closed.
 */
class ServerSelectorUiClientIntegrationTest {

    private GameState originalState;
    private DeterministicUiLoop loop;
    private MenuService menuService;
    private Session session;
    private Player player;
    private SessionService sessionService;
    private ServerRegistryService registryService;
    private ServerMenu serverMenu;

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

        PlayerData data = new PlayerData("test-player-uuid", true);
        data.pid = 42;
        data.nickname = "Explorer";

        Bundle bundle = mock(Bundle.class);
        com.ospx.flubundle.Localizer localizer = mock(com.ospx.flubundle.Localizer.class);
        when(localizer.locale()).thenReturn(Locale.ENGLISH);
        when(localizer.format(anyString())).thenAnswer(i -> {
            String key = i.getArgument(0);
            return switch (key) {
                case "player-servers-badge-current" -> "ВЫ ЗДЕСЬ";
                default -> key;
            };
        });
        when(localizer.format(anyString(), anyMap())).thenAnswer(i -> {
            String key = i.getArgument(0);
            return switch (key) {
                case "player-servers-badge-current" -> "ВЫ ЗДЕСЬ";
                default -> key;
            };
        });
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
                                    boolean hideOnClick, boolean hideExisting, boolean fillScreen, NodeBuilder<?> ui) {
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

        player = spy(Player.create());
        player.con = mock(NetConnection.class);
        doNothing().when(player).sendMessage(anyString());

        session = new Session(
                new TomlSecretsConfig(),
                bundle,
                menuService,
                mock(PlayerDataRepository.class),
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

        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";
        config.server.playerLimit = 25;
        NetworkService network = mock(NetworkService.class);
        registryService = new ServerRegistryService(config, network);

        // Heartbeat for surv, hexed and siege
        registryService.handleHeartbeat(new ServerHeartbeatV1(
                "mini-surv", 1L, 12, 20, "v160", "play.xcore.top", 7002, "Survival", "Islands", 1, "survival", 60
        ));
        registryService.handleHeartbeat(new ServerHeartbeatV1(
                "hexedcore", 3L, 8, 16, "v160", "play.xcore.top", 7005, "Battle Royale", "Hexed", 1, "hexed", 60
        ));
        registryService.handleHeartbeat(new ServerHeartbeatV1(
                "siege", 2L, 5, 20, "v160", "play.xcore.top", 7007, "Siege defense", "Citadel", 1, "siege", 60
        ));

        serverMenu = new ServerMenu(registryService, menuService);
    }

    @AfterEach
    void tearDown() {
        state = originalState;
        Core.app = null;
    }

    @Test
    @DisplayName("Open servers delivers a window per screen class with tabs and server rows")
    void openServers_deliversResponsiveDialog() {
        serverMenu.open(session);
        int menuId = menuService.getMenuBuilderId();

        assertThat(loop.client().isVisible(menuId)).isFalse();

        // Step server -> client
        assertThat(loop.stepServerToClient()).isTrue();
        assertThat(loop.client().isVisible(menuId)).isTrue();
        assertThat(session.hasActiveUiSession()).isTrue();

        // Inspect delivered binary wire DSL
        var lastMsg = (UiWireMessage.Show) loop.transcript().all().stream()
                .filter(m -> m instanceof UiWireMessage.Show)
                .reduce((first, second) -> second)
                .orElseThrow();
        String dsl = UiDslWriter.write((NodeBuilder<?>) lastMsg.body().decode());

        assertThat(dsl).contains("pane{");
        assertThat(dsl).contains("condition: \"width >= 800\"");
        assertThat(dsl).doesNotContain("action:close");
        assertThat(dsl).contains("action:tab:all");
        assertThat(dsl).contains("action:tab:pvp");
        assertThat(dsl).contains("action:connect:mini-pvp");
        assertThat(dsl).contains("action:connect:mini-surv");
        assertThat(dsl).contains("action:refresh");
        assertThat(dsl).contains("ВЫ ЗДЕСЬ");
    }

    @Test
    @DisplayName("Selecting PvP tab triggers full rerender with updated active tab and filtered list")
    void selectTab_triggersPartialUpdate() {
        serverMenu.open(session);
        int menuId = menuService.getMenuBuilderId();
        assertThat(loop.stepServerToClient()).isTrue();

        // Client chooses PvP tab
        loop.client().click(menuId, "action:tab:pvp");
        assertThat(loop.stepClientToServer()).isTrue();

        // Server processes event and emits Show wire message with new active tab and filtered servers
        assertThat(loop.stepServerToClient()).isTrue();

        var showMsg = (UiWireMessage.Show) loop.transcript().all().stream()
                .filter(m -> m instanceof UiWireMessage.Show)
                .reduce((first, second) -> second)
                .orElseThrow();

        String showDsl = UiDslWriter.write((NodeBuilder<?>) showMsg.body().decode());
        assertThat(showDsl).contains("Mini-PvP");
        assertThat(showDsl).contains("HexedCore");
        assertThat(showDsl).doesNotContain("Mini-Surv");
    }

    @Test
    void rejectedTransfer_keepsClientDialogVisibleAndShowsReason() {
        serverMenu.open(session);
        int menuId = menuService.getMenuBuilderId();
        assertThat(loop.stepServerToClient()).isTrue();
        loop.client().click(menuId, "action:connect:mini-pvp");
        assertThat(loop.stepClientToServer()).isTrue();
        assertThat(loop.stepServerToClient()).isTrue();
        assertThat(loop.client().isVisible(menuId)).isTrue();
        assertThat(session.hasActiveUiSession()).isTrue();
        assertThat(loop.transcript().all()).noneMatch(message -> message instanceof UiWireMessage.Hide);
        var last = (UiWireMessage.Show) loop.transcript().all().stream()
                .filter(message -> message instanceof UiWireMessage.Show)
                .reduce((first, second) -> second).orElseThrow();
        assertThat(UiDslWriter.write((NodeBuilder<?>) last.body().decode()))
                .contains("player-servers-already-connected", "action:refresh");
        verify(player).sendMessage("player-servers-already-connected");
    }

    @Test
    @DisplayName("closing the dialog with the client's own button ends the session")
    void dismissDialog_closesSession() {
        serverMenu.open(session);
        int menuId = menuService.getMenuBuilderId();
        assertThat(loop.stepServerToClient()).isTrue();
        assertThat(loop.client().isVisible(menuId)).isTrue();

        // The dialog has no close button of its own: the client's one reports a cancel.
        loop.client().dismiss(menuId);
        assertThat(loop.stepClientToServer()).isTrue();

        assertThat(loop.client().isVisible(menuId)).isFalse();
        assertThat(session.hasActiveUiSession()).isFalse();
    }
}
