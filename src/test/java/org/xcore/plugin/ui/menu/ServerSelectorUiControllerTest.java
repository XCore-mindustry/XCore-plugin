package org.xcore.plugin.ui.menu;

import com.ospx.flubundle.Bundle;
import mindustry.gen.Player;
import mindustry.ui.builder.MenuResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.protocol.generated.messages.server.ServerMessages.ServerHeartbeatV1;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.ServerRegistryService;
import org.xcore.plugin.service.ServerRegistryService.Category;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.kit.LayoutAssert;
import org.xcore.plugin.ui.kit.Screen;
import org.xcore.ui.VNode;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.UpdateResult;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ServerSelectorUiControllerTest {

    @SuppressWarnings("unchecked")
    private Session createTestSession(String uuid) {
        return createTestSession(uuid, null);
    }

    @SuppressWarnings("unchecked")
    private Session createTestSession(String uuid, Player player) {
        PlayerData data = new PlayerData(uuid, true);
        data.nickname = "TestUser";

        Bundle bundle = mock(Bundle.class);
        com.ospx.flubundle.Localizer localizer = mock(com.ospx.flubundle.Localizer.class);
        when(localizer.locale()).thenReturn(Locale.ENGLISH);
        when(localizer.format(anyString())).thenAnswer(i -> {
            String key = i.getArgument(0);
            return switch (key) {
                case "player-servers-title" -> "[white] ИГРОВЫЕ СЕРВЕРЫ[] [gold]XCORE[]";
                case "player-servers-refresh" -> "⟳ Обновить";
                case "player-servers-badge-current" -> "[gold]● ВЫ ЗДЕСЬ[]";
                default -> key;
            };
        });
        when(localizer.format(anyString(), anyMap())).thenAnswer(i -> {
            String key = i.getArgument(0);
            return switch (key) {
                case "player-servers-title" -> "[white] ИГРОВЫЕ СЕРВЕРЫ[] [gold]XCORE[]";
                case "player-servers-refresh" -> "⟳ Обновить";
                case "player-servers-badge-current" -> "[gold]● ВЫ ЗДЕСЬ[]";
                default -> key;
            };
        });
        when(bundle.localizer(any(java.util.function.Supplier.class))).thenReturn(localizer);

        return new Session(
                new TomlSecretsConfig(),
                bundle,
                null,
                mock(PlayerDataRepository.class),
                player,
                data
        );
    }

    private ServerRegistryService createRegistry() {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";
        config.server.playerLimit = 25;
        NetworkService network = mock(NetworkService.class);
        var service = new ServerRegistryService(config, network);

        // Heartbeat for surv
        service.handleHeartbeat(new ServerHeartbeatV1(
                "mini-surv", 1L, 10, 20, "v160", "play.xcore.top", 7002, "Survival server", "Islands", 5, "survival", 60
        ));
        return service;
    }

    /** A network with a full server, a busy one with a long description, an idle one and the rest offline. */
    private ServerRegistryService crowdedRegistry() {
        ServerRegistryService service = createRegistry();
        service.handleHeartbeat(new ServerHeartbeatV1(
                "hexedcore", 2L, 16, 16, "v160", "play.xcore.top", 7005,
                "Захватывайте гексы, стройте базу и не дайте соседям вырасти раньше вас", "Hexed Arena", null, "hexed", 58
        ));
        service.handleHeartbeat(new ServerHeartbeatV1(
                "towerdefence", 3L, 7, 12, "v160", "play.xcore.top", 7009, "", "Supercalifragilistic_Crossroads_v12_final", 143, "td", 60
        ));
        service.handleHeartbeat(new ServerHeartbeatV1(
                "sandbox", 4L, 0, 20, "v160", "play.xcore.top", 7013, "", "-", null, "sandbox", 60
        ));
        return service;
    }

    @Test
    @DisplayName("the window is laid out for every screen in every language")
    void window_isLaidOutForEveryScreen() {
        ServerRegistryService registry = crowdedRegistry();
        Session session = createTestSession("uuid-1");
        ServerSelectorUiController controller = new ServerSelectorUiController(registry, session);

        for (String language : LayoutAssert.LANGUAGES) {
            session.localization = LayoutAssert.localization(language);
            for (Category category : Category.values()) {
                var model = ServerSelectorUiController.createModel(registry, category);
                for (Screen screen : Screen.ALL) {
                    VNode window = controller.window(model, screen);
                    LayoutAssert.assertLaidOut(window, screen);
                    assertThat(LayoutAssert.allText(window)).doesNotContain("player-servers-");
                    assertThat(LayoutAssert.actions(window)).contains(
                            "action:tab:all", "action:tab:pvp", "action:tab:survival", "action:tab:special",
                            "action:refresh");
                }
                LayoutAssert.assertFitsPacket(controller.render(model), language + " " + category);
            }
        }
    }

    @Test
    @DisplayName("a server is a row that connects to it; the player's own is marked")
    void window_showsServersAsRows() {
        ServerRegistryService registry = crowdedRegistry();
        Session session = createTestSession("uuid-1");
        session.localization = LayoutAssert.localization("ru");
        ServerSelectorUiController controller = new ServerSelectorUiController(registry, session);
        var model = ServerSelectorUiController.createModel(registry, Category.ALL);

        for (Screen screen : Screen.ALL) {
            VNode window = controller.window(model, screen);
            String text = LayoutAssert.allText(window);

            assertThat(LayoutAssert.actions(window)).contains("action:connect:mini-pvp", "action:connect:mini-surv");
            assertThat(LayoutAssert.actions(window)).doesNotContain("action:close");
            assertThat(text).contains("ИГРОВЫЕ СЕРВЕРЫ", "ВЫ ЗДЕСЬ", "МЕСТ НЕТ", "Обновить");

            String dsl = LayoutAssert.dsl(window);
            assertThat(dsl).contains("background: pane", "pane{", "buttonTable{");
            assertThat(dsl).doesNotContain("maxWidth", "condition: portrait");
        }
        // A description or a map name longer than the row is cut, and its colour is closed at the cut.
        assertThat(LayoutAssert.allText(controller.window(model, Screen.SMALL))).contains("…[]");
    }

    @Test
    @DisplayName("every screen class gets its own copy, chosen by the client")
    void render_sendsOneWindowPerScreen() {
        ServerRegistryService registry = createRegistry();
        Session session = createTestSession("uuid-1");
        ServerSelectorUiController controller = new ServerSelectorUiController(registry, session);
        var model = ServerSelectorUiController.createModel(registry, Category.ALL);

        String dsl = LayoutAssert.dsl(controller.render(model));

        assertThat(dsl).contains("condition: \"width >= 800\"", "condition: \"width < 800\"", "condition: \"width < 490\"");
    }

    @Test
    @DisplayName("an empty category says so")
    void window_showsEmptyCategory() {
        ServerRegistryService registry = createRegistry();
        Session session = createTestSession("uuid-1");
        ServerSelectorUiController controller = new ServerSelectorUiController(registry, session);
        var model = new ServerSelectorUiController.ServerSelectorModel("none", Category.ALL, List.of(), 0, 0, 0);

        for (Screen screen : Screen.ALL) {
            VNode window = controller.window(model, screen);
            assertThat(LayoutAssert.allText(window)).contains("player-servers-empty-category");
            assertThat(LayoutAssert.actions(window)).noneMatch(action -> action.startsWith("action:connect:"));
        }
    }

    @Test
    @DisplayName("SelectCategory rerenders with filtered list")
    void selectCategory_rerenders() {
        ServerRegistryService registry = createRegistry();
        Session session = createTestSession("uuid-1");
        ServerSelectorUiController controller = new ServerSelectorUiController(registry, session);
        ServerSelectorUiController.ServerSelectorModel model = ServerSelectorUiController.createModel(registry, Category.ALL);

        ControllerContext ctx = mock(ControllerContext.class);
        UpdateResult<ServerSelectorUiController.ServerSelectorModel> result = controller.update(
                model, new ServerSelectorUiController.ServerSelectorEvent.SelectCategory(Category.PVP), ctx
        );

        assertThat(result.fullRerender()).isTrue();
        assertThat(result.model().selectedCategory()).isEqualTo(Category.PVP);
        assertThat(result.model().filteredServers()).allMatch(s -> s.template().category() == Category.PVP);
    }

    @Test
    @DisplayName("Refresh rerenders with fresh snapshot")
    void refresh_rerenders() {
        ServerRegistryService registry = createRegistry();
        Session session = createTestSession("uuid-1");
        ServerSelectorUiController controller = new ServerSelectorUiController(registry, session);
        ServerSelectorUiController.ServerSelectorModel model = ServerSelectorUiController.createModel(registry, Category.ALL);

        ControllerContext ctx = mock(ControllerContext.class);
        UpdateResult<ServerSelectorUiController.ServerSelectorModel> result = controller.update(
                model, new ServerSelectorUiController.ServerSelectorEvent.Refresh(), ctx
        );

        assertThat(result.fullRerender()).isTrue();
    }

    @Test
    @DisplayName("Close closes controller context")
    void close_closesContext() {
        ServerRegistryService registry = createRegistry();
        Session session = createTestSession("uuid-1");
        ServerSelectorUiController controller = new ServerSelectorUiController(registry, session);
        ServerSelectorUiController.ServerSelectorModel model = ServerSelectorUiController.createModel(registry, Category.ALL);

        AtomicBoolean closed = new AtomicBoolean(false);
        ControllerContext ctx = new ControllerContext() {
            @Override public String playerId() { return "uuid-1"; }
            @Override public void close() { closed.set(true); }
        };

        UpdateResult<ServerSelectorUiController.ServerSelectorModel> result = controller.update(
                model, new ServerSelectorUiController.ServerSelectorEvent.Close(), ctx
        );

        assertThat(closed.get()).isTrue();
        assertThat(result.close()).isTrue();
    }

    @Test
    @DisplayName("parseEvent correctly routes actions and cancellations")
    void parseEvent_routesActions() {
        ServerRegistryService registry = createRegistry();
        ServerSelectorUiController controller = new ServerSelectorUiController(registry, null);

        var closeRes = new MenuResult("action:close");
        assertThat(controller.parseEvent(closeRes)).isInstanceOf(ServerSelectorUiController.ServerSelectorEvent.Close.class);

        var refreshRes = new MenuResult("action:refresh");
        assertThat(controller.parseEvent(refreshRes)).isInstanceOf(ServerSelectorUiController.ServerSelectorEvent.Refresh.class);

        var tabPvP = new MenuResult("action:tab:pvp");
        assertThat(controller.parseEvent(tabPvP)).isEqualTo(new ServerSelectorUiController.ServerSelectorEvent.SelectCategory(Category.PVP));

        var connectRes = new MenuResult("action:connect:mini-surv");
        assertThat(controller.parseEvent(connectRes)).isEqualTo(new ServerSelectorUiController.ServerSelectorEvent.Connect("mini-surv"));
    }

    @Test
    @DisplayName("connect to unknown server sends player-servers-not-found via the player's chat")
    void connect_unknownServer_sendsNotFound() {
        ServerRegistryService registry = createRegistry();
        Player player = mock(Player.class);
        Session session = createTestSession("uuid-1", player);

        ServerSelectorUiController controller = new ServerSelectorUiController(registry, session);
        ServerSelectorUiController.ServerSelectorModel model = ServerSelectorUiController.createModel(registry, Category.ALL);
        ControllerContext ctx = mock(ControllerContext.class);

        var result = controller.update(model, new ServerSelectorUiController.ServerSelectorEvent.Connect("unknown-server"), ctx);

        verify(player).sendMessage("player-servers-not-found");
        verify(ctx, never()).close();
        assertThat(result.close()).isFalse();
        assertThat(result.fullRerender()).isTrue();
    }

    @Test
    @DisplayName("connect to current server sends player-servers-already-connected via the player's chat")
    void connect_currentServer_sendsAlreadyConnected() {
        ServerRegistryService registry = createRegistry();
        Player player = mock(Player.class);
        Session session = createTestSession("uuid-1", player);

        ServerSelectorUiController controller = new ServerSelectorUiController(registry, session);
        ServerSelectorUiController.ServerSelectorModel model = ServerSelectorUiController.createModel(registry, Category.ALL);
        ControllerContext ctx = mock(ControllerContext.class);

        var result = controller.update(model, new ServerSelectorUiController.ServerSelectorEvent.Connect("mini-pvp"), ctx);

        verify(player).sendMessage("player-servers-already-connected");
        verify(ctx, never()).close();
        assertThat(result.close()).isFalse();
        assertThat(result.fullRerender()).isTrue();
    }

    @Test
    void unavailableOrFullServer_keepsBrowserOpenWithFreshSnapshot() {
        ServerRegistryService registry = spy(crowdedRegistry());
        var template = registry.findServer("hexedcore").orElseThrow().template();
        var offline = new ServerRegistryService.ServerStatus(template, 0, 16, false, false,
                "", "-", null, "", 0, 0, "localhost", 0);
        doReturn(java.util.Optional.of(offline)).when(registry).findServer("siege");
        Player player = mock(Player.class);
        Session session = createTestSession("uuid-1", player);
        var controller = new ServerSelectorUiController(registry, session);
        var oldModel = ServerSelectorUiController.createModel(registry, Category.PVP);
        for (String target : List.of("hexedcore", "siege")) {
            ControllerContext ctx = mock(ControllerContext.class);
            var result = controller.update(oldModel, new ServerSelectorUiController.ServerSelectorEvent.Connect(target), ctx);
            assertThat(result.close()).isFalse();
            assertThat(result.fullRerender()).isTrue();
            assertThat(result.model().selectedCategory()).isEqualTo(Category.PVP);
            assertThat(result.model().feedback()).isNotBlank();
            verify(ctx, never()).close();
        }
        verify(player).sendMessage("player-servers-full");
        verify(player).sendMessage("player-servers-offline");
    }

    @Test
    void successfulTransfer_closesBrowserAndSendsConnect() {
        var registry = createRegistry();
        Player player = mock(Player.class);
        player.con = mock(mindustry.net.NetConnection.class);
        Session session = createTestSession("uuid-1", player);
        var controller = new ServerSelectorUiController(registry, session);
        ControllerContext ctx = mock(ControllerContext.class);
        try (var call = mockStatic(mindustry.gen.Call.class)) {
            var result = controller.update(ServerSelectorUiController.createModel(registry, Category.ALL),
                    new ServerSelectorUiController.ServerSelectorEvent.Connect("mini-surv"), ctx);
            assertThat(result.close()).isTrue();
            verify(ctx).close();
            call.verify(() -> mindustry.gen.Call.connect(eq(player.con), anyString(), anyInt()));
        }
    }
}
