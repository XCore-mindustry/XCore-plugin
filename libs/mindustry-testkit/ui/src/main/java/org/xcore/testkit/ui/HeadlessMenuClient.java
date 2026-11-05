package org.xcore.testkit.ui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.stream.Collectors;
import mindustry.ui.builder.MenuResult;
import mindustry.ui.builder.UiDslWriter;
import mindustry.ui.builder.UiKey;

/**
 * Semantic stand-in for the Mindustry client menu model ({@code Menus.MenuDialog} and
 * {@code UiTreeBuilder}). Reproduces recorded client behavior; no rendering, no geometry.
 *
 * <p>It builds each dialog the way the client does, for its {@link ClientScreen}:
 * <ul>
 *   <li>a node whose {@code condition} fails on that screen is not built;</li>
 *   <li>a patch ({@code menuBuilderUpdate}) clears a table the client built under that id and
 *       builds into it; an id it does not know, or one that is not a table, is ignored, and what
 *       the cleared table held is gone from the screen;</li>
 *   <li>a press sends the form values of the build the pressed element came from. The dialog's
 *       build shares its id map with the dialog, and every patch adds its ids to it, so a button
 *       of the dialog sends the fields of the dialog and of every patch since; a button a patch
 *       built sends that patch's fields only;</li>
 *   <li>a dialog shown with {@code hideOnClick} closes on a press without reporting a close.</li>
 * </ul>
 *
 * <p>{@link #press} acts like a player: it finds the button and fails when the client has none to
 * press. {@link #click} only sends an answer, for tests about the transport.
 *
 * <p>Not modelled: the {@code checked} state a toggle button sends when its style has one, button
 * groups, and a dialog shown with {@code hidePrevious = false}, which the client leaves on the
 * screen; here the new one takes its place.
 */
public final class HeadlessMenuClient {
    private final Queue<MenuChoose> outbox = new ArrayDeque<>();
    private final ClientScreen screen;
    private Window current;
    private final Map<String, String> lastPatches = new HashMap<>();
    private final List<String> ignoredPatches = new ArrayList<>();

    static final class Window {
        final int menuId;
        long token;
        boolean wasHidden;
        final boolean hideOnClick;
        final ClientTree.Element root;
        /**
         * The client's id map. It is the dialog build's own map ({@code dialog.idToElement =
         * result.idElements}), and every patch puts its ids in, so a patch's ids replace older ones
         * and a button of the dialog sends them too.
         */
        final Map<String, ClientTree.Element> idToElement;

        Window(int menuId, long token, boolean hideOnClick, ClientTree.Element root) {
            this.menuId = menuId;
            this.token = token;
            this.hideOnClick = hideOnClick;
            this.root = root;
            this.idToElement = root.pass.ids;
        }
    }

    /** One client→server menuBuilderChoose message, with the form values sent along with it. */
    public record MenuChoose(int menuId, long token, String action, Map<String, Object> values) {
        public MenuChoose {
            values = Map.copyOf(values);
        }

        public MenuChoose(int menuId, long token, String action) {
            this(menuId, token, action, Map.of());
        }

        public boolean isCancel() { return action == null; }

        /** The {@link MenuResult} a server receives for this answer: action, token and values. */
        public MenuResult toMenuResult() {
            return new UiWireMessage.Choose(menuId, token, action, values).toMenuResult();
        }
    }

    /** A client on a {@link ClientScreen#DESKTOP desktop}. */
    public HeadlessMenuClient() {
        this(ClientScreen.DESKTOP);
    }

    public HeadlessMenuClient(ClientScreen screen) {
        this.screen = Objects.requireNonNull(screen, "screen");
    }

    public ClientScreen screen() {
        return screen;
    }

    public void show(int menuId, long token, UiSnapshot body) { show(menuId, token, false, body); }

    public void show(int menuId, long token, boolean hidePrevious, UiSnapshot body) {
        show(menuId, token, hidePrevious, false, body);
    }

    public void show(int menuId, long token, boolean hidePrevious, boolean hideOnClick, UiSnapshot body) {
        Objects.requireNonNull(body, "body");
        if (current != null && current.menuId == menuId) {
            // Menus hides the old instance during replacement; its hidden listener
            // only emits cancel if that instance has not reported a click.
            if (hidePrevious) emitCancel(current);
            current = null;
        }
        ClientTree.Pass pass = new ClientTree.Pass();
        ClientTree.Element root = new ClientTree.Element(UiKey.table, body.decode(), pass);
        ClientTree.buildInto(root, root.node, pass, screen);
        current = new Window(menuId, token, hideOnClick, root);
        lastPatches.clear();
    }

    /**
     * Applies a patch as {@code Menus.menuBuilderUpdate} does: into the table built under
     * {@code targetId}, or not at all when the client has no such table.
     */
    public void update(int menuId, String targetId, UiSnapshot body) {
        if (current == null || current.menuId != menuId) return; // real client: no-op
        ClientTree.Element target = current.idToElement.get(targetId);
        if (target == null || !target.patchable()) {
            ignoredPatches.add(targetId);
            return;
        }
        for (ClientTree.Element child : target.children) child.detach();
        target.children.clear();
        ClientTree.Pass pass = new ClientTree.Pass();
        ClientTree.buildInto(target, body.decode(), pass, screen);
        current.idToElement.putAll(pass.ids);
        lastPatches.put(targetId, UiDslWriter.write(body.decode()));
    }

    /**
     * Presses what sends {@code action} in the visible dialog, as a player would, with no form
     * field changed. See {@link #press(int, String, Map)}.
     */
    public void press(int menuId, String action) {
        press(menuId, action, Map.of());
    }

    /**
     * Presses the button, image button or button table whose answer is {@code action} (or presses
     * Enter in the field that sends it), after the player entered {@code entered} into the form,
     * keyed by element id. The answer carries the fields, checks and sliders of the build that
     * element came from (for the dialog's, also those of every patch), with what the player
     * entered in place of their initial values.
     *
     * @throws IllegalStateException when no dialog is visible, or none of its elements on the
     *         screen sends {@code action}, or the one that does is disabled
     * @throws IllegalArgumentException when {@code entered} names an element that is not a form
     *         input of that build: the client would not send it
     */
    public void press(int menuId, String action, Map<String, ?> entered) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(entered, "entered");
        if (current == null || current.menuId != menuId) {
            throw new IllegalStateException("menu " + menuId + " is not on the screen");
        }
        List<ClientTree.Element> onScreen = ClientTree.attached(current.root);
        ClientTree.Element pressed = onScreen.stream()
                .filter(e -> action.equals(e.clicked()) || action.equals(e.enter()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("nothing in menu " + menuId + " sends '" + action
                        + "'; it shows " + onScreen.stream().map(ClientTree.Element::clicked)
                        .filter(Objects::nonNull).distinct().collect(Collectors.toList())));
        if (pressed.disabled()) {
            throw new IllegalStateException("'" + action + "' is disabled in menu " + menuId);
        }

        Map<String, Object> values = new LinkedHashMap<>();
        pressed.pass.ids.forEach((id, element) -> {
            Object value = element.initialValue();
            if (value != null) values.put(id, value);
        });
        entered.forEach((id, value) -> {
            if (!values.containsKey(id)) {
                throw new IllegalArgumentException("'" + id + "' is not a form input built with '" + action
                        + "', so the client does not send it; that build has " + values.keySet());
            }
            values.put(id, value);
        });
        answer(action, values);
    }

    /**
     * Sends {@code action} as the visible dialog's answer, as if a button sent it, without looking
     * for that button; for tests about the transport. A test of a menu presses with {@link #press}.
     */
    public void click(int menuId, String action) {
        click(menuId, action, Map.of());
    }

    /**
     * Like {@link #click(int, String)}, with {@code values} as the form fields of the answer, keyed
     * by element id and sent as given.
     */
    public void click(int menuId, String action, Map<String, ?> values) {
        if (current == null || current.menuId != menuId) return;
        answer(action, Map.copyOf(values));
    }

    private void answer(String action, Map<String, ?> values) {
        current.wasHidden = true;
        outbox.add(new MenuChoose(current.menuId, current.token, action, Map.copyOf(values)));
        if (current.hideOnClick) {
            // The dialog hides itself after the press; wasHidden keeps it from reporting a close.
            current = null;
        }
    }

    /** Simulates Escape/back without a prior click; sends cancel like the real client. */
    public void dismiss(int menuId) {
        if (current == null || current.menuId != menuId) return;
        emitCancel(current);
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

    /** Whether the visible dialog {@code menuId} shows an element built under {@code elementId}. */
    public boolean isShown(int menuId, String elementId) {
        if (!isVisible(menuId)) return false;
        ClientTree.Element element = current.idToElement.get(elementId);
        return element != null && element.attached;
    }

    /** DSL of the last patch the visible window applied to {@code targetId}, or null. */
    public String lastPatchDsl(String targetId) {
        return lastPatches.get(targetId);
    }

    /** Targets of the patches the client ignored, as it had no table under their id; in order. */
    public List<String> ignoredPatches() {
        return List.copyOf(ignoredPatches);
    }

    public Queue<MenuChoose> outbox() { return outbox; }

    private void emitCancel(Window window) {
        if (window.wasHidden) return;
        outbox.add(new MenuChoose(window.menuId, window.token, null));
    }
}
