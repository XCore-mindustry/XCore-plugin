package org.xcore.plugin.ui;

import com.ospx.flubundle.Bundle;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import mindustry.ui.builder.MenuResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuFlow;
import org.xcore.plugin.ui.flow.MenuMode;
import org.xcore.plugin.ui.flow.MenuPrompt;
import org.xcore.plugin.ui.flow.MenuRenderContext;
import org.xcore.plugin.ui.flow.MenuScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** The screens of flows, once the server can have the client build a dialog for them. */
class MenuServiceBuiltScreenTest {

    private MindustryMenuGateway gateway;
    private MenuService menuService;
    private Session session;

    @BeforeEach
    void setUp() {
        gateway = mock(MindustryMenuGateway.class);
        menuService = new MenuService(null, gateway);
        menuService.init();
        session = session();
    }

    @Test
    @DisplayName("a screen of a flow is built by the client, to fill the screen and stay on a press")
    void renderFlow_showsABuiltDialog() {
        menuService.renderFlow(session, flow(MenuMode.NORMAL, (context, action) -> {
        }), "state", null);

        assertThat(session.activeScreen().isBuilt()).isTrue();
        assertThat(session.activeScreen().token()).isNegative();
        verify(gateway).menuBuilder(eq(session.player), eq(menuService.getMenuBuilderId()),
                eq(session.activeScreen().token()), isNull(), eq(false), eq(true), eq(true), any());
        verify(gateway, never()).menu(any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("a screen too large for a packet is left to the client's own dialog")
    void renderFlow_fallsBackWhenTooLarge() {
        String text = "Очень длинный текст. ".repeat(1200);
        MenuFlow<String> flow = new TestFlow(MenuScreen.normal("Title", text, List.of(List.of(MenuButton.of("OK", "ok")))),
                (context, action) -> {
                });

        menuService.renderFlow(session, flow, "state", null);

        assertThat(session.activeScreen().isBuilt()).isFalse();
        verify(gateway).menu(eq(session.player), eq(menuService.getMenuId()), eq("Title"), eq(text), any());
        verify(gateway, never()).menuBuilder(any(), anyInt(), anyLong(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    @DisplayName("a screen left to the client's own dialog takes the dialog of a UI session away")
    void renderFlow_fallbackEndsAUiSession() {
        session.setActiveUiSession(mock(org.xcore.ui.runtime.UiSession.class));
        String text = "Очень длинный текст. ".repeat(1200);
        MenuFlow<String> flow = new TestFlow(MenuScreen.normal("Title", text, List.of(List.of(MenuButton.of("OK", "ok")))),
                (context, action) -> {
                });

        menuService.renderFlow(session, flow, "state", null);

        assertThat(session.hasActiveUiSession()).isFalse();
        verify(gateway).hideMenuBuilder(session.player, menuService.getMenuBuilderId());
        verify(gateway).menu(eq(session.player), eq(menuService.getMenuId()), eq("Title"), eq(text), any());
    }

    @Test
    @DisplayName("a press runs the action of the button; a screen that shows nothing after it is taken away")
    void press_hidesAScreenThatIsDone() {
        List<String> pressed = new ArrayList<>();
        menuService.renderFlow(session, flow(MenuMode.NORMAL, (context, action) -> pressed.add(action)), "state", null);

        menuService.onMenuBuilderResult(session, press("1"));

        assertThat(pressed).containsExactly("second");
        assertThat(session.activeScreen()).isNull();
        verify(gateway).hideMenuBuilder(session.player, menuService.getMenuBuilderId());
    }

    @Test
    @DisplayName("a press that shows the next screen replaces the dialog without hiding it")
    void press_replacesTheDialogWithTheNextScreen() {
        menuService.renderFlow(session, flow(MenuMode.NORMAL, (context, action) -> context.render()), "state", null);
        long first = session.activeScreen().token();

        menuService.onMenuBuilderResult(session, press("0"));

        assertThat(session.activeScreen().isBuilt()).isTrue();
        assertThat(session.activeScreen().token()).isNotEqualTo(first);
        verify(gateway, times(2)).menuBuilder(any(), anyInt(), anyLong(), any(), anyBoolean(), eq(true), anyBoolean(), any());
        verify(gateway, never()).hideMenuBuilder(any(), anyInt());
    }

    @Test
    @DisplayName("what a replaced dialog reports is not taken for the one that replaced it")
    void staleResults_areIgnored() {
        List<String> pressed = new ArrayList<>();
        List<String> closed = new ArrayList<>();
        var flow = new TestFlow(screen(MenuMode.NORMAL), (context, action) -> {
            pressed.add(action);
            context.render();
        }) {
            @Override
            public void onClose(MenuRenderContext<String> context) {
                closed.add("closed");
            }
        };
        menuService.renderFlow(session, flow, "state", null);
        long first = session.activeScreen().token();
        menuService.onMenuBuilderResult(session, press("0"));

        // The client hides the first dialog as the second arrives, and may have sent a press before it did.
        MenuResult cancel = new MenuResult();
        cancel.token = first;
        menuService.onMenuBuilderResult(session, cancel);
        MenuResult late = new MenuResult("1");
        late.token = first;
        menuService.onMenuBuilderResult(session, late);

        assertThat(pressed).containsExactly("first");
        assertThat(closed).isEmpty();
        assertThat(session.activeScreen()).isNotNull();
    }

    @Test
    @DisplayName("closing the dialog ends the flow")
    void cancel_closesTheScreen() {
        List<String> closed = new ArrayList<>();
        var flow = new TestFlow(screen(MenuMode.FOLLOW_UP), (context, action) -> {
        }) {
            @Override
            public void onClose(MenuRenderContext<String> context) {
                closed.add("closed");
            }
        };
        menuService.renderFlow(session, flow, "state", null);

        MenuResult cancel = new MenuResult();
        cancel.token = session.activeScreen().token();
        menuService.onMenuBuilderResult(session, cancel);

        assertThat(closed).containsExactly("closed");
        assertThat(session.activeScreen()).isNull();
        assertThat(session.hasActiveMenu()).isFalse();
    }

    @Test
    @DisplayName("a follow-up stays after a press, sent anew so that it still reports being closed")
    void press_keepsAFollowUpOpen() {
        menuService.renderFlow(session, flow(MenuMode.FOLLOW_UP, (context, action) -> {
        }), "state", null);
        long first = session.activeScreen().token();

        menuService.onMenuBuilderResult(session, press("0"));

        assertThat(session.activeScreen().isBuilt()).isTrue();
        assertThat(session.activeScreen().token()).isNotEqualTo(first);
        ArgumentCaptor<Long> tokens = ArgumentCaptor.forClass(Long.class);
        verify(gateway, times(2)).menuBuilder(any(), anyInt(), tokens.capture(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any());
        assertThat(tokens.getAllValues()).containsExactly(first, session.activeScreen().token());
        verify(gateway, never()).hideMenuBuilder(any(), anyInt());
    }

    @Test
    @DisplayName("a press that asks for a text takes the screen away and leaves the prompt")
    void press_thatOpensAPrompt() {
        menuService.renderFlow(session, flow(MenuMode.NORMAL, (context, action) ->
                context.openPrompt(new MenuPrompt("name", "Title", "Text", 16, "", false))), "state", null);

        menuService.onMenuBuilderResult(session, press("0"));

        assertThat(session.activeScreen()).isNull();
        assertThat(session.activePrompt()).isNotNull();
        verify(gateway).hideMenuBuilder(session.player, menuService.getMenuBuilderId());
    }

    @Test
    @DisplayName("a flow that closes its screen takes the dialog away, once")
    void close_hidesTheDialog() {
        menuService.renderFlow(session, flow(MenuMode.NORMAL, (context, action) -> context.close()), "state", null);

        menuService.onMenuBuilderResult(session, press("0"));

        assertThat(session.activeScreen()).isNull();
        verify(gateway, times(1)).hideMenuBuilder(session.player, menuService.getMenuBuilderId());
    }

    @Test
    @DisplayName("a follow-up under a prompt is sent anew once the prompt is answered")
    void prompt_overAFollowUp() {
        menuService.renderFlow(session, flow(MenuMode.FOLLOW_UP, (context, action) ->
                context.openPrompt(new MenuPrompt("name", "Title", "Text", 16, "", false))), "state", null);
        long first = session.activeScreen().token();

        menuService.onMenuBuilderResult(session, press("0"));
        // Nothing is put over the prompt.
        assertThat(session.activeScreen().token()).isEqualTo(first);

        menuService.onTextInput(session, null);

        assertThat(session.activePrompt()).isNull();
        assertThat(session.activeScreen().token()).isNotEqualTo(first);
        verify(gateway, times(2)).menuBuilder(any(), anyInt(), anyLong(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any());
    }

    private MenuResult press(String option) {
        MenuResult result = new MenuResult(option);
        result.token = session.activeScreen().token();
        return result;
    }

    private static MenuScreen screen(MenuMode mode) {
        return new MenuScreen(mode, "Title", "Text", List.of(
                List.of(MenuButton.of("First", "first"), MenuButton.of("Second", "second"))));
    }

    private static TestFlow flow(MenuMode mode, BiConsumer<MenuRenderContext<String>, String> onAction) {
        return new TestFlow(screen(mode), onAction);
    }

    private static class TestFlow implements MenuFlow<String> {
        private final MenuScreen screen;
        private final BiConsumer<MenuRenderContext<String>, String> onAction;

        TestFlow(MenuScreen screen, BiConsumer<MenuRenderContext<String>, String> onAction) {
            this.screen = screen;
            this.onAction = onAction;
        }

        @Override
        public Class<String> stateType() {
            return String.class;
        }

        @Override
        public MenuScreen render(MenuRenderContext<String> context) {
            return screen;
        }

        @Override
        public void onAction(MenuRenderContext<String> context, String actionId) {
            onAction.accept(context, actionId);
        }
    }

    private Session session() {
        Player player = Player.create();
        player.con = mock(NetConnection.class);
        PlayerData data = new PlayerData("uuid-built", true);
        return new Session(
                new TomlSecretsConfig(),
                mock(Bundle.class),
                menuService,
                mock(PlayerDataRepository.class),
                player,
                data
        );
    }
}
