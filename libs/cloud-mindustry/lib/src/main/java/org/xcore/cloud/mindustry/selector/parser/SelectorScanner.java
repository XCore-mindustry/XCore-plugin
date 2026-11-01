package org.xcore.cloud.mindustry.selector.parser;

import org.xcore.cloud.mindustry.selector.exception.SelectorSyntaxException;

public final class SelectorScanner {
    private final CharSequence source;
    private final int length;
    private int cursor;

    public SelectorScanner(CharSequence source) {
        this.source = source;
        this.length = source.length();
        this.cursor = 0;
    }

    public char peek() {
        return cursor < length ? source.charAt(cursor) : '\0';
    }

    public char peek(int offset) {
        int idx = cursor + offset;
        return (idx >= 0 && idx < length) ? source.charAt(idx) : '\0';
    }

    public char next() {
        return cursor < length ? source.charAt(cursor++) : '\0';
    }

    public boolean hasRemaining() {
        return cursor < length;
    }

    public int cursor() {
        return cursor;
    }

    public void setCursor(int cursor) {
        this.cursor = cursor;
    }

    public void skipWhitespace() {
        while (cursor < length && source.charAt(cursor) <= ' ') {
            cursor++;
        }
    }

    public boolean expect(char expected) {
        skipWhitespace();
        if (peek() == expected) {
            cursor++;
            return true;
        }
        return false;
    }

    public void require(char expected, String message) {
        skipWhitespace();
        if (peek() != expected) {
            throw error(message);
        }
        cursor++;
    }

    public String parseIdentifier() {
        skipWhitespace();
        int start = cursor;
        while (cursor < length) {
            char c = source.charAt(cursor);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {
                cursor++;
            } else {
                break;
            }
        }
        if (cursor == start) {
            throw error("Expected identifier");
        }
        return source.subSequence(start, cursor).toString();
    }

    public int parseInt() {
        skipWhitespace();
        int sign = 1;
        if (peek() == '-') {
            sign = -1;
            cursor++;
        } else if (peek() == '+') {
            cursor++;
        }

        int start = cursor;
        int result = 0;
        while (cursor < length) {
            char c = source.charAt(cursor);
            if (c >= '0' && c <= '9') {
                result = result * 10 + (c - '0');
                cursor++;
            } else {
                break;
            }
        }
        if (cursor == start) {
            throw error("Expected integer digits");
        }
        return sign * result;
    }

    public float parseFloat() {
        skipWhitespace();
        int sign = 1;
        if (peek() == '-') {
            sign = -1;
            cursor++;
        } else if (peek() == '+') {
            cursor++;
        }

        int start = cursor;
        float intPart = 0f;
        while (cursor < length && source.charAt(cursor) >= '0' && source.charAt(cursor) <= '9') {
            intPart = intPart * 10f + (source.charAt(cursor++) - '0');
        }

        float fracPart = 0f;
        float divisor = 1f;
        if (peek() == '.' && peek(1) >= '0' && peek(1) <= '9') {
            cursor++; // consume '.'
            while (cursor < length && source.charAt(cursor) >= '0' && source.charAt(cursor) <= '9') {
                fracPart = fracPart * 10f + (source.charAt(cursor++) - '0');
                divisor *= 10f;
            }
        }

        if (cursor == start) {
            throw error("Expected numeric digits");
        }

        return sign * (intPart + (fracPart / divisor));
    }

    public String parseValue() {
        skipWhitespace();
        if (peek() == '"' || peek() == '\'') {
            return parseQuotedString();
        }

        int start = cursor;
        while (cursor < length) {
            char c = source.charAt(cursor);
            if (c == ',' || c == ']' || c <= ' ') {
                break;
            }
            cursor++;
        }
        if (cursor == start) {
            throw error("Expected value");
        }
        return source.subSequence(start, cursor).toString();
    }

    public String parseQuotedString() {
        char quote = next();
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;

        while (cursor < length) {
            char c = next();
            if (escaped) {
                sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == quote) {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        throw error("Unclosed quoted string");
    }

    public SelectorSyntaxException error(String message) {
        return new SelectorSyntaxException(source.toString(), message, cursor);
    }
}
