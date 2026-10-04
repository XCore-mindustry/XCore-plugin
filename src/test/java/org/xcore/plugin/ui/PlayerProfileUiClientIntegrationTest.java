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
import org.xcore.plugin.database.repository.GameDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.gamemode.hexed.HexedRanks;
import org.xcore.plugin.model.AggregatedPlayerStats;
import org.xcore.plugin.model.ModeStatsSummary;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.PlayerStatsOverview;
import org.xcore.plugin.player.Badge;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.service.PlayerProfileSettingsService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.menu.AuditHistoryMenu;
import org.xcore.plugin.ui.menu.PlayerMenu;
import org.xcore.testkit.ui.DeterministicUiLoop;
import org.xcore.testkit.ui.UiSnapshot;
import org.xcore.testkit.ui.UiWireMessage;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static mindustry.Vars.state;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * End-to-end integration test driving MenuService, UiSession, and PlayerProfileUiController
 * through DeterministicUiLoop and HeadlessMenuClient to verify responsive tab switching,
 * online player roster inspection, and zero-flicker client-server lifecycle.
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
        data.pid = 42;
        data.nickname = "TestUser";
        data.customNickname = "[#2CABFEFF]Tester";
        data.description = "Profile tester";
        data.hexedPoints = 12;
        data.hexedRank(HexedRanks.HexedRank.regular);
        data.unlockedBadges = Set.of(Badge.DEVELOPER.id());
        data.activeBadge = Badge.DEVELOPER.id();
        data.badgeSymbolColorMode = "default";
        data.totalPlayTime = 180;

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
        when(sessionService.streamCached()).thenAnswer(inv -> Stream.of(session));
        when(sessionService.getCachedCount()).thenReturn(1);

        loop.onClientMessage(msg -> {
            if (msg instanceof UiWireMessage.Choose choose) {
                var result = new MenuResult(choose.action());
                result.token = choose.token();
                menuService.onMenuBuilderResult(session, result);
            }
        });

        GameDataRepository gameDataRepo = mock(GameDataRepository.class);
        when(gameDataRepo.aggregatePlayerStatsOverview(anyString())).thenReturn(
                new PlayerStatsOverview(
                        new AggregatedPlayerStats(20, 15, 500, 100, 50, 20, 5),
                        ModeStatsSummary.EMPTY,
                        ModeStatsSummary.EMPTY,
                        ModeStatsSummary.EMPTY
                )
        );

        PlayerDataRepository playerDataRepo = mock(PlayerDataRepository.class);
        when(playerDataRepo.findTopRank(any(), any())).thenReturn(2);

        PlayerDisplayService displayService = mock(PlayerDisplayService.class);
        when(displayService.resolveBaseName(any(), any())).thenReturn("TestUser");

        playerMenu = new PlayerMenu(
                new TomlSecretsConfig(),
                sessionService,
                gameDataRepo,
                playerDataRepo,
                bundle,
                displayService,
                mock(PlayerProfileSettingsService.class),
                mock(AuditHistoryMenu.class),
                menuService,
                null,
                null
        );
    }

    @AfterEach
    void tearDown() {
        state = originalState;
        Core.app = null;
    }

    @Test
    @DisplayName("openProfileUi sends menuBuilder DSL to client and transitions tabs on click")
    void openProfileUi_sendsDsl_andSwitchesTabs() {
        playerMenu.openProfileUi(session, session.data);
        int menuId = menuService.getMenuBuilderId();

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

        assertThat(dsl).contains("action:tab:overview");
        assertThat(dsl).contains("action:tab:stats");
        assertThat(dsl).contains("action:tab:players");

        // Client clicks STATS tab
        loop.client().click(menuId, "action:tab:stats");
        assertThat(loop.stepClientToServer()).isTrue();
        assertThat(loop.stepServerToClient()).isTrue();

        var statsMsg = (UiWireMessage.Show) loop.transcript().all().stream()
                .filter(m -> m instanceof UiWireMessage.Show)
                .reduce((first, second) -> second)
                .orElseThrow();
        String statsDsl = UiDslWriter.write((NodeBuilder<?>) statsMsg.body().decode());
        assertThat(statsDsl).contains("action:tab:overview");

        // Client clicks PLAYERS tab
        loop.client().click(menuId, "action:tab:players");
        assertThat(loop.stepClientToServer()).isTrue();
        assertThat(loop.stepServerToClient()).isTrue();

        var playersMsg = (UiWireMessage.Show) loop.transcript().all().stream()
                .filter(m -> m instanceof UiWireMessage.Show)
                .reduce((first, second) -> second)
                .orElseThrow();
        String playersDsl = UiDslWriter.write((NodeBuilder<?>) playersMsg.body().decode());
        assertThat(playersDsl).contains("action:filter_cycle");
        assertThat(playersDsl).contains("action:refresh_players");

        // Client clicks CLOSE
        loop.client().click(menuId, "action:close");
        assertThat(loop.stepClientToServer()).isTrue();
        assertThat(loop.stepServerToClient()).isTrue();

        assertThat(loop.client().isVisible(menuId)).isFalse();
    }
}
