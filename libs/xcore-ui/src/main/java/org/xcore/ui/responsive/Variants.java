package org.xcore.ui.responsive;

import org.xcore.ui.Ui;
import org.xcore.ui.VNode;
import org.xcore.ui.runtime.SlotKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * One dialog laid out once per class of screens, of which each client builds the one for its own.
 *
 * <p>The client does not shrink a dialog to its screen — a wrapped label asks for the width of its
 * whole line and a scroll pane grants it — and the server cannot see the screen. So a dialog that
 * has to fit a phone gives every piece a width, once per class of screens, and puts each layout
 * under the condition of the screens it is for. The client evaluates one comparison per node, so
 * a range of widths takes a table inside a table; this class builds that nesting.
 *
 * <p>Every layout carries its own copy of the dialog's slots, named {@link #slot(SlotKey, Variant)
 * per variant}. A patch goes to all of them ({@link #slots}); the client applies the one it built
 * and ignores the others.
 */
public final class Variants {

    private Variants() {
    }

    /**
     * A layout for screens at least {@code minWidth} wide, in the client's UI units.
     *
     * @param id       names the variant in the keys of its slots
     * @param minWidth the narrowest screen it is for; ignored for the narrowest variant, which
     *                 takes every screen the others leave
     * @param value    what the layout is drawn from, such as the widths of its columns
     */
    public record Variant<V>(String id, float minWidth, V value) {
        public Variant {
            Objects.requireNonNull(id, "id");
            if (minWidth < 0f || Float.isNaN(minWidth)) {
                throw new IllegalArgumentException("minWidth must be >= 0: " + minWidth);
            }
        }
    }

    /** Every variant's layout, each under the condition of the screens it is for. */
    public static <V> VNode byWidth(List<Variant<V>> variants, Function<? super Variant<V>, VNode> layout) {
        if (variants.isEmpty()) {
            throw new IllegalArgumentException("at least one variant is needed");
        }
        List<Variant<V>> widest = new ArrayList<>(variants);
        widest.sort(Comparator.comparingDouble((Variant<V> v) -> v.minWidth()).reversed());
        return Ui.table(root -> addFrom(root, widest, 0, layout));
    }

    private static <V> void addFrom(Ui.TableBuilder parent, List<Variant<V>> widest, int index,
                                    Function<? super Variant<V>, VNode> layout) {
        Variant<V> variant = widest.get(index);
        if (index == widest.size() - 1) {
            // The narrowest: the condition of the table it is in already holds it to what is left.
            parent.add(layout.apply(variant));
            return;
        }
        String threshold = format(variant.minWidth());
        parent.add(Ui.table(own -> {
            own.layout(l -> l.condition("width >= " + threshold));
            own.add(layout.apply(variant));
        }));
        parent.add(Ui.table(rest -> {
            rest.layout(l -> l.condition("width < " + threshold));
            addFrom(rest, widest, index + 1, layout);
        }));
    }

    /** The slot {@code base} in the layout of {@code variant}: every layout has its own. */
    public static <T> SlotKey<T> slot(SlotKey<T> base, Variant<?> variant) {
        return SlotKey.of(base.path() + "_" + variant.id());
    }

    /** The slots {@code bases} in every layout, for a patch that reaches whichever one the client built. */
    public static List<SlotKey<?>> slots(List<? extends Variant<?>> variants, SlotKey<?>... bases) {
        List<SlotKey<?>> all = new ArrayList<>(bases.length * variants.size());
        for (SlotKey<?> base : bases) {
            for (Variant<?> variant : variants) {
                all.add(slot(base, variant));
            }
        }
        return all;
    }

    /** Whole thresholds without a trailing {@code .0}; the client parses either. */
    private static String format(float value) {
        if (value == Math.rint(value) && !Float.isInfinite(value)) {
            return Long.toString((long) value);
        }
        return Float.toString(value);
    }
}
