package com.ospx.flubundle.compiler;

import java.util.Collections;
import java.util.List;

public record CompilationResult(
        List<Diagnostic> diagnostics,
        int filesCount,
        int messagesCount
) {
    public CompilationResult {
        diagnostics = Collections.unmodifiableList(diagnostics);
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.level() == DiagnosticLevel.ERROR);
    }

    public boolean hasWarnings() {
        return diagnostics.stream().anyMatch(d -> d.level() == DiagnosticLevel.WARNING);
    }

    public List<Diagnostic> errors() {
        return diagnostics.stream().filter(d -> d.level() == DiagnosticLevel.ERROR).toList();
    }

    public List<Diagnostic> warnings() {
        return diagnostics.stream().filter(d -> d.level() == DiagnosticLevel.WARNING).toList();
    }

    public void assertSuccess() {
        if (hasErrors()) {
            throw new FtlCompilationException(formatReport(), this);
        }
    }

    public String formatReport() {
        StringBuilder sb = new StringBuilder();
        int errCount = errors().size();
        int warnCount = warnings().size();

        sb.append(String.format("FTL Compilation Report: %d error(s), %d warning(s) across %d file(s) and %d message(s):\n",
                errCount, warnCount, filesCount, messagesCount));

        for (Diagnostic d : diagnostics) {
            sb.append("  ").append(d).append("\n");
        }

        return sb.toString();
    }
}
