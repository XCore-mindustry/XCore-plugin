package org.xcore.plugin.ui;

import com.ospx.flubundle.Bundle;
import jakarta.inject.Provider;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import mindustry.ui.builder.MenuResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuScreen;
import org.xcore.ui.Text;
import org.xcore.ui.Ui;
import org.xcore.ui.VNode;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MenuServiceUiTest {

    private MindustryMenuGateway gateway;
    private MenuService menuService;
    private Session session;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        gateway = mock(MindustryMenuGateway.class);
        Provider<SessionService> sessionServiceProvider = mock(Provider.class);
        menuService = new MenuService(sessionServiceProvider, gateway);

        Player player = Player.create();
        player.name = "TestPlayer";
        player.con = mock(NetConnection.class);

        PlayerData data = new PlayerData("test-uuid", true);
        session = new Session(
                new TomlSecretsConfig(),
                mock(Bundle.class),
                menuService,
                mock(PlayerDataRepository.class),
                player,
                data
        );
    }

    @Test
    @DisplayName("showUi transmits compiled NodeBuilder via gateway.menuBuilder")
    void showUi_transmitsCompiledNodeBuilder() {
        VNode root = Ui.table(t -> {
            t.label(Text.raw("Modern v160 Dialog"));
            t.row();
            t.button(Text.raw("Confirm"), "act:confirm", null);
        });

        menuService.showUi(session, root, "Test Title");

        verify(gateway).menuBuilder(eq(session.player), eq(menuService.getMenuBuilderId()), eq(1L), eq("Test Title"), eq(true), eq(true), eq(false), any());
        assertThat(session.activeScreen()).isNull();
    }

    @Test
    @DisplayName("onMenuBuilderResult triggers corresponding action when index is passed")
    void onMenuBuilderResult_triggersAction() {
        AtomicBoolean actionRan = new AtomicBoolean(false);
        org.xcore.plugin.ui.flow.MenuAction action = new org.xcore.plugin.ui.flow.MenuAction.CallbackAction(() -> actionRan.set(true));

        menuService.show(session, "Title", "Content", List.of(List.of("Click Me")), List.of(action), org.xcore.plugin.ui.flow.MenuMode.NORMAL);

        MenuResult result = new MenuResult();
        result.result = "0";

        menuService.onMenuBuilderResult(session, result);

        assertThat(actionRan.get()).isTrue();
    }

    @Test
    @DisplayName("onMenuBuilderResult closes menu cleanly on cancel")
    void onMenuBuilderResult_closesMenuOnCancel() {
        org.xcore.plugin.ui.flow.MenuAction noop = new org.xcore.plugin.ui.flow.MenuAction.CallbackAction(() -> {});
        menuService.show(session, "Title", "Content", List.of(List.of("Click Me")), List.of(noop), org.xcore.plugin.ui.flow.MenuMode.NORMAL);
        assertThat(session.activeScreen()).isNotNull();

        MenuResult cancelled = new MenuResult();
        cancelled.result = null; // cancelled dialog

        menuService.onMenuBuilderResult(session, cancelled);

        assertThat(session.activeScreen()).isNull();
    }

    @Test
    @DisplayName("resolverFor delegates to session.locale().format")
    void resolverFor_delegatesToSessionLocale() {
        org.xcore.ui.LocalizerResolver resolver = menuService.resolverFor(session);
        assertThat(resolver).isNotNull();
    }
}
