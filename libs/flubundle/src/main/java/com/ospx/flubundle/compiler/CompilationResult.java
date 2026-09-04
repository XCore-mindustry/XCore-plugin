package com.ospx.flubundle.compiler;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record CompilationResult(
        List<Diagnostic> diagnostics,
        int filesCount,
        int messagesCount,
        Map<Path, Set<String>> messageKeysByFile
) {
    public CompilationResult {
        diagnostics = Collections.unmodifiableList(diagnostics);
        messageKeysByFile = Collections.unmodifiableMap(new LinkedHashMap<>(messageKeysByFile));
    }

    public CompilationResult(List<Diagnostic> diagnostics, int filesCount, int messagesCount) {
        this(diagnostics, filesCount, messagesCount, Collections.emptyMap());
    }

    public Set<String> messageKeysForFile(Path file) {
        if (messageKeysByFile.containsKey(file)) {
            return messageKeysByFile.get(file);
        }
        for (Map.Entry<Path, Set<String>> entry : messageKeysByFile.entrySet()) {
            if (entry.getKey().getFileName().equals(file.getFileName())) {
                return entry.getValue();
            }
        }
        return Collections.emptySet();
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
