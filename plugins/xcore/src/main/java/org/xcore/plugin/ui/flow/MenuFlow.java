package org.xcore.plugin.ui.flow;

public interface MenuFlow<TState> {

    Class<TState> stateType();

    MenuScreen render(MenuRenderContext<TState> context);

    default void onAction(MenuRenderContext<TState> context, String actionId) {
        // no-op
    }

    default void onPromptSubmit(MenuRenderContext<TState> context, String promptId, String text) {
        // no-op
    }

    default void onPromptCancel(MenuRenderContext<TState> context, String promptId) {
        // no-op
    }

    default void onClose(MenuRenderContext<TState> context) {
        // no-op
    }
}
