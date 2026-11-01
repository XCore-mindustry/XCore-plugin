package org.xcore.plugin.ui.flow;

import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.route.MenuRoute;
import org.xcore.plugin.ui.route.RoutedMenuFlow;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class BaseMenuFlow<T> implements RoutedMenuFlow<T> {

    private final String routeId;
    private final Class<T> stateType;

    private final Map<String, Consumer<MenuRenderContext<T>>> actions = new LinkedHashMap<>();
    private final Map<String, BiConsumer<MenuRenderContext<T>, String>> prefixActions = new LinkedHashMap<>();
    private final Map<String, Consumer<MenuPromptContext<T>>> promptSubmits = new LinkedHashMap<>();
    private final Map<String, Consumer<MenuRenderContext<T>>> promptCancels = new LinkedHashMap<>();
    private BiConsumer<MenuRenderContext<T>, String> defaultActionHandler;

    protected BaseMenuFlow(String routeId, Class<T> stateType) {
        this.routeId = routeId;
        this.stateType = stateType;
        action("back", ctx -> ctx.goBack());
        action("close", ctx -> ctx.close());
    }

    @Override
    public final String routeId() {
        return routeId;
    }

    @Override
    public final Class<T> stateType() {
        return stateType;
    }

    protected void action(String id, Consumer<MenuRenderContext<T>> handler) {
        actions.put(id, handler);
    }

    protected void actionPrefix(String prefix, BiConsumer<MenuRenderContext<T>, String> handler) {
        if (!prefix.endsWith(":")) {
            throw new IllegalArgumentException("Prefix actions must end with a colon (:): " + prefix);
        }
        prefixActions.put(prefix, handler);
    }

    protected void defaultAction(BiConsumer<MenuRenderContext<T>, String> handler) {
        this.defaultActionHandler = handler;
    }

    protected void onPrompt(String id, Consumer<MenuPromptContext<T>> submitHandler) {
        promptSubmits.put(id, submitHandler);
    }

    protected void onPrompt(String id, Consumer<MenuPromptContext<T>> submitHandler, Consumer<MenuRenderContext<T>> cancelHandler) {
        promptSubmits.put(id, submitHandler);
        promptCancels.put(id, cancelHandler);
    }

    protected void onPromptCancel(String id, Consumer<MenuRenderContext<T>> handler) {
        promptCancels.put(id, handler);
    }

    @Override
    public void onAction(MenuRenderContext<T> context, String actionId) {
        var handler = actions.get(actionId);
        if (handler != null) {
            handler.accept(context);
            return;
        }

        for (var entry : prefixActions.entrySet()) {
            String prefix = entry.getKey();
            if (actionId.startsWith(prefix)) {
                String suffix = actionId.substring(prefix.length());
                entry.getValue().accept(context, suffix);
                return;
            }
        }

        if (defaultActionHandler != null) {
            defaultActionHandler.accept(context, actionId);
        }
    }

    @Override
    public void onPromptSubmit(MenuRenderContext<T> context, String promptId, String text) {
        var handler = promptSubmits.get(promptId);
        if (handler != null) {
            handler.accept(new MenuPromptContext<>(context, text));
        }
    }

    @Override
    public void onPromptCancel(MenuRenderContext<T> context, String promptId) {
        var handler = promptCancels.get(promptId);
        if (handler != null) {
            handler.accept(context);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public T createState(Session session, MenuRoute route, T currentState) {
        Class<T> type = stateType();
        if (type == null) {
            return currentState;
        }
        if (type == NoState.class) {
            return (T) NoState.INSTANCE;
        }
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            return currentState;
        }
    }
}
