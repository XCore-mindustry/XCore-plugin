package org.xcore.plugin.utils;

import java.util.ArrayList;

public class TextArgumentSplitter {
    public static String[] split(String text) {
        ArrayList<String> arguments = new ArrayList<>();
        boolean insideQuotes = false;
        StringBuilder currentArgument = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '\"') {
                insideQuotes = !insideQuotes;
            } else if (Character.isWhitespace(c) && !insideQuotes) {
                if (!currentArgument.isEmpty()) {
                    arguments.add(currentArgument.toString());
                    currentArgument.setLength(0);
                }
            } else {
                currentArgument.append(c);
            }
        }

        if (!currentArgument.isEmpty()) {
            arguments.add(currentArgument.toString());
        }

        return arguments.toArray(new String[0]);
    }
}

