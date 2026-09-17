package org.xcore.testkit.ui;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import mindustry.ui.builder.UiDslWriter;

/**
 * Semantic stand-in for the Mindustry client menu model (MenuDialog + Menus registries).
 * Reproduces recorded client behavior only; no rendering, no geometry.
 */
public final class HeadlessMenuClient {
    private final Queue<MenuChoose> outbox = new java.util.ArrayDeque<>();
    private Window current;
    private final java.util.Map<String, String> lastPatches = new java.util.HashMap<>();

    static final class Window {
        final int menuId;
        long token;
        boolean wasHidden;
        Window(int menuId, long token) { this.menuId = menuId; this.token = token; }
    }

    /** One client→server menuBuilderChoose message. */
    public record MenuChoose(int menuId, long token, String action) {
        public boolean isCancel() { return action == null; }
    }

    public void show(int menuId, long token, UiSnapshot body) { show(menuId, token, false, body); }

    public void show(int menuId, long token, boolean hidePrevious, UiSnapshot body) {
        Objects.requireNonNull(body, "body");
        if (current != null && current.menuId == menuId) {
            // same-menuId replacement: server hides the old window first (hidePrevious)
            if (hidePrevious) emitCancel(current);
            current = null;
        }
        current = new Window(menuId, token);
    }

    public void update(int menuId, String targetId, UiSnapshot body) {
        if (current == null || current.menuId != menuId) return; // real client: no-op
        lastPatches.put(targetId, UiDslWriter.write(body.decode()));
    }

    /** Simulates pressing a button; the window stays visible (hideOnClick handled by explicit hide()). */
    public void click(int menuId, String action) {
        if (current == null || current.menuId != menuId) return;
        current.wasHidden = true;
        outbox.add(new MenuChoose(menuId, current.token, action));
    }

    /** Simulates Escape/back without a prior click; sends cancel like the real client. */
    public void dismiss(int menuId) {
        if (current == null || current.menuId != menuId) return;
        if (!current.wasHidden) emitCancel(current); // post-click Escape sends nothing (wasHidden)
        current = null;
    }

    public void hide(int menuId) {
        if (current == null || current.menuId != menuId) return;
        emitCancel(current);
        current = null;
    }

    public boolean isVisible(int menuId) {
        return current != null && current.menuId == menuId;
    }

    /** DSL of the last patch delivered to {@code targetId} of the visible window, or null. */
    public String lastPatchDsl(String targetId) {
        return lastPatches.get(targetId);
    }

    public Queue<MenuChoose> outbox() { return outbox; }

    private void emitCancel(Window window) {
        outbox.add(new MenuChoose(window.menuId, window.token, null));
    }
}
