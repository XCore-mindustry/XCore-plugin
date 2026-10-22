package org.xcore.ui.responsive;

import org.xcore.ui.Ui;

/**
 * Structural variants that the client picks, rather than the server guessing.
 *
 * <p>These place one piece under a condition. A whole dialog laid out once per class of screens,
 * which is what keeps text on a phone's screen, goes through {@link Variants#byWidth} instead.
 * What is left here is a local difference: a portrait phone stacking a name above its score while
 * a landscape desktop shows them side by side.
 *
 * <p>Each variant is a real node carrying Mindustry's native {@code condition}, evaluated in
 * {@code UiTreeBuilder.evalCondition} on the client from {@code Core.graphics.isPortrait()} or
 * {@code Core.scene.getWidth() / Scl.scl(1f)}. Conditions are evaluated once, when the dialog is
 * built, so a variant stays fixed for as long as the dialog is open.
 */
public final class Responsive {

    private Responsive() {
    }

    /**
     * Adds the variant only in portrait. Pairs with a {@link #landscape} sibling of the same slot;
     * when neither matches, the slot is empty rather than duplicated.
     */
    public static Ui.TableBuilder portrait(Ui.TableBuilder parent, java.util.function.Consumer<Ui.TableBuilder> body) {
        parent.add(Ui.table(t -> {
            t.layout(l -> l.condition("portrait").growX());
            body.accept(t);
        }));
        parent.row();
        return parent;
    }

    /** Adds the variant only in landscape. */
    public static Ui.TableBuilder landscape(Ui.TableBuilder parent, java.util.function.Consumer<Ui.TableBuilder> body) {
        parent.add(Ui.table(t -> {
            t.layout(l -> l.condition("landscape").growX());
            body.accept(t);
        }));
        parent.row();
        return parent;
    }

    /** Adds the variant only when the scaled scene is at least {@code minLogicalWidth} wide. */
    public static Ui.TableBuilder atLeast(Ui.TableBuilder parent, float minLogicalWidth,
                                           java.util.function.Consumer<Ui.TableBuilder> body) {
        parent.add(Ui.table(t -> {
            t.layout(l -> l.condition("width >= " + format(minLogicalWidth)).growX());
            body.accept(t);
        }));
        parent.row();
        return parent;
    }

    /**
     * Renders whole thresholds without a trailing {@code .0}.
     *
     * <p>The condition is parsed client-side by {@code Strings.parseFloat}, so this only affects how
     * the tree reads in a debug dump, not how it behaves.
     */
    private static String format(float value) {
        if (value == Math.rint(value) && !Float.isInfinite(value)) {
            return Long.toString((long) value);
        }
        return Float.toString(value);
    }
}