package org.xcore.plugin.cases;

import arc.util.Strings;

public final class CaseText {
    private CaseText() {}

    public static String plain(String value, int limit) {
        String plain = Strings.stripColors(value == null ? "" : value)
                .replaceAll("[\\p{Cntrl}]", " ").strip();
        return plain.length() <= limit ? plain : plain.substring(0, limit);
    }

    public static String name(String value) {
        String plain = plain(value, 64);
        return plain.isEmpty() ? "Player" : plain;
    }
}
