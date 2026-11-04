package com.ospx.flubundle.compiler;

import com.ospx.flubundle.LocaleCodes;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Every build-time check of a bundle directory in one call, for a plugin's test suite:
 * {@link FtlCompiler}, {@link LocaleConsistencyChecker} and, optionally, keys that given locales
 * must define.
 *
 * <pre>{@code
 * @Test
 * void bundles() {
 *     BundleChecks.of(Path.of("src/main/resources/bundles")).assertValid();
 * }
 *
 * BundleChecks.of(dir)
 *         .externalReferences("-xcore")                       // defined by another plugin
 *         .requireKeys(List.of("en", "ru"), captionKeys)       // must exist in these locales
 *         .assertValid();
 * }</pre>
 */
public final class BundleChecks {

    private final Path directory;
    private String baseLocale = "en";
    private final Set<String> externalReferences = new LinkedHashSet<>();
    private final Map<String, Set<String>> requiredKeys = new LinkedHashMap<>();

    private BundleChecks(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    public static BundleChecks of(Path directory) {
        return new BundleChecks(directory);
    }

    /** The locale translations are compared with; {@code en} by default. */
    public BundleChecks baseLocale(String baseLocale) {
        this.baseLocale = Objects.requireNonNull(baseLocale, "baseLocale");
        return this;
    }

    /** Ids messages may reference although these files do not define them: {@code message} or {@code -term}. */
    public BundleChecks externalReferences(String... ids) {
        externalReferences.addAll(List.of(ids));
        return this;
    }

    /** Keys each of {@code locales} must define in its own files, without falling back. */
    public BundleChecks requireKeys(Collection<String> locales, Collection<String> keys) {
        for (String locale : locales) {
            String code = Objects.requireNonNull(LocaleCodes.normalize(locale), "locale must not be blank");
            requiredKeys.computeIfAbsent(code, c -> new LinkedHashSet<>()).addAll(keys);
        }
        return this;
    }

    /**
     * @return the diagnostics of every check; untranslated keys are not reported, a translation
     *         falls back to the base locale for them
     */
    public CompilationResult check() {
        CompilationResult compiled = FtlCompiler.builder()
                .externalReferences(externalReferences.toArray(String[]::new))
                .build()
                .compilePaths(List.of(directory));
        CompilationResult consistency = LocaleConsistencyChecker.builder()
                .baseLocale(baseLocale)
                .reportMissingKeys(false)
                .build()
                .check(directory);

        List<Diagnostic> diagnostics = new ArrayList<>(compiled.diagnostics());
        diagnostics.addAll(consistency.diagnostics());
        diagnostics.addAll(missingRequiredKeys(compiled));
        return new CompilationResult(diagnostics, compiled.filesCount(), compiled.messagesCount(),
                compiled.messageKeysByFile());
    }

    /**
     * @throws FtlCompilationException with the full report when any check finds an error
     */
    public void assertValid() {
        check().assertSuccess();
    }

    private List<Diagnostic> missingRequiredKeys(CompilationResult compiled) {
        Map<String, Set<String>> keysByLocale = new LinkedHashMap<>();
        Map<String, Path> fileByLocale = new LinkedHashMap<>();
        compiled.messageKeysByFile().forEach((file, keys) -> {
            String name = file.getFileName().toString();
            String code = LocaleCodes.normalize(LocaleCodes.fromFileName(name.substring(0, name.length() - ".ftl".length())));
            if (code != null) {
                keysByLocale.computeIfAbsent(code, c -> new LinkedHashSet<>()).addAll(keys);
                fileByLocale.putIfAbsent(code, file);
            }
        });

        List<Diagnostic> diagnostics = new ArrayList<>();
        requiredKeys.forEach((locale, keys) -> {
            Set<String> missing = new LinkedHashSet<>(keys);
            missing.removeAll(keysByLocale.getOrDefault(locale, Set.of()));
            if (!missing.isEmpty()) {
                diagnostics.add(Diagnostic.error(fileByLocale.getOrDefault(locale, directory), 0, null,
                        "Locale '" + locale + "' is missing required key(s): " + String.join(", ", missing)));
            }
        });
        return diagnostics;
    }
}
