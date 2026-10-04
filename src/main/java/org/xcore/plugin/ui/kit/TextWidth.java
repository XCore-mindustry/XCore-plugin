package org.xcore.plugin.ui.kit;

/**
 * How wide the client draws a line of text, in its own UI units.
 *
 * <p>The client neither shrinks nor clips a label: a line longer than its place runs over what
 * is next to it. Knowing the width here is what lets a menu decide how many buttons a row holds
 * and where a name has to be cut, instead of leaving it to luck and the length of a translation.
 */
public final class TextWidth {

    private static final String ELLIPSIS = "…";

    private TextWidth() {
    }

    /** Width of the widest line of {@code markup}; colour tags take no room. */
    public static float of(String markup) {
        if (markup == null || markup.isEmpty()) return 0f;
        float widest = 0f;
        float line = 0f;
        int i = 0;
        while (i < markup.length()) {
            int tag = tagLength(markup, i);
            if (tag > 0) {
                i += tag;
                continue;
            }
            char c = markup.charAt(i);
            if (c == '\n') {
                widest = Math.max(widest, line);
                line = 0f;
            } else {
                line += FontWidths.of(c);
            }
            // "[[" draws one bracket.
            i += tag < 0 ? 2 : 1;
        }
        return Math.max(widest, line);
    }

    /**
     * {@code markup} cut to one line no wider than {@code width}, with an ellipsis where it was
     * cut. Colour tags are kept, so the part that stays looks as it did, and the colours still
     * open at the cut are closed, so they do not run into what follows.
     */
    public static String fit(String markup, float width) {
        if (markup == null || markup.isEmpty()) return "";
        String line = markup.replace('\n', ' ').replace('\r', ' ');
        if (of(line) <= width) return line;

        float room = width - FontWidths.of(ELLIPSIS.charAt(0));
        StringBuilder kept = new StringBuilder();
        float used = 0f;
        int open = 0;
        int i = 0;
        while (i < line.length()) {
            int tag = tagLength(line, i);
            if (tag > 0) {
                open = tag == 2 ? Math.max(0, open - 1) : open + 1;
                kept.append(line, i, i + tag);
                i += tag;
                continue;
            }
            float advance = FontWidths.of(line.charAt(i));
            if (used + advance > room) break;
            used += advance;
            int step = tag < 0 ? 2 : 1;
            kept.append(line, i, i + step);
            i += step;
        }
        return kept.toString().stripTrailing() + ELLIPSIS + "[]".repeat(open);
    }

    /**
     * The signs of {@code markup} the client's font has no glyph for. The client draws nothing in
     * their place, so an arrow or a tick that is not in the font is simply not there.
     */
    public static String undrawable(String markup) {
        if (markup == null) return "";
        StringBuilder missing = new StringBuilder();
        for (int i = 0; i < markup.length(); i++) {
            char c = markup.charAt(i);
            if (!FontWidths.has(c) && missing.indexOf(String.valueOf(c)) < 0) {
                missing.append(c);
            }
        }
        return missing.toString();
    }

    /**
     * Length of the colour tag that starts at {@code at}; 0 when there is none there, and -1 for
     * the escape {@code [[}.
     */
    private static int tagLength(String text, int at) {
        if (text.charAt(at) != '[') return 0;
        if (at + 1 >= text.length()) return 0;
        char next = text.charAt(at + 1);
        if (next == '[') return -1;
        if (next == ']') return 2;
        int end = at + 1;
        boolean hex = next == '#';
        if (hex) end++;
        while (end < text.length() && text.charAt(end) != ']') {
            char c = text.charAt(end);
            boolean allowed = hex
                    ? Character.digit(c, 16) >= 0
                    : (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
            if (!allowed) return 0;
            end++;
        }
        if (end >= text.length() || end == at + 1 + (hex ? 1 : 0)) return 0;
        return end - at + 1;
    }
}
