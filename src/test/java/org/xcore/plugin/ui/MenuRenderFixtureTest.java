package org.xcore.plugin.ui;

import arc.Application;
import arc.Core;
import arc.mock.MockApplication;
import com.ospx.flubundle.Bundle;
import mindustry.core.GameState;
import mindustry.gen.MenuBuilderCallPacket;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.InlineStorageExecutor;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.integration.top.LeaderboardEntry;
import org.xcore.plugin.integration.top.LeaderboardPage;
import org.xcore.plugin.integration.top.LeaderboardPageRequest;
import org.xcore.plugin.integration.top.TopCategoryProvider;
import org.xcore.plugin.integration.top.TopCategoryRegistry;
import org.xcore.plugin.integration.top.TopScope;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.service.PlayerProfileSettingsService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.kit.LayoutAssert;
import org.xcore.plugin.ui.menu.PlayerMenu;
import org.xcore.plugin.ui.menu.PlayerSettingsUiController;
import org.xcore.plugin.ui.menu.TopMenu;
import org.xcore.plugin.ui.menu.TopUiController;
import org.xcore.testkit.ui.MenuFixtures;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static mindustry.Vars.state;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Opt-in fixtures for the real-client renderer of mindustry-testkit ({@code tools/ui-render/render}):
 * the production gateway boundary supplies both the tree and its envelope.
 */
@Tag("ui-render")
class MenuRenderFixtureTest {
    private final Path output = Path.of(System.getProperty("xcore.uiRenderOutput", "build/ui-render/packets"));
    private Application previousApp;
    private GameState previousState;
    private MenuService menus;
    private Session session;
    private SessionService sessions;
    private String fixture;

    @BeforeEach
    void setUp() {
        previousApp = Core.app;
        previousState = state;
        Core.app = new MockApplication();
        state = new GameState();
        sessions = mock(SessionService.class);
        MindustryMenuGateway gateway = mock(MindustryMenuGateway.class);
        doAnswer(call -> {
            MenuBuilderCallPacket packet = new MenuBuilderCallPacket();
            packet.id = call.getArgument(1);
            packet.token = call.getArgument(2);
            packet.title = call.getArgument(3);
            packet.hideOnClick = call.getArgument(4);
            packet.hidePrevious = call.getArgument(5);
            packet.fillScreen = call.getArgument(6);
            packet.builder = call.getArgument(7);
            writePacket(fixture, packet);
            return null;
        }).when(gateway).menuBuilder(any(), anyInt(), anyLong(), nullable(String.class),
                anyBoolean(), anyBoolean(), anyBoolean(), any());
        menus = new MenuService(() -> sessions, gateway);
        menus.init();

        PlayerData data = new PlayerData("viewer-uuid", true);
        data.pid = 1;
        data.nickname = "W".repeat(40);
        data.customNickname = "[#ff8800]" + "W".repeat(40);
        data.description = "A long profile description. ".repeat(8).substring(0, 200);
        data.globalChatVisible = true;
        data.discordRelayVisible = false;
        data.leaderboard = true;
        Player player = Player.create();
        player.con = mock(NetConnection.class);
        session = new Session(new TomlSecretsConfig(), Bundle.INSTANCE, menus,
                mock(PlayerDataRepository.class), player, data);
        when(sessions.get(anyString())).thenReturn(session);
    }

    @AfterEach
    void tearDown() {
        Core.app = previousApp;
        state = previousState;
    }

    @ParameterizedTest
    @FieldSource("org.xcore.plugin.ui.kit.LayoutAssert#LANGUAGES")
    void exportLocalizedMenus(String language) throws IOException {
        session.localization = LayoutAssert.localization(language);
        PlayerMenu players = new PlayerMenu(new TomlSecretsConfig(), sessions, null, null,
                Bundle.INSTANCE, mock(PlayerDisplayService.class), mock(PlayerProfileSettingsService.class),
                null, menus, null, null);
        for (var tab : PlayerSettingsUiController.Tab.values()) {
            fixture = "settings-" + language + "-" + tab.name().toLowerCase(Locale.ROOT);
            players.openSettingsUi(session, session.data, tab);
            assertThat(output.resolve(fixture + ".packet")).isNotEmptyFile();
        }

        List<LeaderboardEntry> entries = new ArrayList<>();
        for (int i = 0; i < TopUiController.PLAYERS_PER_PAGE; i++) {
            entries.add(new LeaderboardEntry(i == 3 ? "viewer-uuid" : "uuid-" + i, 1238 + i,
                    "Player" + i, "2147483", Map.of("customNickname", "[#ff8800]" + "W".repeat(40),
                    "activeBadge", "developer", "admin", "true", "leagueName", "DIAMOND",
                    "rankName", "veteran"), ""));
        }
        TopCategoryRegistry registry = new TopCategoryRegistry();
        for (String category : List.of("MINI_PVP", "HEXED", "PLAYTIME", "HEXED_LEGACY")) {
            registry.register(new TopCategoryProvider() {
                public String id() { return category; }
                public String displayName(org.xcore.plugin.localization.Localization local) {
                    return switch (category) {
                        case "MINI_PVP" -> local.t("top-menu-category-mini-pvp");
                        case "PLAYTIME" -> local.t("top-menu-category-playtime");
                        case "HEXED" -> local.t("top-menu-category-hexed");
                        default -> "Legacy Hexed";
                    };
                }
                public List<TopScope> scopes() {
                    return List.of(new TopScope("3", true, Map.of()), new TopScope("2", false, Map.of()));
                }
                public LeaderboardPage loadPage(LeaderboardPageRequest request) {
                    return new LeaderboardPage(124, entries, true, "next", 123_456L, 1241);
                }
            });
        }
        var controller = new TopUiController(mock(TopMenu.class), registry, players, sessions,
                new Async(InlineStorageExecutor.create(), Runnable::run), session);
        fixture = "top-" + language + "-crowded";
        menus.openUi(session, controller, controller.createInitialModel("MINI_PVP", null, 124,
                "cursor", new ArrayDeque<>(List.of("previous"))), true);
        assertThat(output.resolve(fixture + ".packet")).isNotEmptyFile();
    }

    private void writePacket(String name, MenuBuilderCallPacket packet) throws IOException {
        MenuFixtures.write(output, name, packet);
    }
}
