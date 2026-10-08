package com.ospx.flubundle.mindustry;

import arc.util.Strings;

/**
 * Mindustry color markup helpers for text that comes from players or other untrusted sources.
 *
 * <p>Mindustry reads {@code [name]} and {@code [#rrggbb]} as color tags and {@code [[} as a literal
 * bracket. A chat message, sign text or ban reason passed as a message argument can therefore
 * recolor the rest of the message or close its color with {@code []}. Escape such values before
 * passing them, or use {@code ESCAPE()} in the FTL file:
 *
 * <pre>{@code
 * Args.of("message", Markup.escape(chatText))
 * report = [scarlet]{ $reporter }[] reported: { ESCAPE($message) }
 * }</pre>
 *
 * <p>Do not escape values that are meant to carry colors, such as {@code player.coloredName()}.
 */
public final class Markup {

    private Markup() {
    }

    /** Doubles every {@code [} so the text renders literally and cannot open or close color tags. */
    public static String escape(String text) {
        if (text == null || text.indexOf('[') < 0) {
            return text;
        }
        return text.replace("[", "[[");
    }

    /** Removes color tags, keeping the visible text. */
    public static String strip(String text) {
        return text == null ? null : Strings.stripColors(text);
    }
}
