package org.xcore.plugin.ui.flow;

import java.util.List;

public class ActiveMenuScreen {
    private final long version;
    private final MenuMode mode;
    private final List<MenuAction> actions;
    private final MenuFlow<?> flow;
    private final Object state;
    private final List<String> actionIds;

    private ActiveMenuScreen(long version, MenuMode mode, List<MenuAction> actions, MenuFlow<?> flow, Object state, List<String> actionIds) {
        this.version = version;
        this.mode = mode;
        this.actions = List.copyOf(actions);
        this.flow = flow;
        this.state = state;
        this.actionIds = actionIds == null ? List.of() : List.copyOf(actionIds);
    }

    public static ActiveMenuScreen create(long version, MenuMode mode, List<MenuAction> actions) {
        return new ActiveMenuScreen(version, mode, actions, null, null, null);
    }

    public static ActiveMenuScreen create(long version, MenuMode mode, List<MenuAction> actions, MenuFlow<?> flow, Object state, List<String> actionIds) {
        return new ActiveMenuScreen(version, mode, actions, flow, state, actionIds);
    }

    public long version() {
        return version;
    }

    public MenuMode mode() {
        return mode;
    }

    public int actionCount() {
        return actions.size();
    }

    public MenuAction actionAt(int index) {
        if (index < 0 || index >= actions.size()) {
            return null;
        }
        return actions.get(index);
    }

    public void runAction(int index) {
        var action = actionAt(index);
        if (action != null) {
            action.run();
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

    public String actionIdAt(int index) {
        if (index < 0 || index >= actionIds.size()) {
            return null;
        }
        return actionIds.get(index);
    }
}
