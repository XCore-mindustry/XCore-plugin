package com.ospx.flubundle.compiler;

import com.ospx.flubundle.LocaleCodes;
import fluent.bundle.FluentResource;
import fluent.syntax.ast.Attribute;
import fluent.syntax.ast.CallArguments;
import fluent.syntax.ast.Entry;
import fluent.syntax.ast.Expression;
import fluent.syntax.ast.Identifier;
import fluent.syntax.ast.InlineExpression;
import fluent.syntax.ast.Message;
import fluent.syntax.ast.Pattern;
import fluent.syntax.ast.PatternElement;
import fluent.syntax.ast.SelectExpression;
import fluent.syntax.ast.Term;
import fluent.syntax.ast.Variant;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * Checks, per locale, that every {@code { message }}, {@code { message.attribute }} and
 * {@code { -term }} reference points at something the locale's files define, and reports ids
 * defined more than once. Files of one locale ({@code bundle_en.ftl}, {@code extra_en.ftl}) are
 * loaded into one bundle at runtime, so they see each other's messages.
 */
final class ReferenceChecker {

    private record Reference(Path file, int line, String fromId, String target, String attribute, boolean term) {
    }

    private record Definition(Path file, int line, Set<String> attributes) {
    }

    private static final class LocaleUnit {
        final Map<String, Definition> messages = new LinkedHashMap<>();
        final Map<String, Definition> terms = new LinkedHashMap<>();
        final List<Reference> references = new ArrayList<>();
    }

    private static final java.util.regex.Pattern ENTRY_START =
            java.util.regex.Pattern.compile("^(-?[a-zA-Z][a-zA-Z0-9_-]*)\\s*=");

    private final Map<String, LocaleUnit> locales = new LinkedHashMap<>();

    void add(Path file, String content, FluentResource resource, List<Diagnostic> diagnostics) {
        LocaleUnit unit = locales.computeIfAbsent(localeKey(file), key -> new LocaleUnit());
        Map<String, ArrayDeque<Integer>> lines = occurrenceLines(content);

        for (Entry entry : resource.entries()) {
            if (entry instanceof Message message) {
                String id = message.identifier().name();
                int line = nextLine(lines, id);
                define(unit.messages, id, id, file, line, message.attributes(), diagnostics);
                collect(message.pattern(), file, line, id, unit.references);
                for (Attribute attribute : message.attributes()) {
                    collect(attribute.pattern(), file, line, id + "." + attribute.identifier().name(), unit.references);
                }
            } else if (entry instanceof Term term) {
                String id = term.identifier().name();
                int line = nextLine(lines, "-" + id);
                define(unit.terms, id, "-" + id, file, line, term.attributes(), diagnostics);
                collect(term.value(), file, line, "-" + id, unit.references);
                for (Attribute attribute : term.attributes()) {
                    collect(attribute.pattern(), file, line, "-" + id + "." + attribute.identifier().name(), unit.references);
                }
            }
        }
    }

    /**
     * @param external ids defined outside the checked files (another plugin's bundle, for
     *                 example), written as they are referenced: {@code message} or {@code -term}
     */
    void check(Set<String> external, List<Diagnostic> diagnostics) {
        for (LocaleUnit unit : locales.values()) {
            for (Reference reference : unit.references) {
                String shown = (reference.term() ? "-" : "") + reference.target();
                if (external.contains(shown)) {
                    continue;
                }

                Map<String, Definition> scope = reference.term() ? unit.terms : unit.messages;
                Definition definition = scope.get(reference.target());
                if (definition == null) {
                    String suggestion = Levenshtein.findClosest(reference.target(), scope.keySet(), 2)
                            .map(s -> " Did you mean '" + (reference.term() ? "-" : "") + s + "'?")
                            .orElse("");
                    diagnostics.add(Diagnostic.error(reference.file(), reference.line(), reference.fromId(),
                            "Unknown " + (reference.term() ? "term" : "message") + " '" + shown + "'." + suggestion));
                } else if (reference.attribute() != null && !definition.attributes().contains(reference.attribute())) {
                    diagnostics.add(Diagnostic.error(reference.file(), reference.line(), reference.fromId(),
                            "'" + shown + "' has no attribute '" + reference.attribute() + "'"));
                }
            }
        }
    }

    private static void define(Map<String, Definition> scope, String id, String shown, Path file, int line,
                               List<Attribute> attributes, List<Diagnostic> diagnostics) {
        Set<String> names = new HashSet<>();
        for (Attribute attribute : attributes) {
            names.add(attribute.identifier().name());
        }

        Definition previous = scope.put(id, new Definition(file, line, names));
        if (previous != null) {
            String where = previous.file().equals(file)
                    ? "line " + previous.line()
                    : previous.file().getFileName() + ":" + previous.line();
            diagnostics.add(Diagnostic.warning(file, line, shown,
                    "'" + shown + "' is defined again; the definition at " + where + " is overridden"));
        }
    }

    private static void collect(Pattern pattern, Path file, int line, String fromId, List<Reference> out) {
        if (pattern == null) {
            return;
        }
        for (PatternElement element : pattern.elements()) {
            if (element instanceof PatternElement.Placeable placeable) {
                collect(placeable.expression(), file, line, fromId, out);
            }
        }
    }

    private static void collect(Expression expression, Path file, int line, String fromId, List<Reference> out) {
        switch (expression) {
            case InlineExpression.MessageReference reference ->
                    out.add(new Reference(file, line, fromId, reference.identifier().name(),
                            name(reference.attributeID()), false));
            case InlineExpression.TermReference reference ->
                    out.add(new Reference(file, line, fromId, reference.identifier().name(),
                            name(reference.attributeID()), true));
            case InlineExpression.FunctionReference function -> {
                CallArguments arguments = function.arguments();
                if (arguments != null && arguments.positionals() != null) {
                    for (Expression positional : arguments.positionals()) {
                        collect(positional, file, line, fromId, out);
                    }
                }
            }
            case PatternElement.Placeable placeable -> collect(placeable.expression(), file, line, fromId, out);
            case SelectExpression select -> {
                collect(select.selector(), file, line, fromId, out);
                for (Variant variant : select.variants()) {
                    collect(variant.pattern(), file, line, fromId, out);
                }
            }
            default -> {
            }
        }
    }

    /** The lines each id starts an entry on, in file order, so a repeated id gets its own line. */
    private static Map<String, ArrayDeque<Integer>> occurrenceLines(String content) {
        Map<String, ArrayDeque<Integer>> lines = new HashMap<>();
        String[] rows = content.split("\\R", -1);
        for (int i = 0; i < rows.length; i++) {
            Matcher matcher = ENTRY_START.matcher(rows[i]);
            if (matcher.find()) {
                lines.computeIfAbsent(matcher.group(1), id -> new ArrayDeque<>()).addLast(i + 1);
            }
        }
        return lines;
    }

    private static int nextLine(Map<String, ArrayDeque<Integer>> lines, String id) {
        ArrayDeque<Integer> queue = lines.get(id);
        Integer line = queue == null ? null : queue.pollFirst();
        return line == null ? 0 : line;
    }

    private static String name(Identifier identifier) {
        return identifier == null ? null : identifier.name();
    }

    /** Files of one locale share a bundle at runtime; files without a locale stand alone. */
    private static String localeKey(Path file) {
        String name = file.getFileName().toString();
        String base = name.endsWith(".ftl") ? name.substring(0, name.length() - ".ftl".length()) : name;
        String code = LocaleCodes.normalize(LocaleCodes.fromFileName(base));
        return code != null ? code : "file:" + file;
    }
}
