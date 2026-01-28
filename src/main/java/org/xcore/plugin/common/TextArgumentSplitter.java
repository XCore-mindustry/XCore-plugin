package org.xcore.plugin.common;

import java.util.ArrayList;
import java.util.List;

/**
 * A utility class for splitting a string into command arguments.
 * <p>
 * Features:
 * - Supports double quotes (") for arguments with spaces.
 * - Supports escaping (\) for quotes and backslashes.
 * - Handles empty quoted strings ("") correctly as empty arguments.
 * - Robust: malformed input (e.g., unterminated quotes) does not throw exceptions.
 */
public final class TextArgumentSplitter {

    private TextArgumentSplitter() {}

    /**
     * Splits the raw command string into parsed arguments.
     *
     * @param text The input string (e.g., 'kick "Player Name" "Spamming chat"').
     * @return An array of parsed arguments. Returns empty array if input is null/blank.
     */
    public static String[] split(String text) {
        if (text == null || text.isBlank()) {
            return new String[0];
        }

        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();

        boolean inQuote = false;
        boolean escaped = false;
        boolean hasToken = false; // Tracks if we are currently building a token (even an empty one)

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // 1. Handle Escaping
            if (escaped) {
                currentToken.append(c);
                escaped = false;
                hasToken = true;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                // We don't set hasToken = true here yet, because a trailing backslash
                // shouldn't necessarily create a token if it's the only char (though rare case).
                // Actually, standard CLI usually treats a backslash as a start of a token sequence.
                hasToken = true;
                continue;
            }

            // 2. Handle Quotes
            if (c == '"') {
                inQuote = !inQuote;
                hasToken = true; // Seeing a quote means we have a token, e.g. "" is an empty token
                continue;
            }

            // 3. Handle Whitespace
            if (Character.isWhitespace(c)) {
                if (inQuote) {
                    currentToken.append(c);
                    hasToken = true;
                } else {
                    // Whitespace outside quotes marks the end of a token
                    if (hasToken) {
                        tokens.add(currentToken.toString());
                        currentToken.setLength(0);
                        hasToken = false;
                    }
                }
                continue;
            }

            // 4. Normal Characters
            currentToken.append(c);
            hasToken = true;
        }

        // Add the final token if one exists
        if (hasToken) {
            tokens.add(currentToken.toString());
        }

        return tokens.toArray(new String[0]);
    }
}