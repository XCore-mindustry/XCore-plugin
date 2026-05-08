package org.xcore.plugin.ui.flow;

public sealed interface MenuAction {
    void run();

    record CallbackAction(Runnable callback) implements MenuAction {
        @Override
        public void run() {
            if (callback != null) callback.run();
        }
    }

    record NamedAction(String id, Runnable callback) implements MenuAction {
        @Override
        public void run() {
            if (callback != null) callback.run();
        }
    }
}
