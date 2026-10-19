package org.xcore.plugin.ui.kit;

import arc.files.Fi;
import arc.util.io.Writes;
import com.ospx.flubundle.Bundle;
import mindustry.ui.builder.UiDslWriter;
import org.xcore.plugin.localization.Localization;
import org.xcore.ui.LocalizerResolver;
import org.xcore.ui.VButton;
import org.xcore.ui.VButtonTable;
import org.xcore.ui.VCheck;
import org.xcore.ui.VField;
import org.xcore.ui.VImage;
import org.xcore.ui.VLabel;
import org.xcore.ui.VNode;
import org.xcore.ui.VNodeCompiler;
import org.xcore.ui.VNodes;
import org.xcore.ui.VPane;
import org.xcore.ui.VTable;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What every menu has to hold to, checked without a client: the client neither shrinks a dialog
 * to its screen nor clips a text to its place, so a menu that passes here is one that cannot run
 * off the screen it was laid out for.
 */
public final class LayoutAssert {

    /** A dialog travels as one packet and the client reads a packet into a 32 KB buffer; this leaves a margin. */
    public static final int PACKET_LIMIT = 24_000;

    /** The languages whose bundles are complete; the others fall back to one of these. */
    public static final String[] LANGUAGES = {"ru", "uk", "en"};

    private LayoutAssert() {
    }

    /** The plugin's own texts in {@code language}, as a player with that language sees them. */
    public static Localization localization(String language) {
        Bundle.INSTANCE.addSource(new Fi("src/main/resources/bundles"));
        Bundle.INSTANCE.addLocaleAlias("uk", "uk_UA");
        return new Localization(Bundle.INSTANCE, Locale.forLanguageTag(language));
    }

    /**
     * Nothing in {@code window} is left to find its own width, nothing is wider than the screen
     * the window is for, and every text fits the place it is given.
     */
    public static void assertLaidOut(VNode window, Screen screen) {
        for (VNode node : VNodes.walk(window)) {
            Float width = node.layout() == null ? null : node.layout().width();

            if (node instanceof VLabel || node instanceof VButton || node instanceof VField
                    || node instanceof VButtonTable || node instanceof VPane || node instanceof VImage) {
                assertThat(width).as("width of %s", describe(node)).isNotNull();
            }
            if (width != null) {
                assertThat(width).as("%s on %s", describe(node), screen).isLessThanOrEqualTo(screen.width());
            }

            if (node instanceof VLabel label) {
                String text = label.text().resolve(null);
                assertThat(TextWidth.undrawable(text)).as("signs of \"%s\" the client's font lacks", text).isEmpty();
                float needed = label.wrap() ? widestWord(text) : TextWidth.of(text);
                assertThat(needed).as("\"%s\" in a label of %s on %s", text, width, screen)
                        .isLessThanOrEqualTo(width + 0.5f);
            }
            if (node instanceof VButton button) {
                String text = button.text().resolve(null);
                assertThat(TextWidth.undrawable(text)).as("signs of \"%s\" the client's font lacks", text).isEmpty();
                assertThat(TextWidth.of(text) + Kit.BUTTON_INSET)
                        .as("\"%s\" on a button of %s on %s", text, width, screen)
                        .isLessThanOrEqualTo(width + 0.5f);
            }

            assertThat(node instanceof VTable table && Boolean.TRUE.equals(table.wrap()))
                    .as("a wrap table asks for the width of all its cells in one row").isFalse();
            assertThat(node instanceof VCheck).as("a check box cannot wrap its text").isFalse();
        }
    }

    /** A wrapped label breaks between words only, so its widest word is the least it can be. */
    private static float widestWord(String text) {
        float widest = 0f;
        for (String word : text.split("\\s+")) {
            widest = Math.max(widest, TextWidth.of(word));
        }
        return widest;
    }

    public static void assertFitsPacket(VNode root, Object what) {
        assertThat(packetSize(root)).as("packet of %s", what).isLessThan(PACKET_LIMIT);
    }

    /** Bytes the dialog takes on the wire. */
    public static int packetSize(VNode root) {
        var bytes = new ByteArrayOutputStream();
        try (var writes = new Writes(new DataOutputStream(bytes))) {
            new VNodeCompiler(LocalizerResolver.IDENTITY).compile(root).write(writes);
        }
        return bytes.size();
    }

    /** The texts of every label and button, in the order they are drawn. */
    public static List<String> texts(VNode root) {
        List<String> texts = new ArrayList<>();
        for (VNode node : VNodes.walk(root)) {
            switch (node) {
                case VLabel label -> texts.add(label.text().resolve(null));
                case VButton button -> texts.add(button.text().resolve(null));
                default -> {
                }
            }
        }
        return texts;
    }

    /** All the texts as one string, to look for a phrase in. */
    public static String allText(VNode root) {
        return String.join("\n", texts(root));
    }

    /** The actions a press can send. */
    public static List<String> actions(VNode root) {
        List<String> actions = new ArrayList<>();
        for (VNode node : VNodes.walk(root)) {
            switch (node) {
                case VButton button -> actions.add(button.clicked());
                case VButtonTable button -> actions.add(button.clicked());
                default -> {
                }
            }
        }
        return actions;
    }

    /** The tree as the client's own DSL, for a look at what was sent. */
    public static String dsl(VNode root) {
        return UiDslWriter.write(new VNodeCompiler(LocalizerResolver.IDENTITY).compile(root));
    }

    private static String describe(VNode node) {
        return switch (node) {
            case VLabel label -> "label \"" + label.text().resolve(null) + "\"";
            case VButton button -> "button \"" + button.text().resolve(null) + "\"";
            default -> node.getClass().getSimpleName() + " " + node.id();
        };
    }
}
