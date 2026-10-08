package com.ospx.flubundle;

import fluent.syntax.ast.Attribute;
import fluent.syntax.ast.CallArguments;
import fluent.syntax.ast.Expression;
import fluent.syntax.ast.InlineExpression;
import fluent.syntax.ast.Message;
import fluent.syntax.ast.Pattern;
import fluent.syntax.ast.PatternElement;
import fluent.syntax.ast.SelectExpression;
import fluent.syntax.ast.Variant;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * Collects the {@code $variables} a message expects from its caller.
 *
 * <p>Variables of messages referenced with {@code { other-message }} are included, because Fluent
 * passes the caller's arguments to them. Terms are skipped: they only see explicit term arguments.
 */
public final class MessageVariables {

    private MessageVariables() {
    }

    /**
     * @param message the message to inspect (value and all attributes)
     * @param lookup  resolves referenced message ids; may return {@code null}
     * @return variable names in order of first appearance
     */
    public static Set<String> of(Message message, Function<String, Message> lookup) {
        var variables = new LinkedHashSet<String>();
        collect(message, lookup, variables, new HashSet<>());
        return Collections.unmodifiableSet(variables);
    }

    private static void collect(Message message, Function<String, Message> lookup,
                                Set<String> variables, Set<String> visited) {
        if (message == null || !visited.add(message.identifier().name())) {
            return;
        }

        if (message.pattern() != null) {
            collect(message.pattern(), lookup, variables, visited);
        }
        for (Attribute attribute : message.attributes()) {
            collect(attribute.pattern(), lookup, variables, visited);
        }
    }

    private static void collect(Pattern pattern, Function<String, Message> lookup,
                                Set<String> variables, Set<String> visited) {
        for (PatternElement element : pattern.elements()) {
            if (element instanceof PatternElement.Placeable placeable) {
                collect(placeable.expression(), lookup, variables, visited);
            }
        }
    }

    private static void collect(Expression expression, Function<String, Message> lookup,
                                Set<String> variables, Set<String> visited) {
        switch (expression) {
            case InlineExpression.VariableReference variable -> variables.add(variable.identifier().name());
            case InlineExpression.MessageReference reference ->
                    collect(lookup.apply(reference.identifier().name()), lookup, variables, visited);
            case InlineExpression.FunctionReference function -> {
                CallArguments arguments = function.arguments();
                if (arguments != null && arguments.positionals() != null) {
                    for (Expression positional : arguments.positionals()) {
                        collect(positional, lookup, variables, visited);
                    }
                }
            }
            case PatternElement.Placeable placeable -> collect(placeable.expression(), lookup, variables, visited);
            case SelectExpression select -> {
                collect(select.selector(), lookup, variables, visited);
                for (Variant variant : select.variants()) {
                    collect(variant.pattern(), lookup, variables, visited);
                }
            }
            default -> {
            }
        }
    }
}
