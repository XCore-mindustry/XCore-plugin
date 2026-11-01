package com.ospx.flubundle.compiler;

import com.ospx.flubundle.LocaleCodes;
import com.ospx.flubundle.MessageVariables;
import fluent.syntax.ast.Entry;
import fluent.syntax.ast.Message;
import fluent.syntax.parser.FTLParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Compares every locale of a bundle directory with a base locale.
 *
 * <ul>
 *     <li><b>error</b>: a translation uses a {@code $variable} the base locale message does not
 *     use — code written against the base locale will not pass it, so it renders as an error;</li>
 *     <li><b>warning</b>: a translation drops a variable of the base message;</li>
 *     <li><b>warning</b>: a key exists only in the translation (orphan);</li>
 *     <li><b>warning</b> (optional): a base key is not translated and falls back to the base locale.</li>
 * </ul>
 *
 * <pre>{@code
 * LocaleConsistencyChecker.check(Path.of("src/main/resources/bundles"), "en").assertSuccess();
 * }</pre>
 */
public final class LocaleConsistencyChecker {

    private final String baseLocale;
    private final boolean reportMissingKeys;

    private LocaleConsistencyChecker(String baseLocale, boolean reportMissingKeys) {
        this.baseLocale = Objects.requireNonNull(LocaleCodes.normalize(baseLocale), "baseLocale must not be blank");
        this.reportMissingKeys = reportMissingKeys;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Checks a bundle directory against the base locale, reporting missing translations as warnings.
     */
    public static CompilationResult check(Path directory, String baseLocale) {
        return builder().baseLocale(baseLocale).build().check(directory);
    }

    public CompilationResult check(Path directory) {
        var diagnostics = new ArrayList<Diagnostic>();
        var locales = load(directory, diagnostics);
        var keysByFile = new LinkedHashMap<Path, Set<String>>();
        int messages = 0;
        int files = 0;

        for (var locale : locales.values()) {
            messages += locale.messages.size();
            files += locale.files.size();
            locale.keysByFile.forEach(keysByFile::put);
        }

        var base = locales.get(baseLocale);
        if (base == null) {
            diagnostics.add(Diagnostic.error(directory, 0, null, "No bundle files for base locale '" + baseLocale + "'"));
            return new CompilationResult(diagnostics, files, messages, keysByFile);
        }

        for (var locale : locales.values()) {
            if (locale != base) {
                compare(base, locale, diagnostics);
            }
        }

        return new CompilationResult(diagnostics, files, messages, keysByFile);
    }

    public static void main(String[] args) {
        if (args == null || args.length == 0) {
            System.err.println("Usage: LocaleConsistencyChecker <path-to-bundles> [base-locale]");
            System.exit(1);
        }

        CompilationResult result = check(Path.of(args[0]), args.length > 1 ? args[1] : "en");
        System.out.println(result.formatReport());
        if (result.hasErrors()) {
            System.exit(1);
        }
    }

    private void compare(LocaleMessages base, LocaleMessages translation, List<Diagnostic> diagnostics) {
        if (reportMissingKeys) {
            var missing = new ArrayList<String>();
            for (var key : base.messages.keySet()) {
                if (!translation.messages.containsKey(key)) {
                    missing.add(key);
                }
            }
            if (!missing.isEmpty()) {
                diagnostics.add(Diagnostic.warning(translation.files.getFirst(), 0, null,
                        missing.size() + " key(s) missing, falling back to '" + baseLocale + "': " + summarize(missing)));
            }
        }

        for (var entry : translation.messages.entrySet()) {
            var key = entry.getKey();
            var file = translation.fileOf.get(key);
            var line = translation.lineOf.getOrDefault(key, 0);

            if (!base.messages.containsKey(key)) {
                diagnostics.add(Diagnostic.warning(file, line, key,
                        "Key does not exist in base locale '" + baseLocale + "'"));
                continue;
            }

            var expected = base.variables(key);
            var actual = translation.variables(key);

            var unexpected = new LinkedHashSet<>(actual);
            unexpected.removeAll(expected);
            if (!unexpected.isEmpty()) {
                diagnostics.add(Diagnostic.error(file, line, key,
                        "Uses " + variables(unexpected) + " not used by base locale '" + baseLocale
                                + "' (expected " + variables(expected) + ")"));
            }

            var dropped = new LinkedHashSet<>(expected);
            dropped.removeAll(actual);
            if (!dropped.isEmpty()) {
                diagnostics.add(Diagnostic.warning(file, line, key,
                        "Does not use " + variables(dropped) + " from base locale '" + baseLocale + "'"));
            }
        }
    }

    private static Map<String, LocaleMessages> load(Path directory, List<Diagnostic> diagnostics) {
        var locales = new TreeMap<String, LocaleMessages>();
        List<Path> files;
        try (var stream = Files.walk(directory)) {
            files = stream.filter(f -> f.toString().endsWith(".ftl")).sorted().toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan directory: " + directory, e);
        }

        for (var file : files) {
            var name = file.getFileName().toString();
            var code = LocaleCodes.normalize(LocaleCodes.fromFileName(name.substring(0, name.length() - ".ftl".length())));
            if (code == null) {
                diagnostics.add(Diagnostic.warning(file, 0, null, "Could not parse locale from file name"));
                continue;
            }

            String content;
            try {
                content = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                diagnostics.add(Diagnostic.error(file, 0, null, "Failed to read file: " + e.getMessage()));
                continue;
            }

            locales.computeIfAbsent(code, c -> new LocaleMessages()).add(file, content);
        }
        return locales;
    }

    private static String variables(Set<String> names) {
        var joined = new ArrayList<String>();
        for (var name : names) {
            joined.add("$" + name);
        }
        return String.join(", ", joined);
    }

    private static String summarize(List<String> keys) {
        return keys.size() <= 10
                ? String.join(", ", keys)
                : String.join(", ", keys.subList(0, 10)) + ", ...";
    }

    private static final class LocaleMessages {
        final Map<String, Message> messages = new LinkedHashMap<>();
        final Map<String, Path> fileOf = new LinkedHashMap<>();
        final Map<String, Integer> lineOf = new LinkedHashMap<>();
        final Map<Path, Set<String>> keysByFile = new LinkedHashMap<>();
        final List<Path> files = new ArrayList<>();

        void add(Path file, String content) {
            files.add(file);
            var lines = FtlCompiler.indexMessageLines(content);
            var keys = new LinkedHashSet<String>();

            for (Entry entry : FTLParser.parse(content).entries()) {
                if (entry instanceof Message message) {
                    var key = message.identifier().name();
                    keys.add(key);
                    messages.put(key, message);
                    fileOf.put(key, file);
                    lineOf.put(key, lines.getOrDefault(key, 0));
                }
            }
            keysByFile.put(file, keys);
        }

        Set<String> variables(String key) {
            return MessageVariables.of(messages.get(key), messages::get);
        }
    }

    public static final class Builder {
        private String baseLocale = "en";
        private boolean reportMissingKeys = true;

        public Builder baseLocale(String baseLocale) {
            this.baseLocale = baseLocale;
            return this;
        }

        /**
         * Whether untranslated base keys are reported as one warning per locale. Defaults to {@code true}.
         */
        public Builder reportMissingKeys(boolean reportMissingKeys) {
            this.reportMissingKeys = reportMissingKeys;
            return this;
        }

        public LocaleConsistencyChecker build() {
            return new LocaleConsistencyChecker(baseLocale, reportMissingKeys);
        }
    }
}
