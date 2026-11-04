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
 *     names (compared case-insensitively, without color tags), so {@code /tp Some Name} works
 *     without quotes when such a player exists;</li>
 *     <li>otherwise one word, as before.</li>
 * </ol>
 */
public final class PlayerNameInput {

    /** The most words a name without quotes is matched over. */
    public static final int MAX_WORDS = 8;

    private PlayerNameInput() {
    }

    /**
     * @param names the names a run of words may match; color tags are ignored
     * @return the name, without quotes; the input is left after it
     */
    public static @NonNull String read(@NonNull CommandInput input, @NonNull Iterable<String> names) {
        String quoted = readQuoted(input);
        if (quoted != null) {
            return quoted;
        }

        Set<String> known = new HashSet<>();
        for (String name : names) {
            if (name != null) {
                known.add(normalize(name));
            }
        }

        String first = input.readString();
        int end = input.cursor();
        String best = first;

        StringBuilder run = new StringBuilder(first);
        for (int words = 2; words <= MAX_WORDS && input.hasRemainingInput(); words++) {
            run.append(' ').append(input.readString());
            if (known.contains(normalize(run.toString()))) {
                best = run.toString();
                end = input.cursor();
            }
        }

        input.cursor(end);
        return best;
    }

    /**
     * @return what is between the quotes, or {@code null} (input untouched) when the input does not
     *         start with a closed quote
     */
    private static String readQuoted(CommandInput input) {
        if (!input.hasRemainingInput() || !input.peekString().startsWith("\"")) {
            return null;
        }

        int start = input.cursor();
        StringBuilder text = new StringBuilder();
        while (input.hasRemainingInput()) {
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(input.readString());
            if (text.length() > 1 && text.charAt(text.length() - 1) == '"') {
                return text.substring(1, text.length() - 1);
            }
        }

        input.cursor(start);
        return null;
    }

    static String normalize(String name) {
        return Strings.stripColors(name).trim().toLowerCase(Locale.ROOT);
    }
}
