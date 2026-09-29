package org.xcore.plugin.ui.menu;

import com.ospx.flubundle.Bundle;
import com.ospx.flubundle.BundleContext;
import mindustry.gen.Player;
import mindustry.ui.builder.MenuResult;
import mindustry.ui.builder.UiDslWriter;
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
import org.xcore.ui.LocalizerResolver;
import org.xcore.ui.VNode;
import org.xcore.ui.VNodeCompiler;
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
        return createTestSession(uuid, null, null);
    }

    @SuppressWarnings("unchecked")
    private Session createTestSession(String uuid, Player player, BundleContext context) {
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
        if (context != null) {
            when(bundle.context(any(Player.class), any(java.util.function.Supplier.class))).thenReturn(context);
        }

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

    @Test
    @DisplayName("render compiles VNode tree with 740 width, maxHeight 520 pane, tabs, and buttonTable cards")
    void render_compilesVNodeTreeWithResponsiveCards() {
        ServerRegistryService registry = createRegistry();
        Session session = createTestSession("uuid-1");
        ServerSelectorUiController controller = new ServerSelectorUiController(registry, session);
        ServerSelectorUiController.ServerSelectorModel model = ServerSelectorUiController.createModel(registry, Category.ALL);

        VNode root = controller.render(model);
        VNodeCompiler compiler = new VNodeCompiler((key, args) -> session.locale().format(key, args));
        String dsl = UiDslWriter.write(compiler.compile(root));

        // Background and width
        assertThat(dsl).contains("background: pane");
        assertThat(dsl).contains("width: 740");

        // Header
        assertThat(dsl).contains("ИГРОВЫЕ СЕРВЕРЫ");
        assertThat(dsl).contains("action:close");
        assertThat(dsl).contains("size: 34");

        // Tabs
        assertThat(dsl).contains("action:tab:all");
        assertThat(dsl).contains("action:tab:pvp");
        assertThat(dsl).contains("action:tab:survival");
        assertThat(dsl).contains("action:tab:special");

        // Dynamic slot
        assertThat(dsl).contains("id: slot_servers");
        assertThat(dsl).contains("pane{");
        assertThat(dsl).contains("maxHeight: 520");

        // Card buttonTables
        assertThat(dsl).contains("buttonTable{");
        assertThat(dsl).contains("action:connect:mini-pvp");
        assertThat(dsl).contains("action:connect:mini-surv");
        assertThat(dsl).contains("ВЫ ЗДЕСЬ");

        // Footer
        assertThat(dsl).contains("action:refresh");
        assertThat(dsl).contains("Обновить");
    }

    @Test
    @DisplayName("render compiles responsive mobile layout when isMobile is true")
    void render_compilesMobileLayout() {
        ServerRegistryService registry = createRegistry();
        Session session = createTestSession("uuid-1");
        ServerSelectorUiController controller = new ServerSelectorUiController(registry, session);
        ServerSelectorUiController.ServerSelectorModel model = ServerSelectorUiController.createModel(registry, Category.ALL, true);

        VNode root = controller.render(model);
        VNodeCompiler compiler = new VNodeCompiler((key, args) -> session.locale().format(key, args));
        String dsl = UiDslWriter.write(compiler.compile(root));

        assertThat(dsl).contains("background: pane");
        assertThat(dsl).contains("width: 680");
        assertThat(dsl).contains("maxHeight: 600");
        assertThat(dsl).contains("size: 32");
        assertThat(dsl).contains("action:connect:mini-pvp");
    }

    @Test
    @DisplayName("render compiles centered info message when category is empty")
    void render_compilesEmptyCategoryMessage() {
        ServerRegistryService registry = createRegistry();
        Session session = createTestSession("uuid-1");
        ServerSelectorUiController controller = new ServerSelectorUiController(registry, session);
        ServerSelectorUiController.ServerSelectorModel model = new ServerSelectorUiController.ServerSelectorModel(
                "none", Category.ALL, List.of(), 0, 0, 0, false
        );

        VNode root = controller.render(model);
        VNodeCompiler compiler = new VNodeCompiler((key, args) -> session.locale().format(key, args));
        String dsl = UiDslWriter.write(compiler.compile(root));

        assertThat(dsl).contains("player-servers-empty-category");
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
    @DisplayName("connect to unknown server sends player-servers-not-found via locale.send")
    void connect_unknownServer_sendsNotFound() {
        ServerRegistryService registry = createRegistry();
        Player player = mock(Player.class);
        BundleContext context = mock(BundleContext.class);
        Session session = createTestSession("uuid-1", player, context);

        ServerSelectorUiController controller = new ServerSelectorUiController(registry, session);
        ServerSelectorUiController.ServerSelectorModel model = ServerSelectorUiController.createModel(registry, Category.ALL);
        ControllerContext ctx = mock(ControllerContext.class);

        controller.update(model, new ServerSelectorUiController.ServerSelectorEvent.Connect("unknown-server"), ctx);

        verify(context).send(eq("player-servers-not-found"), anyMap());
        verify(ctx).close();
    }

    @Test
    @DisplayName("connect to current server sends player-servers-already-connected via locale.send")
    void connect_currentServer_sendsAlreadyConnected() {
        ServerRegistryService registry = createRegistry();
        Player player = mock(Player.class);
        BundleContext context = mock(BundleContext.class);
        Session session = createTestSession("uuid-1", player, context);

        ServerSelectorUiController controller = new ServerSelectorUiController(registry, session);
        ServerSelectorUiController.ServerSelectorModel model = ServerSelectorUiController.createModel(registry, Category.ALL);
        ControllerContext ctx = mock(ControllerContext.class);

        controller.update(model, new ServerSelectorUiController.ServerSelectorEvent.Connect("mini-pvp"), ctx);

        verify(context).send(eq("player-servers-already-connected"), anyMap());
        verify(ctx).close();
    }
}
