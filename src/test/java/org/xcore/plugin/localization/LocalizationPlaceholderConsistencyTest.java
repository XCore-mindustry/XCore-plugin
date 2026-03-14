package org.xcore.plugin.localization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.incendo.cloud.caption.StandardCaptionKeys;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class LocalizationPlaceholderConsistencyTest {

    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath();
    private static final Path BUNDLES_DIR = PROJECT_ROOT.resolve("src/main/resources/bundles");
    private static final Path JAVA_DIR = PROJECT_ROOT.resolve("src/main/java");
    private static final Set<String> REQUIRED_CLOUD_CAPTION_KEYS = StandardCaptionKeys.standardCaptionKeys().stream()
            .map(caption -> caption.key().replace('.', '-'))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    private static final Set<String> PRIMARY_CLOUD_CAPTION_KEYS = Set.of(
            "argument-parse-failure-boolean",
            "argument-parse-failure-number",
            "argument-parse-failure-char",
            "argument-parse-failure-string",
            "argument-parse-failure-uuid",
            "argument-parse-failure-enum",
            "argument-parse-failure-regex",
            "argument-parse-failure-flag-unknown",
            "argument-parse-failure-flag-duplicate-flag",
            "argument-parse-failure-flag-no-flag-started",
            "argument-parse-failure-flag-missing-argument",
            "argument-parse-failure-flag-no-permission",
            "argument-parse-failure-color",
            "argument-parse-failure-duration",
            "argument-parse-failure-aggregate-missing",
            "argument-parse-failure-aggregate-failure",
            "argument-parse-failure-either",
            "exception-unexpected",
            "exception-invalid-argument",
            "exception-no-such-command",
            "exception-no-permission",
            "exception-invalid-sender",
            "exception-invalid-sender-list",
            "exception-invalid-syntax"
    );

    private static final Pattern BUNDLE_KEY_PATTERN = Pattern.compile("^([a-z][a-z0-9-]*)\\s*=");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\s*\\$([A-Za-z0-9_-]+)");
    private static final Pattern LOCALIZATION_CALL_PATTERN = Pattern.compile(
            "(?:\\.locale\\(\\)\\.(?:send|format|t)|\\blocal\\.(?:send|format|t)|\\bsystemLocal\\.(?:send|format|t)|\\bbundleService\\.format|\\bsessionService\\.broadcast)\\s*\\("
    );

    @Test
    @DisplayName("bundle placeholder sets stay consistent across locales")
    void bundlePlaceholderSetsStayConsistentAcrossLocales() throws IOException {
        var bundles = loadBundlePlaceholders();
        var allKeys = bundles.values().stream()
                .flatMap(map -> map.keySet().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        var mismatches = new ArrayList<String>();
        for (var key : allKeys) {
            Map<String, Set<String>> perLocale = new LinkedHashMap<>();
            for (var entry : bundles.entrySet()) {
                var placeholders = entry.getValue().get(key);
                if (placeholders != null) {
                    perLocale.put(entry.getKey(), placeholders);
                }
            }

            if (perLocale.size() <= 1) {
                continue;
            }

            var distinctSets = new LinkedHashSet<>(perLocale.values());
            if (distinctSets.size() > 1) {
                mismatches.add(key + " -> " + perLocale.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(Collectors.joining(", ")));
            }
        }

        assertThat(mismatches)
                .withFailMessage("Found locale placeholder mismatches:%n%s", String.join(System.lineSeparator(), mismatches))
                .isEmpty();
    }

    @Test
    @DisplayName("java localization calls provide required placeholders")
    void javaLocalizationCallsProvideRequiredPlaceholders() throws IOException {
        var bundles = loadBundlePlaceholders();
        var expectedByKey = canonicalPlaceholdersByKey(bundles);
        var mismatches = new ArrayList<String>();

        try (var files = Files.walk(JAVA_DIR)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(path -> mismatches.addAll(findJavaCallMismatches(path, expectedByKey)));
        }

        assertThat(mismatches)
                .withFailMessage("Found Java/bundle placeholder mismatches:%n%s", String.join(System.lineSeparator(), mismatches))
                .isEmpty();
    }

    @Test
    @DisplayName("primary locales cover all standard cloud caption keys")
    void primaryLocalesCoverAllStandardCloudCaptionKeys() throws IOException {
        var bundles = loadBundlePlaceholders();
        var primaryLocales = List.of("bundle_en.ftl", "bundle_ru.ftl", "bundle_uk_UA.ftl");
        var missing = new ArrayList<String>();

        for (var locale : primaryLocales) {
            var keys = bundles.get(locale);
            assertThat(keys)
                    .withFailMessage("Bundle file %s was not loaded", locale)
                    .isNotNull();

            var missingKeys = new LinkedHashSet<>(PRIMARY_CLOUD_CAPTION_KEYS);
            missingKeys.removeAll(keys.keySet());
            if (!missingKeys.isEmpty()) {
                missing.add(locale + " -> " + missingKeys);
            }
        }

        assertThat(missing)
                .withFailMessage("Primary locale bundles are missing standard cloud caption keys:%n%s",
                        String.join(System.lineSeparator(), missing))
                .isEmpty();
    }

    private static Map<String, Map<String, Set<String>>> loadBundlePlaceholders() throws IOException {
        var bundles = new LinkedHashMap<String, Map<String, Set<String>>>();

        try (var files = Files.list(BUNDLES_DIR)) {
            for (var path : files.filter(file -> file.toString().endsWith(".ftl"))
                    .sorted(Comparator.naturalOrder())
                    .toList()) {
                bundles.put(path.getFileName().toString(), parseBundlePlaceholders(path));
            }
        }

        return bundles;
    }

    private static Map<String, Set<String>> parseBundlePlaceholders(Path path) {
        try {
            var lines = Files.readAllLines(path);
            var result = new LinkedHashMap<String, Set<String>>();
            String currentKey = null;
            StringBuilder currentValue = null;

            for (var line : lines) {
                if (!line.isBlank() && !Character.isWhitespace(line.charAt(0))) {
                    if (currentKey != null) {
                        result.put(currentKey, extractPlaceholders(currentValue.toString()));
                    }

                    Matcher matcher = BUNDLE_KEY_PATTERN.matcher(line);
                    if (matcher.find()) {
                        currentKey = matcher.group(1);
                        currentValue = new StringBuilder(line.substring(line.indexOf('=') + 1));
                    } else {
                        currentKey = null;
                        currentValue = null;
                    }
                    continue;
                }

                if (currentValue != null) {
                    currentValue.append('\n').append(line);
                }
            }

            if (currentKey != null) {
                result.put(currentKey, extractPlaceholders(currentValue.toString()));
            }

            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse bundle file " + path, e);
        }
    }

    private static Set<String> extractPlaceholders(String value) {
        var placeholders = new LinkedHashSet<String>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);
        while (matcher.find()) {
            placeholders.add(matcher.group(1));
        }
        return placeholders;
    }

    private static Map<String, Set<String>> canonicalPlaceholdersByKey(Map<String, Map<String, Set<String>>> bundles) {
        var canonical = new LinkedHashMap<String, Set<String>>();

        for (var bundle : bundles.values()) {
            for (var entry : bundle.entrySet()) {
                canonical.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }

        return canonical;
    }

    private static List<String> findJavaCallMismatches(Path path, Map<String, Set<String>> expectedByKey) {
        try {
            var source = Files.readString(path);
            var mismatches = new ArrayList<String>();
            Matcher matcher = LOCALIZATION_CALL_PATTERN.matcher(source);
            while (matcher.find()) {
                int openParen = source.indexOf('(', matcher.start());
                int closeParen = findMatchingDelimiter(source, openParen, '(', ')');
                if (openParen < 0 || closeParen < 0) {
                    continue;
                }

                var invocation = source.substring(openParen + 1, closeParen);
                var arguments = splitTopLevel(invocation);
                if (arguments.isEmpty()) {
                    continue;
                }

                var key = parseStringLiteral(arguments.getFirst());
                if (key == null) {
                    continue;
                }

                var expected = expectedByKey.get(key);
                if (expected == null || expected.isEmpty()) {
                    continue;
                }

                var provided = arguments.size() > 1 ? parseArgsCall(arguments.get(1)) : Set.<String>of();
                if (provided == null) {
                    continue;
                }

                var missing = new LinkedHashSet<>(expected);
                missing.removeAll(provided);
                if (!missing.isEmpty()) {
                    mismatches.add(PROJECT_ROOT.relativize(path)
                            + ":" + lineNumber(source, matcher.start())
                            + " key=" + key
                            + " missing=" + missing
                            + " provided=" + provided
                            + " expected=" + expected);
                }
            }
            return mismatches;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to inspect Java source " + path, e);
        }
    }

    private static Set<String> parseArgsCall(String expression) {
        var trimmed = expression.trim();
        if (!trimmed.startsWith("args(")) {
            return null;
        }

        int openParen = trimmed.indexOf('(');
        int closeParen = findMatchingDelimiter(trimmed, openParen, '(', ')');
        if (closeParen != trimmed.length() - 1) {
            return null;
        }

        var tokens = splitTopLevel(trimmed.substring(openParen + 1, closeParen));
        var keys = new LinkedHashSet<String>();
        for (int i = 0; i < tokens.size(); i += 2) {
            var key = parseStringLiteral(tokens.get(i));
            if (key == null) {
                return null;
            }
            keys.add(key);
        }
        return keys;
    }

    private static List<String> splitTopLevel(String text) {
        var parts = new ArrayList<String>();
        int start = 0;
        int parenDepth = 0;
        int bracketDepth = 0;
        int braceDepth = 0;
        int angleDepth = 0;
        boolean inString = false;
        boolean escaping = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaping) {
                    escaping = false;
                } else if (c == '\\') {
                    escaping = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            switch (c) {
                case '"' -> inString = true;
                case '(' -> parenDepth++;
                case ')' -> parenDepth--;
                case '[' -> bracketDepth++;
                case ']' -> bracketDepth--;
                case '{' -> braceDepth++;
                case '}' -> braceDepth--;
                case '<' -> angleDepth++;
                case '>' -> angleDepth--;
                case ',' -> {
                    if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0 && angleDepth == 0) {
                        parts.add(text.substring(start, i).trim());
                        start = i + 1;
                    }
                }
                default -> {
                }
            }
        }

        var tail = text.substring(start).trim();
        if (!tail.isEmpty()) {
            parts.add(tail);
        }
        return parts;
    }

    private static int findMatchingDelimiter(String text, int openIndex, char open, char close) {
        int depth = 0;
        boolean inString = false;
        boolean escaping = false;

        for (int i = openIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaping) {
                    escaping = false;
                } else if (c == '\\') {
                    escaping = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }

        return -1;
    }

    private static String parseStringLiteral(String token) {
        var trimmed = token.trim();
        if (trimmed.length() < 2 || trimmed.charAt(0) != '"' || trimmed.charAt(trimmed.length() - 1) != '"') {
            return null;
        }
        return trimmed.substring(1, trimmed.length() - 1);
    }

    private static int lineNumber(String text, int index) {
        int line = 1;
        for (int i = 0; i < index; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
