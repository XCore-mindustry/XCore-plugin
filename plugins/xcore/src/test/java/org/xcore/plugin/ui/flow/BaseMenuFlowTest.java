package org.xcore.plugin.ui.flow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.route.MenuRoute;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BaseMenuFlowTest {

    @Test
    @DisplayName("onAction dispatches exact registered action")
    void onAction_dispatchesExactRegisteredAction() {
        TestFlow flow = new TestFlow();
        MenuRenderContext<TestState> context = context(flow, new TestState());

        flow.onAction(context, "exact");

        assertThat(flow.exactActionTriggered.get()).isTrue();
    }

    @Test
    @DisplayName("onAction dispatches prefix action with suffix")
    void onAction_dispatchesPrefixActionWithSuffix() {
        TestFlow flow = new TestFlow();
        MenuRenderContext<TestState> context = context(flow, new TestState());

        flow.onAction(context, "prefix:payload-42");

        assertThat(flow.lastPrefixSuffix.get()).isEqualTo("payload-42");
    }

    @Test
    @DisplayName("onAction dispatches default action for unknown ids")
    void onAction_dispatchesDefaultActionForUnknownIds() {
        TestFlow flow = new TestFlow();
        MenuRenderContext<TestState> context = context(flow, new TestState());

        flow.onAction(context, "unexpected-action");

        assertThat(flow.lastDefaultAction.get()).isEqualTo("unexpected-action");
    }

    @Test
    @DisplayName("default back action delegates to MenuService")
    void defaultBackAction_delegatesToMenuService() {
        TestFlow flow = new TestFlow();
        MenuService menuService = mock(MenuService.class);
        Session session = mock(Session.class);
        when(menuService.goBack(session)).thenReturn(true);
        MenuRenderContext<TestState> context = new MenuRenderContext<>(menuService, session, flow, new TestState());

        flow.onAction(context, "back");

        verify(menuService).goBack(session);
    }

    @Test
    @DisplayName("default close action delegates to MenuService")
    void defaultCloseAction_delegatesToMenuService() {
        TestFlow flow = new TestFlow();
        MenuService menuService = mock(MenuService.class);
        Session session = mock(Session.class);
        MenuRenderContext<TestState> context = new MenuRenderContext<>(menuService, session, flow, new TestState());

        flow.onAction(context, "close");

        verify(menuService).close(session);
    }

    @Test
    @DisplayName("onPromptSubmit dispatches registered prompt handler")
    void onPromptSubmit_dispatchesRegisteredPromptHandler() {
        TestFlow flow = new TestFlow();
        MenuRenderContext<TestState> context = context(flow, new TestState());

        flow.onPromptSubmit(context, "rename", "new-name");

        assertThat(flow.lastPromptText.get()).isEqualTo("new-name");
        assertThat(flow.lastPromptState.get()).isSameAs(context.state());
    }

    @Test
    @DisplayName("onPromptCancel dispatches registered cancel handler")
    void onPromptCancel_dispatchesRegisteredCancelHandler() {
        TestFlow flow = new TestFlow();
        MenuRenderContext<TestState> context = context(flow, new TestState());

        flow.onPromptCancel(context, "rename");

        assertThat(flow.promptCancelTriggered.get()).isTrue();
    }

    @Test
    @DisplayName("createState returns NoState singleton for stateless flows")
    void createState_returnsNoStateSingletonForStatelessFlows() {
        StatelessFlow flow = new StatelessFlow();

        NoState state = flow.createState(mock(Session.class), MenuRoute.of("stateless"), null);

        assertThat(state).isSameAs(NoState.INSTANCE);
    }

    @Test
    @DisplayName("createState instantiates state type with no-args constructor")
    void createState_instantiatesStateTypeWithNoArgsConstructor() {
        TestFlow flow = new TestFlow();

        TestState state = flow.createState(mock(Session.class), MenuRoute.of("test"), null);

        assertThat(state).isNotNull();
        assertThat(state.createdByConstructor).isTrue();
    }

    private static MenuRenderContext<TestState> context(TestFlow flow, TestState state) {
        return new MenuRenderContext<>(mock(MenuService.class), mock(Session.class), flow, state);
    }

    private static final class TestFlow extends BaseMenuFlow<TestState> {
        private final AtomicBoolean exactActionTriggered = new AtomicBoolean(false);
        private final AtomicReference<String> lastPrefixSuffix = new AtomicReference<>();
        private final AtomicReference<String> lastDefaultAction = new AtomicReference<>();
        private final AtomicReference<String> lastPromptText = new AtomicReference<>();
        private final AtomicReference<TestState> lastPromptState = new AtomicReference<>();
        private final AtomicBoolean promptCancelTriggered = new AtomicBoolean(false);

        private TestFlow() {
            super("test.flow", TestState.class);
            action("exact", ctx -> exactActionTriggered.set(true));
            actionPrefix("prefix:", (ctx, suffix) -> lastPrefixSuffix.set(suffix));
            defaultAction((ctx, actionId) -> lastDefaultAction.set(actionId));
            onPrompt("rename", prompt -> {
                lastPromptText.set(prompt.text());
                lastPromptState.set(prompt.renderContext().state());
            }, ctx -> promptCancelTriggered.set(true));
        }

        @Override
        public MenuScreen render(MenuRenderContext<TestState> context) {
            return null;
        }
    }

    private static final class StatelessFlow extends BaseMenuFlow<NoState> {
        private StatelessFlow() {
            super("stateless.flow", NoState.class);
        }

        @Override
        public MenuScreen render(MenuRenderContext<NoState> context) {
            return null;
        }
    }

    static final class TestState {
        private final boolean createdByConstructor;

        TestState() {
            this.createdByConstructor = true;
        }
    }
}
