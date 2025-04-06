package org.xcore.plugin.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * A utility class for splitting a string into arguments, respecting quoted sections
 * and handling escape characters (\).
 */
public final class TextArgumentSplitter {
    private TextArgumentSplitter() {}

    /**
     * Splits the input text into an array of arguments based on shell-like quoting and escaping rules.
     * Handles quoted sections and escapes (\", \\).
     *
     * @param text The input string to split. Cannot be null.
     * @return An array of strings representing the arguments. Returns an empty array if the input is empty or contains only whitespace.
     * @throws IllegalArgumentException if the input string contains an unterminated quote.
     */
    public static String[] split(final String text) {
        if (text == null) {
            throw new IllegalArgumentException("Input text cannot be null");
        }

        final List<String> arguments = new ArrayList<>();
        final StringBuilder currentArgument = new StringBuilder();
        boolean insideQuotes = false;
        boolean escaped = false; // Tracks if the previous character was '\'

        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);

            if (escaped) {
                // Previous char was '\', append current char literally
                currentArgument.append(c);
                escaped = false;
            } else if (c == '\\') {
                // Found escape character, mark for next iteration
                escaped = true;
                // Don't append the backslash itself yet
            } else if (c == '"') {
                // Toggle quote state; don't append the quote char itself
                insideQuotes = !insideQuotes;
            } else if (Character.isWhitespace(c) && !insideQuotes) {
                // Whitespace outside quotes: finalize current argument if non-empty
                if (currentArgument.length() > 0) {
                    arguments.add(currentArgument.toString());
                    currentArgument.setLength(0);
                }
                // Skip the whitespace character
            } else {
                // Regular character, or whitespace inside quotes: append it
                currentArgument.append(c);
            }
        }

        // Check for errors after parsing
        if (insideQuotes) {
            throw new IllegalArgumentException("Unterminated quote in input string: " + text);
        }

        // Handle a dangling backslash at the very end of the input
        if (escaped) {
            currentArgument.append('\\'); // Treat dangling backslash literally
        }

        // Add the last argument if it exists
        if (currentArgument.length() > 0) {
            arguments.add(currentArgument.toString());
        }

        return arguments.toArray(new String[0]);
    }
}
