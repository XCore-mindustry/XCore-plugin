package org.xcore.testkit.ui;

/**
 * Immutable snapshot of one message traversing the client-server boundary.
 */
public sealed interface UiWireMessage {
    record Show(int menuId, long token, boolean hidePrevious, UiSnapshot body) implements UiWireMessage {}
    record Update(int menuId, String targetId, UiSnapshot body) implements UiWireMessage {}
    record Hide(int menuId) implements UiWireMessage {}
    record Choose(int menuId, long token, String action) implements UiWireMessage {
        public boolean isCancel() { return action == null; }
    }
}
