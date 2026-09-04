package com.ospx.flubundle.compiler;

import fluent.bundle.FluentResource;
import fluent.syntax.ast.Attribute;
import fluent.syntax.ast.CallArguments;
import fluent.syntax.ast.Entry;
import fluent.syntax.ast.Expression;
import fluent.syntax.ast.InlineExpression;
import fluent.syntax.ast.Junk;
import fluent.syntax.ast.Message;
import fluent.syntax.ast.NamedArgument;
import fluent.syntax.ast.Pattern;
import fluent.syntax.ast.PatternElement;
import fluent.syntax.ast.SelectExpression;
import fluent.syntax.ast.Term;
import fluent.syntax.ast.Variant;
import fluent.syntax.parser.FTLParseException;
import fluent.syntax.parser.FTLParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;

public class FtlCompiler {

    private static final java.util.regex.Pattern MESSAGE_START_PATTERN =
            java.util.regex.Pattern.compile("^([a-zA-Z0-9_-]+)\\s*=");

    private final FunctionCatalog functionCatalog;
    private final boolean strictPluralDefaults;

    public FtlCompiler(FunctionCatalog functionCatalog, boolean strictPluralDefaults) {
        this.functionCatalog = Objects.requireNonNull(functionCatalog, "functionCatalog must not be null");
        this.strictPluralDefaults = strictPluralDefaults;
    }

    public static FtlCompiler createDefault() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CompilationResult compile(Path... paths) {
        return createDefault().compilePaths(Arrays.asList(paths));
    }

    public static CompilationResult compile(Collection<Path> paths) {
        return createDefault().compilePaths(paths);
    }

    public static void check(Path path) {
        compile(path).assertSuccess();
    }

    public CompilationResult compilePaths(Collection<Path> paths) {
        List<Path> allFtlFiles = new ArrayList<>();
        for (Path p : paths) {
            if (Files.isDirectory(p)) {
                try (var stream = Files.walk(p)) {
                    stream.filter(f -> f.toString().endsWith(".ftl"))
                            .sorted()
                            .forEach(allFtlFiles::add);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to scan directory: " + p, e);
                }
            } else if (Files.isRegularFile(p) && p.toString().endsWith(".ftl")) {
                allFtlFiles.add(p);
            }
        }

        List<Diagnostic> diagnostics = new ArrayList<>();
        Map<Path, Set<String>> messageKeysByFile = new LinkedHashMap<>();
        int totalMessages = 0;

        for (Path file : allFtlFiles) {
            Set<String> keys = new LinkedHashSet<>();
            totalMessages += compileFile(file, diagnostics, keys);
            messageKeysByFile.put(file, keys);
        }

        return new CompilationResult(diagnostics, allFtlFiles.size(), totalMessages, messageKeysByFile);
    }

    private int compileFile(Path file, List<Diagnostic> diagnostics, Set<String> collectedKeys) {
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            diagnostics.add(Diagnostic.error(file, 0, null, "Failed to read file: " + e.getMessage()));
            return 0;
        }

        Map<String, Integer> lineByMessageId = indexMessageLines(content);
        FluentResource resource = FTLParser.parse(content, FTLParser.ParseOptions.EXTENDED);

        for (FTLParseException parseError : resource.errors()) {
            diagnostics.add(Diagnostic.error(file, parseError.line(), null, "Syntax error: " + parseError.getMessage()));
        }

        for (Junk junk : resource.junk()) {
            int junkLine = findApproximateLine(content, junk.content());
            String summary = summarizeJunk(junk.content());
            diagnostics.add(Diagnostic.error(file, junkLine, null, "Unparsed junk in FTL: " + summary));
        }

        int messageCount = 0;
        for (Entry entry : resource.entries()) {
            if (entry instanceof Message msg) {
                messageCount++;
                String msgId = msg.identifier().name();
                collectedKeys.add(msgId);
                int line = lineByMessageId.getOrDefault(msgId, 0);

                if (msg.pattern() != null) {
                    validatePattern(msg.pattern(), file, line, msgId, diagnostics);
                }

                for (Attribute attr : msg.attributes()) {
                    if (attr.pattern() != null) {
                        validatePattern(attr.pattern(), file, line, msgId + "." + attr.identifier().name(), diagnostics);
                    }
                }
            } else if (entry instanceof Term term) {
                String termId = "-" + term.identifier().name();
                int line = lineByMessageId.getOrDefault(termId, 0);

                if (term.value() != null) {
                    validatePattern(term.value(), file, line, termId, diagnostics);
                }

                for (Attribute attr : term.attributes()) {
                    if (attr.pattern() != null) {
                        validatePattern(attr.pattern(), file, line, termId + "." + attr.identifier().name(), diagnostics);
                    }
                }
            }
        }

        return messageCount;
    }

    private void validatePattern(Pattern pattern, Path file, int line, String messageId, List<Diagnostic> diagnostics) {
        for (PatternElement element : pattern.elements()) {
            if (element instanceof PatternElement.Placeable placeable) {
                validateExpression(placeable.expression(), file, line, messageId, diagnostics);
            }
        }
    }

    private void validateExpression(Expression expr, Path file, int line, String messageId, List<Diagnostic> diagnostics) {
        if (expr instanceof SelectExpression select) {
            validateExpression(select.selector(), file, line, messageId, diagnostics);

            boolean hasDefault = false;
            boolean hasPluralCategories = false;
            boolean hasOtherVariant = false;

            for (Variant variant : select.variants()) {
                if (variant.isDefault()) {
                    hasDefault = true;
                }
                String keyName = variant.key().name();
                if (keyName.equals("one") || keyName.equals("few") || keyName.equals("many") || keyName.equals("two") || keyName.equals("zero")) {
                    hasPluralCategories = true;
                }
                if (keyName.equals("other")) {
                    hasOtherVariant = true;
                }
                validatePattern(variant.pattern(), file, line, messageId, diagnostics);
            }

            if (!hasDefault) {
                diagnostics.add(Diagnostic.error(file, line, messageId,
                        "Select expression is missing a default variant marked with '*'"));
            }

            if (strictPluralDefaults && hasPluralCategories && !hasOtherVariant) {
                diagnostics.add(Diagnostic.error(file, line, messageId,
                        "Plural select expression using CLDR categories must include a fallback '*[other]' variant"));
            }

        } else if (expr instanceof InlineExpression.FunctionReference fnRef) {
            String fnName = fnRef.name();

            if (!functionCatalog.contains(fnName)) {
                if (!functionCatalog.isAllowUnknownFunctions()) {
                    String suggestion = functionCatalog.findSimilar(fnName)
                            .map(s -> " Did you mean '" + s + "'?")
                            .orElse("");
                    diagnostics.add(Diagnostic.error(file, line, messageId,
                            "Unknown function '" + fnName + "()'." + suggestion));
                }
            } else {
                FunctionSpec spec = functionCatalog.get(fnName).orElse(null);
                if (spec != null) {
                    CallArguments args = fnRef.arguments();
                    int posCount = (args != null && args.positionals() != null) ? args.positionals().size() : 0;

                    if (posCount < spec.minPositionals() || posCount > spec.maxPositionals()) {
                        if (spec.minPositionals() == spec.maxPositionals()) {
                            diagnostics.add(Diagnostic.error(file, line, messageId,
                                    String.format("%s() expects %d positional argument(s), but got %d",
                                            fnName, spec.minPositionals(), posCount)));
                        } else {
                            diagnostics.add(Diagnostic.error(file, line, messageId,
                                    String.format("%s() expects between %d and %d positional argument(s), but got %d",
                                            fnName, spec.minPositionals(), spec.maxPositionals(), posCount)));
                        }
                    }

                    if (args != null && args.positionals() != null) {
                        for (Expression posExpr : args.positionals()) {
                            validateExpression(posExpr, file, line, messageId, diagnostics);
                        }
                    }

                    if (args != null && args.named() != null) {
                        for (NamedArgument named : args.named()) {
                            String optName = named.name().name();
                            Optional<OptionSpec> optSpec = spec.findOption(optName);

                            if (optSpec.isEmpty()) {
                                if (!spec.allowExtraOptions()) {
                                    String suggestion = Levenshtein.findClosest(optName, spec.options().keySet(), 2)
                                            .map(s -> " Did you mean '" + s + "'?")
                                            .orElse("");
                                    diagnostics.add(Diagnostic.error(file, line, messageId,
                                            String.format("Unknown option '%s' in call to %s().%s Allowed options: %s",
                                                    optName, fnName, suggestion, spec.options().keySet())));
                                }
                            } else {
                                optSpec.get().validate(fnName, optName, named.value(), diagnostics, file, line, messageId);
                            }
                        }
                    }
                }
            }

        } else if (expr instanceof PatternElement.Placeable placeable) {
            validateExpression(placeable.expression(), file, line, messageId, diagnostics);

        } else if (expr instanceof InlineExpression.TermReference termRef) {
            for (NamedArgument named : termRef.namedArguments()) {
                // Term argument patterns can be validated if needed
            }
        }
    }

    private static Map<String, Integer> indexMessageLines(String content) {
        Map<String, Integer> map = new HashMap<>();
        String[] lines = content.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher matcher = MESSAGE_START_PATTERN.matcher(line);
            if (matcher.find()) {
                map.put(matcher.group(1), i + 1);
            }
        }
        return map;
    }

    private static int findApproximateLine(String content, String target) {
        int idx = content.indexOf(target);
        if (idx < 0) {
            return 0;
        }
        int line = 1;
        for (int i = 0; i < idx; i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static String summarizeJunk(String junk) {
        String trimmed = junk.trim().replaceAll("\\s+", " ");
        if (trimmed.length() > 50) {
            return "'" + trimmed.substring(0, 47) + "...'";
        }
        return "'" + trimmed + "'";
    }

    public static void main(String[] args) {
        if (args == null || args.length == 0) {
            System.err.println("Usage: FtlCompiler <path-to-bundles> [additional-paths...]");
            System.exit(1);
        }

        List<Path> paths = Arrays.stream(args).map(Path::of).toList();
        FtlCompiler compiler = createDefault();
        CompilationResult result = compiler.compilePaths(paths);

        System.out.println(result.formatReport());
        if (result.hasErrors()) {
            System.exit(1);
        }
    }

    public static class Builder {
        private FunctionCatalog catalog = FunctionCatalog.defaults();
        private boolean strictPluralDefaults = true;

        public Builder functionCatalog(FunctionCatalog catalog) {
            this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
            return this;
        }

        public Builder strictPluralDefaults(boolean strict) {
            this.strictPluralDefaults = strict;
            return this;
        }

        public FtlCompiler build() {
            return new FtlCompiler(catalog, strictPluralDefaults);
        }
    }
}
