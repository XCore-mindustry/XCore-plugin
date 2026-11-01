package org.xcore.plugin.common;

import static arc.util.Strings.stripColors;
import static arc.util.Strings.stripGlyphs;

public final class TextUtils {
    private TextUtils() {
    }

    public static String stripFooCharacters(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        var builder = new StringBuilder(text);
        for (int i = text.length() - 1; i >= 0; i--) {
            char c = builder.charAt(i);
            if (c >= 0xF80 && c <= 0x107F) {
                builder.deleteCharAt(i);
            }
        }
        return builder.toString();
    }

    public static boolean deepEquals(String first, String second) {
        if (first == null || second == null) {
            return false;
        }

        first = stripColors(stripGlyphs(first));
        second = stripColors(stripGlyphs(second));

        return first.equalsIgnoreCase(second)
                || first.toLowerCase().contains(second.toLowerCase());
    }


    public static boolean equalsNonEmpty(String query, String name) {
        if (query == null || query.isEmpty() || name == null || name.isEmpty()) {
            return false;
        }
        return query.equals(name);
    }
}
