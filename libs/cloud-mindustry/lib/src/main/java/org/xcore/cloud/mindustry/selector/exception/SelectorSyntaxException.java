package org.xcore.cloud.mindustry.selector.exception;

public class SelectorSyntaxException extends IllegalArgumentException {
    private final String input;
    private final String reason;
    private final int cursor;

    public SelectorSyntaxException(String input, String reason, int cursor) {
        super(formatMessage(input, reason, cursor));
        this.input = input;
        this.reason = reason;
        this.cursor = cursor;
    }

    private static String formatMessage(String input, String reason, int cursor) {
        StringBuilder sb = new StringBuilder();
        sb.append(reason).append(" at position ").append(cursor).append(":\n");
        sb.append(input).append("\n");
        for (int i = 0; i < cursor && i < input.length(); i++) {
            sb.append(' ');
        }
        sb.append('^');
        return sb.toString();
    }

    public String input() {
        return input;
    }

    public String reason() {
        return reason;
    }

    public int cursor() {
        return cursor;
    }
}
