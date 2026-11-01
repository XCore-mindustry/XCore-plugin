package org.xcore.plugin.service.map.inspect;

/** Stable public codes with bounded, plain diagnostic text. */
public record MapFinding(String code, Severity severity, String detail, Integer x, Integer y, Integer count) {
    public enum Severity { ERROR, WARNING, INFO }

    public MapFinding { detail = safe(detail); }

    public static String safe(String text) {
        if (text == null) return null;
        StringBuilder result = new StringBuilder();
        // Strip Mindustry color markup, control characters and directional formatting.
        boolean markup = false;
        int lastClosingBracket = text.lastIndexOf(']');
        for (int i = 0; i < text.length() && result.length() < 300;) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (markup) { if (cp == ']') markup = false; continue; }
            if (cp == '[' && i <= lastClosingBracket) { markup = true; continue; }
            if (Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT) cp = ' ';
            if (result.length() + Character.charCount(cp) <= 300) result.appendCodePoint(cp);
        }
        return result.toString();
    }
}
