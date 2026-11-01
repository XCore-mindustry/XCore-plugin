package org.xcore.plugin.ui.flow;

import org.xcore.plugin.ui.route.MenuRoute;

import java.util.function.Consumer;

public class ActiveMenuPrompt {
    private final long version;
    private final int promptId;
    private final Consumer<String> onSubmit;
    private final Runnable onCancel;
    private final MenuFlow<?> flow;
    private final Object state;
    private final String promptIdString;
    private final MenuRoute route;

    private ActiveMenuPrompt(long version, int promptId, Consumer<String> onSubmit, Runnable onCancel, MenuFlow<?> flow, Object state, String promptIdString, MenuRoute route) {
        this.version = version;
        this.promptId = promptId;
        this.onSubmit = onSubmit;
        this.onCancel = onCancel;
        this.flow = flow;
        this.state = state;
        this.promptIdString = promptIdString;
        this.route = route;
    }

    public static ActiveMenuPrompt create(long version, int promptId, Consumer<String> onSubmit, Runnable onCancel) {
        return new ActiveMenuPrompt(version, promptId, onSubmit, onCancel, null, null, null, null);
    }

    public static ActiveMenuPrompt create(long version, int promptId, Consumer<String> onSubmit, Runnable onCancel, MenuFlow<?> flow, Object state, String promptIdString) {
        return new ActiveMenuPrompt(version, promptId, onSubmit, onCancel, flow, state, promptIdString, null);
    }

    public static ActiveMenuPrompt create(long version, int promptId, Consumer<String> onSubmit, Runnable onCancel, MenuFlow<?> flow, Object state, String promptIdString, MenuRoute route) {
        return new ActiveMenuPrompt(version, promptId, onSubmit, onCancel, flow, state, promptIdString, route);
    }

    public long version() {
        return version;
    }

    public int promptId() {
        return promptId;
    }

    public void submit(String text) {
        if (onSubmit != null) {
            onSubmit.accept(text);
        }
    }

    public void cancel() {
        if (onCancel != null) {
            onCancel.run();
        }
    }

    public boolean hasFlow() {
        return flow != null;
    }

    public MenuFlow<?> flow() {
        return flow;
    }

    public Object state() {
        return state;
    }

    public boolean hasRoute() {
        return route != null;
    }

    public MenuRoute route() {
        return route;
    }

    public String promptIdString() {
        return promptIdString;
    }
}
