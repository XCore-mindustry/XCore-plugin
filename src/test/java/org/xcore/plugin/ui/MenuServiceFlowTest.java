package org.xcore.plugin.ui;

import com.ospx.flubundle.Bundle;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.flow.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MenuServiceFlowTest {

    private MindustryMenuGateway gateway;
    private MenuService menuService;
    private Session session;

    @BeforeEach
    void setUp() {
        gateway = mock(MindustryMenuGateway.class);
        menuService = new MenuService(null, gateway);
        session = session();
    }

    @Test
    @DisplayName("renderFlow shows NORMAL screen from flow render")
    void renderFlow_showsNormalScreenFromFlowRender() {
        MenuFlow<String> flow = mock(MenuFlow.class);
        when(flow.stateType()).thenReturn(String.class);
        MenuScreen screen = MenuScreen.normal("Title", "Content", List.of(List.of(MenuButton.of("Btn", "btn1"))));
        when(flow.render(any(MenuRenderContext.class))).thenReturn(screen);

        session.setDraft("state");
        menuService.renderFlow(session, flow);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.NORMAL);
        verify(gateway).menu(eq(session.player), eq(0), eq("Title"), eq("Content"), any());
    }

    @Test
    @DisplayName("renderFlow shows FOLLOW_UP screen from flow render")
    void renderFlow_showsFollowUpScreenFromFlowRender() {
        MenuFlow<String> flow = mock(MenuFlow.class);
        when(flow.stateType()).thenReturn(String.class);
        MenuScreen screen = MenuScreen.followUp("Title", "Content", List.of(List.of(MenuButton.of("Btn", "btn1"))));
        when(flow.render(any(MenuRenderContext.class))).thenReturn(screen);

        session.setDraft("state");
        menuService.renderFlow(session, flow);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.FOLLOW_UP);
        verify(gateway).followUpMenu(eq(session.player), eq(0), eq("Title"), eq("Content"), any());
    }

    @Test
    @DisplayName("onMenuOption dispatches named action to flow onAction")
    void onMenuOption_dispatchesNamedActionToFlowOnAction() {
        AtomicReference<String> receivedActionId = new AtomicReference<>();

        MenuFlow<String> flow = new MenuFlow<>() {
            @Override
            public Class<String> stateType() {
                return String.class;
            }

            @Override
            public MenuScreen render(MenuRenderContext<String> context) {
                return MenuScreen.normal("T", "C", List.of(List.of(MenuButton.of("OK", "ok"))));
            }

            @Override
            public void onAction(MenuRenderContext<String> context, String actionId) {
                receivedActionId.set(actionId);
            }
        };

        session.setDraft("state");
        menuService.renderFlow(session, flow);

        menuService.onMenuOption(session, 0);

        assertThat(receivedActionId.get()).isEqualTo("ok");
    }

    @Test
    @DisplayName("onMenuOption option -1 calls flow onClose")
    void onMenuOption_optionMinusOneCallsFlowOnClose() {
        AtomicBoolean closed = new AtomicBoolean(false);

        MenuFlow<String> flow = new MenuFlow<>() {
            @Override
            public Class<String> stateType() {
                return String.class;
            }

            @Override
            public MenuScreen render(MenuRenderContext<String> context) {
                return MenuScreen.followUp("T", "C", List.of());
            }

            @Override
            public void onClose(MenuRenderContext<String> context) {
                closed.set(true);
            }
        };

        session.setDraft("state");
        menuService.renderFlow(session, flow);

        menuService.onMenuOption(session, -1);

        assertThat(closed).isTrue();
        assertThat(session.activeScreen()).isNull();
    }

    @Test
    @DisplayName("onMenuOption option -1 keeps replacement screen rendered by flow onClose")
    void onMenuOption_optionMinusOneKeepsReplacementScreenRenderedByFlowOnClose() {
        MenuFlow<String> replacementFlow = mockFlow("Replacement", "C2");

        MenuFlow<String> flow = new MenuFlow<>() {
            @Override
            public Class<String> stateType() {
                return String.class;
            }

            @Override
            public MenuScreen render(MenuRenderContext<String> context) {
                return MenuScreen.normal("T", "C", List.of());
            }

            @Override
            public void onClose(MenuRenderContext<String> context) {
                context.session().menuService.show(
                        context.session(),
                        MenuScreen.normal("Replacement", "C2", List.of()),
                        replacementFlow,
                        context.state()
                );
            }
        };

        session.setDraft("state");
        menuService.renderFlow(session, flow);
        ActiveMenuScreen original = session.activeScreen();

        menuService.onMenuOption(session, -1);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen()).isNotSameAs(original);
        verify(gateway).menu(eq(session.player), eq(0), eq("Replacement"), eq("C2"), any());
    }

    @Test
    @DisplayName("close calls flow onClose for active flow screen")
    void close_callsFlowOnClose() {
        AtomicBoolean closed = new AtomicBoolean(false);

        MenuFlow<String> flow = new MenuFlow<>() {
            @Override
            public Class<String> stateType() {
                return String.class;
            }

            @Override
            public MenuScreen render(MenuRenderContext<String> context) {
                return MenuScreen.normal("T", "C", List.of());
            }

            @Override
            public void onClose(MenuRenderContext<String> context) {
                closed.set(true);
            }
        };

        session.setDraft("state");
        menuService.renderFlow(session, flow);

        menuService.close(session);

        assertThat(closed).isTrue();
        assertThat(session.activeScreen()).isNull();
    }

    @Test
    @DisplayName("close keeps replacement screen rendered by flow onClose")
    void close_keepsReplacementScreenRenderedByFlowOnClose() {
        MenuFlow<String> replacementFlow = mockFlow("Replacement", "C2");

        MenuFlow<String> flow = new MenuFlow<>() {
            @Override
            public Class<String> stateType() {
                return String.class;
            }

            @Override
            public MenuScreen render(MenuRenderContext<String> context) {
                return MenuScreen.normal("T", "C", List.of());
            }

            @Override
            public void onClose(MenuRenderContext<String> context) {
                context.session().menuService.show(
                        context.session(),
                        MenuScreen.normal("Replacement", "C2", List.of()),
                        replacementFlow,
                        context.state()
                );
            }
        };

        session.setDraft("state");
        menuService.renderFlow(session, flow);
        ActiveMenuScreen original = session.activeScreen();

        menuService.close(session);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen()).isNotSameAs(original);
        verify(gateway).menu(eq(session.player), eq(0), eq("Replacement"), eq("C2"), any());
    }

    @Test
    @DisplayName("onTextInput dispatches submit to flow onPromptSubmit")
    void onTextInput_dispatchesSubmitToFlowOnPromptSubmit() {
        AtomicReference<String> receivedText = new AtomicReference<>();

        MenuFlow<String> flow = new MenuFlow<>() {
            @Override
            public Class<String> stateType() {
                return String.class;
            }

            @Override
            public MenuScreen render(MenuRenderContext<String> context) {
                return MenuScreen.normal("T", "C", List.of());
            }

            @Override
            public void onPromptSubmit(MenuRenderContext<String> context, String promptId, String text) {
                receivedText.set(text);
            }
        };

        session.setDraft("state");
        menuService.openPrompt(session, flow, "state", new MenuPrompt("p1", "Title", "Content", 50, "def", false));

        menuService.onTextInput(session, "hello");

        assertThat(receivedText.get()).isEqualTo("hello");
        assertThat(session.activePrompt()).isNull();
    }

    @Test
    @DisplayName("onTextInput dispatches cancel to flow onPromptCancel")
    void onTextInput_dispatchesCancelToFlowOnPromptCancel() {
        AtomicBoolean cancelled = new AtomicBoolean(false);

        MenuFlow<String> flow = new MenuFlow<>() {
            @Override
            public Class<String> stateType() {
                return String.class;
            }

            @Override
            public MenuScreen render(MenuRenderContext<String> context) {
                return MenuScreen.normal("T", "C", List.of());
            }

            @Override
            public void onPromptCancel(MenuRenderContext<String> context, String promptId) {
                cancelled.set(true);
            }
        };

        session.setDraft("state");
        menuService.openPrompt(session, flow, "state", new MenuPrompt("p1", "Title", "Content", 50, "def", false));

        menuService.onTextInput(session, null);

        assertThat(cancelled).isTrue();
        assertThat(session.activePrompt()).isNull();
    }

    private MenuFlow<String> mockFlow(String title, String content) {
        return new MenuFlow<>() {
            @Override
            public Class<String> stateType() {
                return String.class;
            }

            @Override
            public MenuScreen render(MenuRenderContext<String> context) {
                return MenuScreen.normal(title, content, List.of());
            }
        };
    }

    private Session session() {
        Player player = Player.create();
        player.con = mock(NetConnection.class);
        PlayerData data = new PlayerData("uuid-flow", true);
        return new Session(
                new GlobalConfig(),
                mock(Bundle.class),
                menuService,
                mock(PlayerDataRepository.class),
                player,
                data
        );
    }
}
