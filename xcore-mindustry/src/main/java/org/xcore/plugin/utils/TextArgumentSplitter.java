package org.xcore.plugin.utils;

import arc.struct.*;

public class TextArgumentSplitter {

    public static String[] split(String text) {
        Seq<String> arguments = new Seq<>();
        boolean insideQuotes = false;

        StringBuilder currentArgument = new StringBuilder();

        for(var c : text.toCharArray()){
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

        return arguments.toArray();
    }
}

