package org.xcore.testkit.render;

import arc.Core;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.ui.Button;
import arc.scene.ui.Dialog;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Walks a laid-out dialog and reports what a player would see broken: an element past the screen
 * or the dialog, a horizontal scroll, text wider or taller than its label, a label sticking out of
 * the button that owns it. Vertical overflow inside a vertical scroll pane is allowed.
 *
 * <p>The policy suits ordinary axis-aligned menus, not intentional horizontal scrollers, rotated
 * elements or decorative overflow.
 */
final class Inspector {

    /** A layout rounds to half units; less than this is not overflow. */
    private static final float EPS = 1.5f;

    /** One thing found wrong: what kind, and the element it was found on. */
    record Finding(String code, String element) {
    }

    final List<Finding> findings = new ArrayList<>();
    final List<String> tree = new ArrayList<>();
    private final Dialog dialog;

    Inspector(Dialog dialog) {
        this.dialog = dialog;
    }

    Inspector run() {
        inspect(dialog, false, 0);
        return this;
    }

    /** The positions and sizes of {@code element} and everything in it, to tell when the layout has settled. */
    static String geometry(Element element) {
        StringBuilder value = new StringBuilder().append(element.x).append(',').append(element.y)
                .append(',').append(element.getWidth()).append(',').append(element.getHeight());
        if (element instanceof Group group) {
            for (Element child : group.getChildren()) value.append('|').append(geometry(child));
        }
        return value.toString();
    }

    private void inspect(Element element, boolean verticalScroll, int depth) {
        if (!element.visible) return;
        Vec2 position = element.localToStageCoordinates(new Vec2());
        String text = element instanceof Label label ? label.getText().toString().replace('\n', ' ') : "";
        String id = element.getClass().getSimpleName() + "[" + element.name + "] "
                + text.substring(0, Math.min(100, text.length()));
        tree.add(" ".repeat(depth) + id + String.format(Locale.ROOT,
                " xy=%.1f,%.1f wh=%.1f,%.1f pref=%.1f,%.1f", position.x, position.y,
                element.getWidth(), element.getHeight(), element.getPrefWidth(), element.getPrefHeight()));

        Vec2 origin = dialog.localToStageCoordinates(new Vec2());
        if (position.x < -EPS || position.x + element.getWidth() > Core.scene.getWidth() + EPS)
            add("STAGE_X", id);
        if (!verticalScroll && (position.y < -EPS || position.y + element.getHeight() > Core.scene.getHeight() + EPS))
            add("STAGE_Y", id);
        if (position.x < origin.x - EPS || position.x + element.getWidth() > origin.x + dialog.getWidth() + EPS)
            add("DIALOG_X", id);
        if (!verticalScroll && (position.y < origin.y - EPS
                || position.y + element.getHeight() > origin.y + dialog.getHeight() + EPS))
            add("DIALOG_Y", id);
        if (element instanceof ScrollPane pane) {
            if (pane.isScrollX() && pane.getMaxX() > EPS) add("HSCROLL", id);
            if (pane.getWidget() != null && pane.getWidget().getWidth() > pane.getScrollWidth() + EPS)
                add("PANE_CONTENT_X", id);
        }
        if (element instanceof Label label) {
            var glyphs = label.getGlyphLayout();
            if (glyphs.width > label.getWidth() + EPS) add("TEXT_X", id);
            if (glyphs.height > label.getHeight() + EPS) add("TEXT_Y", id);
            // A label can fit its own size yet extend beyond the button that owns it.
            for (Element parent = label.parent; parent != null; parent = parent.parent) {
                if (parent instanceof Button) {
                    Vec2 relative = label.localToAscendantCoordinates(parent, new Vec2());
                    if (relative.x < -EPS || relative.x + label.getWidth() > parent.getWidth() + EPS
                            || relative.y < -EPS || relative.y + label.getHeight() > parent.getHeight() + EPS)
                        add("BUTTON_TEXT", id);
                    break;
                }
            }
        }
        if (element instanceof Group group) {
            boolean scroll = verticalScroll || element instanceof ScrollPane pane && pane.isScrollY();
            for (Element child : group.getChildren()) inspect(child, scroll, depth + 1);
        }
    }

    private void add(String code, String element) {
        findings.add(new Finding(code, element));
    }
}
