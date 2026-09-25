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
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.AggregatedPlayerStats;
import org.xcore.plugin.model.ModeStatsSummary;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.PlayerStatsOverview;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.menu.PlayerMenu;
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
 * End-to-end integration test driving MenuService, UiSession, and PlayerProfileUiController
 * through DeterministicUiLoop and HeadlessMenuClient to verify mobile landscape scrolling,
 * responsive layout constraints, partial slot updates, and cancel/close handling.
 */
class PlayerProfileUiClientIntegrationTest {

    private GameState originalState;
    private DeterministicUiLoop loop;
    private MenuService menuService;
    private Session session;
    private SessionService sessionService;
    private PlayerMenu playerMenu;

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
        data.pid = 99;
        data.nickname = "ProfileUser";
        data.customNickname = "MasterBuilder";
        data.description = "Pro Mindustry player";
        data.pvpRating = 1650;
        data.hexedPoints = 35;
        data.totalPlayTime = 300;
        data.admin = false;
        data.discordUsername = "pro_builder";

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

        playerMenu = new PlayerMenu(
                new TomlSecretsConfig(),
                sessionService,
                null,
                null,
                bundle,
                mock(PlayerDisplayService.class),
                null,
                null,
                menuService,
                null
        );
    }

    @AfterEach
    void tearDown() {
        state = originalState;
        Core.app = null;
    }

    private PlayerStatsOverview createSampleStats() {
        return new PlayerStatsOverview(
                new AggregatedPlayerStats(200, 150, 10000, 2000, 500, 100, 250),
                new ModeStatsSummary(80, 60, 0, 0, 0, 0),
                new ModeStatsSummary(50, 40, 200, 120, 0, 0),
                new ModeStatsSummary(70, 50, 0, 0, 1, 45)
        );
    }

    @Test
    @DisplayName("Open player profile delivers responsive dialog with tabs, close button, and maxHeight 360")
    void openProfile_deliversResponsiveDialog() {
        PlayerStatsOverview stats = createSampleStats();
        playerMenu.openPlayerProfileUi(session, session.data, stats, 5);
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
        assertThat(dsl).contains("maxHeight: 360");
        assertThat(dsl).contains("action:close");
        assertThat(dsl).contains("action:tab:overview");
        assertThat(dsl).contains("action:tab:stats");
        assertThat(dsl).contains("action:tab:modes");
        assertThat(dsl).contains("#99");
        assertThat(dsl).contains("@pro_builder");
    }

    @Test
    @DisplayName("Selecting Stats tab triggers zero-flicker partial update targeting slot_content")
    void selectTab_triggersPartialUpdate() {
        PlayerStatsOverview stats = createSampleStats();
        playerMenu.openPlayerProfileUi(session, session.data, stats, 5);
        int menuId = menuService.getMenuBuilderId();
        assertThat(loop.stepServerToClient()).isTrue();

        // Client chooses Stats tab
        loop.client().click(menuId, "action:tab:stats");
        assertThat(loop.stepClientToServer()).isTrue();

        // Server processes event and emits Update wire message targeting slot_content
        assertThat(loop.stepServerToClient()).isTrue();

        var updateMsg = (UiWireMessage.Update) loop.transcript().all().stream()
                .filter(m -> m instanceof UiWireMessage.Update)
                .reduce((first, second) -> second)
                .orElseThrow();

        assertThat(updateMsg.targetId()).isEqualTo("slot_content");

        String updateDsl = UiDslWriter.write((NodeBuilder<?>) updateMsg.body().decode());
        assertThat(updateDsl).contains("Overall Statistics");
        assertThat(updateDsl).contains("PvP Rating:[] [gold]1650");
        assertThat(updateDsl).contains("Win Rate:[] [accent]75%");
    }

    @Test
    @DisplayName("Selecting Modes tab triggers partial update displaying PvP, Survival, and Hexed cards")
    void selectModesTab_triggersModesPartialUpdate() {
        PlayerStatsOverview stats = createSampleStats();
        playerMenu.openPlayerProfileUi(session, session.data, stats, 5);
        int menuId = menuService.getMenuBuilderId();
        assertThat(loop.stepServerToClient()).isTrue();

        // Client chooses Modes tab
        loop.client().click(menuId, "action:tab:modes");
        assertThat(loop.stepClientToServer()).isTrue();

        assertThat(loop.stepServerToClient()).isTrue();

        var updateMsg = (UiWireMessage.Update) loop.transcript().all().stream()
                .filter(m -> m instanceof UiWireMessage.Update)
                .reduce((first, second) -> second)
                .orElseThrow();

        assertThat(updateMsg.targetId()).isEqualTo("slot_content");

        String updateDsl = UiDslWriter.write((NodeBuilder<?>) updateMsg.body().decode());
        assertThat(updateDsl).contains("⚔ PvP");
        assertThat(updateDsl).contains("🛡 Survival");
        assertThat(updateDsl).contains("👑 Hexed");
        assertThat(updateDsl).contains("Best Wave:[] [white]200");
    }

    @Test
    @DisplayName("Close button dismisses profile dialog cleanly")
    void closeButton_dismissesDialog() {
        PlayerStatsOverview stats = createSampleStats();
        playerMenu.openPlayerProfileUi(session, session.data, stats, 5);
        int menuId = menuService.getMenuBuilderId();
        assertThat(loop.stepServerToClient()).isTrue();

        // Client presses close
        loop.client().click(menuId, "action:close");
        assertThat(loop.stepClientToServer()).isTrue();

        // Server emits Hide wire message
        assertThat(loop.stepServerToClient()).isTrue();
        assertThat(loop.client().isVisible(menuId)).isFalse();
        assertThat(session.hasActiveUiSession()).isFalse();
    }
}
