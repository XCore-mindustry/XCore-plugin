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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end integration test driving MenuService, UiSession, and ServerSelectorUiController
 * through DeterministicUiLoop and HeadlessMenuClient to verify mobile landscape scrolling,
 * responsive layout constraints, partial slot updates on category tabs, and connection handling.
 */
class ServerSelectorUiClientIntegrationTest {

    private GameState originalState;
    private DeterministicUiLoop loop;
    private MenuService menuService;
    private Session session;
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

        // Heartbeat for surv and siege
        registryService.handleHeartbeat(new ServerHeartbeatV1(
                "mini-surv", 1L, 12, 20, "v160", "play.xcore.top", 7002
        ));
        registryService.handleHeartbeat(new ServerHeartbeatV1(
                "siege", 2L, 5, 20, "v160", "play.xcore.top", 7007
        ));

        serverMenu = new ServerMenu(registryService, menuService);
    }

    @AfterEach
    void tearDown() {
        state = originalState;
        Core.app = null;
    }

    @Test
    @DisplayName("Open servers delivers responsive dialog with buttonTable cards, tabs, and maxHeight 350")
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
        assertThat(dsl).contains("maxHeight: 350");
        assertThat(dsl).contains("action:close");
        assertThat(dsl).contains("action:tab:all");
        assertThat(dsl).contains("action:tab:pvp");
        assertThat(dsl).contains("action:connect:mini-pvp");
        assertThat(dsl).contains("action:connect:mini-surv");
        assertThat(dsl).contains("action:refresh");
        assertThat(dsl).contains("ВЫ ЗДЕСЬ");
    }

    @Test
    @DisplayName("Selecting PvP tab triggers partial update targeting slot_servers")
    void selectTab_triggersPartialUpdate() {
        serverMenu.open(session);
        int menuId = menuService.getMenuBuilderId();
        assertThat(loop.stepServerToClient()).isTrue();

        // Client chooses PvP tab
        loop.client().click(menuId, "action:tab:pvp");
        assertThat(loop.stepClientToServer()).isTrue();

        // Server processes event and emits Update wire message targeting slot_servers
        assertThat(loop.stepServerToClient()).isTrue();

        var updateMsg = (UiWireMessage.Update) loop.transcript().all().stream()
                .filter(m -> m instanceof UiWireMessage.Update)
                .reduce((first, second) -> second)
                .orElseThrow();

        assertThat(updateMsg.targetId()).isEqualTo("slot_servers");

        String updateDsl = UiDslWriter.write((NodeBuilder<?>) updateMsg.body().decode());
        assertThat(updateDsl).contains("Mini-PvP");
        assertThat(updateDsl).contains("HexedCore");
        assertThat(updateDsl).doesNotContain("Mini-Surv");
    }

    @Test
    @DisplayName("Close button dismisses servers dialog cleanly")
    void closeButton_dismissesDialog() {
        serverMenu.open(session);
        int menuId = menuService.getMenuBuilderId();
        assertThat(loop.stepServerToClient()).isTrue();

        // Client clicks close
        loop.client().click(menuId, "action:close");
        assertThat(loop.stepClientToServer()).isTrue();

        // Server emits Hide wire message
        assertThat(loop.stepServerToClient()).isTrue();
        assertThat(loop.client().isVisible(menuId)).isFalse();
        assertThat(session.hasActiveUiSession()).isFalse();
    }
}
