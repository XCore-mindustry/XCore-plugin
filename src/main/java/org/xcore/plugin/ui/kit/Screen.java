package org.xcore.plugin.ui.kit;

import org.xcore.ui.Ui;
import org.xcore.ui.VNode;
import org.xcore.ui.runtime.SlotKey;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A class of client screens by width, in the client's own UI units.
 *
 * <p>The client does not shrink a dialog to the screen it has: a wrapped label asks for the width
 * of its whole line, a row of buttons for the width of all of them, and a scroll pane grants both.
 * The server cannot see the screen either. So a menu is laid out once per class, with a width on
 * every piece, and each client builds the one layout whose condition matches its own screen.
 *
 * <p>Height needs no classes: a dialog opened to fill the screen gives its content the height
 * there is, so the pane of the menu takes what is left and scrolls.
 *
 * @param id      names the class in the ids of slots
 * @param columns columns of cards
 * @param card    width of a card
 */
public record Screen(String id, int columns, float card) {

    /** A desktop, a tablet, or a phone held sideways. */
    public static final Screen WIDE = new Screen("wide", 2, 350f);
    /** A phone held upright. */
    public static final Screen NARROW = new Screen("narrow", 1, 450f);
    /** A small phone, or a larger one with the interface scaled up. */
    public static final Screen SMALL = new Screen("small", 1, 330f);

    public static final List<Screen> ALL = List.of(WIDE, NARROW, SMALL);

    /** The narrowest screens {@link #WIDE} and {@link #NARROW} fit on. */
    public static final int WIDE_WIDTH = 800;
    public static final int NARROW_WIDTH = 490;

    /** Width of the cards side by side. */
    public float cards() {
        return columns * card + (columns - 1) * Kit.GAP;
    }

    /** Width of everything in the window. A single column is swiped, so it has no scroll bar. */
    public float width() {
        return columns > 1 ? cards() + Kit.SCROLLBAR : cards();
    }

    /**
     * The same menu for every class of screens, each under the condition of the screens it is
     * for. The client evaluates one comparison per node, so a range of widths takes a table
     * inside a table.
     */
    public static VNode each(Function<Screen, VNode> window) {
        return Ui.table(root -> {
            root.add(Ui.table(wide -> {
                wide.layout(l -> l.condition("width >= " + WIDE_WIDTH));
                wide.add(window.apply(WIDE));
            }));
            root.add(Ui.table(phones -> {
                phones.layout(l -> l.condition("width < " + WIDE_WIDTH));
                phones.add(Ui.table(narrow -> {
                    narrow.layout(l -> l.condition("width >= " + NARROW_WIDTH));
                    narrow.add(window.apply(NARROW));
                }));
                phones.add(Ui.table(small -> {
                    small.layout(l -> l.condition("width < " + NARROW_WIDTH));
                    small.add(window.apply(SMALL));
                }));
            }));
        });
    }

    /** The slot {@code base} of this class's layout: every layout has its own, since a client builds one. */
    public <T> SlotKey<T> slot(SlotKey<T> base) {
        return SlotKey.of(base.path() + "_" + id);
    }

    /**
     * The slots {@code bases} of every layout. A patch goes to all of them; the client applies
     * the one it has built and ignores the rest.
     */
    public static List<SlotKey<?>> slots(SlotKey<?>... bases) {
        List<SlotKey<?>> all = new ArrayList<>();
        for (SlotKey<?> base : bases) {
            for (Screen screen : ALL) {
                all.add(screen.slot(base));
            }
        }
        return all;
    }
}
