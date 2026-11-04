package org.xcore.cloud.mindustry.parser;

import arc.util.Strings;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.context.CommandInput;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Reads a player name that may contain spaces from command input. Mindustry names often do, while
 * Cloud splits input on spaces.
 * <ol>
 *     <li>{@code "Some Name"}: everything up to the closing quote;</li>
 *     <li>otherwise the longest run of words, up to {@value #MAX_WORDS}, that is one of the known
 *     names (see {@link #matches}), so {@code /tp Some Name} works without quotes when such a
 *     player exists;</li>
 *     <li>otherwise one word, as before.</li>
 * </ol>
 */
public final class PlayerNameInput {

    /** The most words a name without quotes is matched over. */
    public static final int MAX_WORDS = 8;

    private PlayerNameInput() {
    }

    /**
     * @param names the names a run of words may match; compared as {@link #matches} does
     * @return the name as typed (spacing kept), without quotes; the input is left after it
     */
    public static @NonNull String read(@NonNull CommandInput input, @NonNull Iterable<String> names) {
        int start = input.cursor();
        String quoted = readQuoted(input, start);
        if (quoted != null) {
            return quoted;
        }

        Set<String> known = new HashSet<>();
        for (String name : names) {
            if (name != null) {
                known.add(normalize(name));
            }
        }

        input.readString();
        int end = input.cursor();
        for (int words = 2; words <= MAX_WORDS && input.hasRemainingInput(); words++) {
            input.readString();
            if (known.contains(normalize(typed(input, start, input.cursor())))) {
                end = input.cursor();
            }
        }

        input.cursor(end);
        return typed(input, start, end);
    }

    /**
     * Whether {@code typed} names the player called {@code name}: case-insensitively, without color
     * tags, and ignoring leading, trailing and repeated spaces.
     */
    public static boolean matches(@NonNull String name, @NonNull String typed) {
        return normalize(name).equals(normalize(typed));
    }

    /**
     * @return what is between the quotes, or {@code null} (input untouched) when the input does not
     *         start with a closed quote
     */
    private static String readQuoted(CommandInput input, int start) {
        if (!input.hasRemainingInput() || !input.peekString().startsWith("\"")) {
            return null;
        }

        while (input.hasRemainingInput()) {
            String word = input.readString();
            String text = typed(input, start, input.cursor());
            if (text.length() > 1 && word.endsWith("\"")) {
                return text.substring(1, text.length() - 1);
            }
        }

        input.cursor(start);
        return null;
    }

    private static String typed(CommandInput input, int start, int end) {
        return input.input().substring(start, end).strip();
    }

    static String normalize(String name) {
        return Strings.stripColors(name).strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
