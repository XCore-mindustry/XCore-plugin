package org.xcore.plugin.ui;

import arc.Core;
import arc.mock.MockApplication;
import com.ospx.flubundle.Bundle;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import mindustry.ui.builder.MenuResult;
import mindustry.ui.builder.UiBuilder.NodeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.testkit.ui.DeterministicUiLoop;
import org.xcore.testkit.ui.UiSnapshot;
import org.xcore.testkit.ui.UiWireMessage;
import org.xcore.ui.Text;
import org.xcore.ui.Ui;
import org.xcore.ui.VNode;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.SlotKey;
import org.xcore.ui.runtime.UiController;
import org.xcore.ui.runtime.UpdateResult;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The client reports a menu builder dialog closed only until it is pressed: the press sets the
 * dialog's {@code wasHidden}, and its hidden listener then sends nothing. A UI session patches
 * that same dialog in place, so after the first press its close goes unheard.
 */
@SuppressWarnings("unchecked")
class UiSessionCloseTrackingTest {

    private DeterministicUiLoop loop;
    private MenuService menuService;
    private Session session;
    private long now;

    @BeforeEach
    void setUp() {
        loop = new DeterministicUiLoop();
        Core.app = new MockApplication() {
            @Override
            public void post(Runnable runnable) {
                loop.serverPost().post(runnable);
            }
        };

        Bundle bundle = mock(Bundle.class);
        com.ospx.flubundle.Localizer localizer = mock(com.ospx.flubundle.Localizer.class);
        when(localizer.locale()).thenReturn(Locale.ENGLISH);
        when(localizer.format(anyString())).thenAnswer(i -> i.getArgument(0));
        when(localizer.format(anyString(), anyMap())).thenAnswer(i -> i.getArgument(0));
        when(bundle.localizer(any(java.util.function.Supplier.class))).thenReturn(localizer);

        SessionService sessionService = mock(SessionService.class);
        menuService = new MenuService(() -> sessionService, new WireGateway());
        menuService.init();

        Player player = Player.create();
        player.con = mock(NetConnection.class);
        session = new Session(new TomlSecretsConfig(), bundle, menuService, mock(PlayerDataRepository.class),
                player, new PlayerData("close-tracking-uuid", true));
        session.localization = null;
        now = 1_000_000L;
        session.useClock(() -> now);
        when(sessionService.get(anyString())).thenReturn(session);

        loop.onClientMessage(msg -> {
            if (msg instanceof UiWireMessage.Choose choose) {
                var result = new MenuResult(choose.action());
                result.token = choose.token();
                menuService.onMenuBuilderResult(session, result);
            }
        });
    }

    private int open() {
        menuService.openUi(session, new TabsController(), "a", true);
        assertThat(loop.stepServerToClient()).isTrue();
        return menuService.getMenuBuilderId();
    }

    private void press(int menuId, String action) {
        loop.client().click(menuId, action);
        assertThat(loop.stepClientToServer()).isTrue();
        while (loop.stepServerToClient()) {
            // deliver the patch or re-render the press caused
        }
    }

    @Test
    @DisplayName("a dialog closed before any press reports it, and the menu ends at once")
    void closeWithoutPress_isReported() {
        int menuId = open();
        assertThat(session.hasActiveMenu()).isTrue();

        loop.client().dismiss(menuId);
        assertThat(loop.stepClientToServer()).isTrue();

        assertThat(session.hasActiveUiSession()).isFalse();
        assertThat(session.hasActiveMenu()).isFalse();
    }

    @Test
    @DisplayName("a dialog closed after a patching press reports nothing to the server")
    void closeAfterPatchingPress_isNotReported() {
        int menuId = open();
        press(menuId, "tab:b");
        assertThat(loop.transcript().all()).anyMatch(m -> m instanceof UiWireMessage.Update);

        loop.client().dismiss(menuId);

        assertThat(loop.client().isVisible(menuId)).isFalse();
        assertThat(loop.stepClientToServer()).as("the stock client sends no cancel").isFalse();
        assertThat(session.hasActiveUiSession()).as("the server cannot know the dialog is gone").isTrue();
    }

    @Test
    @DisplayName("after a patching press the menu counts as open only while the player keeps pressing")
    void afterPatchingPress_menuExpiresWithoutActivity() {
        int menuId = open();
        press(menuId, "tab:b");
        loop.client().dismiss(menuId);

        now += Session.UNREPORTED_MENU_TIMEOUT_MILLIS - 1;
        assertThat(session.hasActiveMenu()).isTrue();

        now += 1;
        assertThat(session.hasActiveMenu()).isFalse();
        assertThat(menuService.isMenuOpen(session)).isFalse();
    }

    @Test
    @DisplayName("every press restarts the timeout of a dialog that can no longer report closing")
    void eachPress_extendsTheTimeout() {
        int menuId = open();
        press(menuId, "tab:b");

        now += Session.UNREPORTED_MENU_TIMEOUT_MILLIS - 1;
        press(menuId, "tab:a");

        now += Session.UNREPORTED_MENU_TIMEOUT_MILLIS - 1;
        assertThat(session.hasActiveMenu()).isTrue();

        now += 1;
        assertThat(session.hasActiveMenu()).isFalse();
    }

    @Test
    @DisplayName("a dialog that is never pressed counts as open for as long as it stays up")
    void unpressedDialog_neverExpires() {
        open();

        now += 10 * Session.UNREPORTED_MENU_TIMEOUT_MILLIS;

        assertThat(session.hasActiveMenu()).isTrue();
    }

    @Test
    @DisplayName("a press that re-sends the dialog arms it again, so its close is reported")
    void rerenderingPress_rearmsTheDialog() {
        int menuId = open();
        press(menuId, "tab:b");
        press(menuId, "action:reload");
        assertThat(loop.transcript().all().stream().filter(m -> m instanceof UiWireMessage.Show)).hasSize(2);

        now += 10 * Session.UNREPORTED_MENU_TIMEOUT_MILLIS;
        assertThat(session.hasActiveMenu()).isTrue();

        loop.client().dismiss(menuId);
        assertThat(loop.stepClientToServer()).isTrue();
        assertThat(session.hasActiveUiSession()).isFalse();
        assertThat(session.hasActiveMenu()).isFalse();
    }

    @Test
    @DisplayName("a dialog that outlived the timeout still answers presses, and is open again after one")
    void expiredDialog_stillHandlesPresses() {
        int menuId = open();
        press(menuId, "tab:b");
        now += Session.UNREPORTED_MENU_TIMEOUT_MILLIS;
        assertThat(session.hasActiveMenu()).isFalse();

        int updatesBefore = (int) loop.transcript().all().stream().filter(m -> m instanceof UiWireMessage.Update).count();
        press(menuId, "tab:a");

        assertThat(loop.transcript().all().stream().filter(m -> m instanceof UiWireMessage.Update))
                .hasSize(updatesBefore + 1);
        assertThat(session.hasActiveMenu()).isTrue();
    }

    @Test
    @DisplayName("a stale press from a replaced window does not disarm the current one")
    void stalePress_doesNotDisarmTheCurrentWindow() {
        open();
        var result = new MenuResult("tab:b");
        result.token = session.activeUiSession().token() - 1;
        menuService.onMenuBuilderResult(session, result);

        now += 10 * Session.UNREPORTED_MENU_TIMEOUT_MILLIS;

        assertThat(session.hasActiveMenu()).isTrue();
    }

    @Test
    @DisplayName("a reported close ends the session even when the controller ignores it")
    void reportedClose_endsSessionTheControllerIgnores() {
        menuService.openUi(session, new TabsController(true), "a", true);
        assertThat(loop.stepServerToClient()).isTrue();
        int menuId = menuService.getMenuBuilderId();

        loop.client().dismiss(menuId);
        assertThat(loop.stepClientToServer()).isTrue();

        assertThat(session.hasActiveUiSession()).isFalse();
        assertThat(session.hasActiveMenu()).isFalse();
    }

    @Test
    @DisplayName("closing a menu from the server ends it whatever was pressed")
    void serverClose_endsTheMenu() {
        int menuId = open();
        press(menuId, "tab:b");
        press(menuId, "action:close");

        assertThat(session.hasActiveUiSession()).isFalse();
        assertThat(session.hasActiveMenu()).isFalse();
    }

    /** Two tabs swapped through a slot patch, a full re-render, and a close button. */
    private static final class TabsController implements UiController<String, String> {
        private static final SlotKey<Object> CONTENT = SlotKey.of("content");
        private final boolean ignoreCancel;

        TabsController() {
            this(false);
        }

        TabsController(boolean ignoreCancel) {
            this.ignoreCancel = ignoreCancel;
        }

        @Override
        public String initialModel(Object context) {
            return "a";
        }

        @Override
        public UpdateResult<String> update(String model, String event, ControllerContext ctx) {
            return switch (event) {
                case "close" -> UpdateResult.close(model);
                case "reload" -> UpdateResult.rerender(model);
                default -> UpdateResult.patch(event, CONTENT);
            };
        }

        @Override
        public VNode render(String model) {
            return Ui.table(t -> {
                t.button(Text.raw("A"), "tab:a", null);
                t.button(Text.raw("B"), "tab:b", null);
                t.row();
                t.slot("content", slot -> slot.label(Text.raw("tab " + model)));
                t.row();
                t.button(Text.raw("Reload"), "action:reload", null);
                t.button(Text.raw("Close"), "action:close", null);
            });
        }

        @Override
        public String parseEvent(MenuResult result) {
            if (result.wasCancelled()) return ignoreCancel ? null : "close";
            if (result.result.startsWith("tab:")) return result.result.substring(4);
            if ("action:reload".equals(result.result)) return "reload";
            if ("action:close".equals(result.result)) return "close";
            return null;
        }
    }

    private final class WireGateway implements MindustryMenuGateway {
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
    }
}
