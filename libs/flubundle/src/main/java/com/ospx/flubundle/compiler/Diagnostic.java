package com.ospx.flubundle.compiler;

import java.nio.file.Path;

public record Diagnostic(
        DiagnosticLevel level,
        Path file,
        int line,
        String messageId,
        String message
) {
    public static Diagnostic error(Path file, int line, String messageId, String message) {
        return new Diagnostic(DiagnosticLevel.ERROR, file, line, messageId, message);
    }

    public static Diagnostic warning(Path file, int line, String messageId, String message) {
        return new Diagnostic(DiagnosticLevel.WARNING, file, line, messageId, message);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(level).append("] ");
        if (file != null) {
            sb.append(file.getFileName());
        } else {
            sb.append("<unknown>");
        }
        if (line > 0) {
            sb.append(":").append(line);
        }
        if (messageId != null && !messageId.isBlank()) {
            sb.append(" [").append(messageId).append("]");
        }
        sb.append(": ").append(message);
        return sb.toString();
    }
}
