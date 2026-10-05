package org.xcore.cloud.mindustry.selector.engine;

import io.leangen.geantyref.TypeToken;
import org.incendo.cloud.Command;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.ParsingContext;
import org.incendo.cloud.key.CloudKey;
import org.xcore.cloud.mindustry.selector.SelectorKind;
import org.xcore.cloud.mindustry.selector.SelectorRestrictions;
import org.xcore.cloud.mindustry.selector.TargetSelectorSpec;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

/**
 * Enforces the command-level {@code @DenySelectors} / {@code @AllowedSelectors} restrictions.
 * <p>
 * The command is only known once parsing has finished, so parsers record every selector they
 * accept with {@link #checkGuard} and the manager's postprocessor checks them all.
 */
public final class SelectorGuard {

    public static final CloudKey<Boolean> DENY_SELECTORS_KEY =
            CloudKey.of("mindustry:deny_selectors", Boolean.class);
    public static final CloudKey<String> DENY_REASON_KEY =
            CloudKey.of("mindustry:deny_selectors_reason", String.class);
    public static final CloudKey<SelectorKind[]> ALLOWED_SELECTORS_KEY =
            CloudKey.of("mindustry:allowed_selectors", SelectorKind[].class);
    /**
     * @deprecated only holds the last parsed selector, which may belong to a branch the parser
     * abandoned; use {@link #selectorSpecs(Command, CommandContext)}
     */
    @Deprecated
    public static final CloudKey<TargetSelectorSpec> LAST_SELECTOR_SPEC_KEY =
            CloudKey.of("mindustry:last_selector_spec", TargetSelectorSpec.class);

    /** Every recorded selector, keyed by the parsing attempt that produced it. */
    private static final CloudKey<Map<Object, TargetSelectorSpec>> SPECS_KEY =
            CloudKey.of("mindustry:selector_specs", new TypeToken<Map<Object, TargetSelectorSpec>>() {});
    /** Key for selectors parsed outside a command tree, where there is no parsing context. */
    private static final Object NO_PARSING_CONTEXT = new Object();

    private SelectorGuard() {}

    /**
     * Records {@code spec}, accepted for the argument being parsed, for the command-level check
     * that runs after parsing.
     */
    public static void checkGuard(CommandContext<?> context, TargetSelectorSpec spec) {
        if (spec == null || spec.kind() == SelectorKind.LITERAL_PLAYER) {
            return;
        }

        Map<Object, TargetSelectorSpec> specs = context.getOrDefault(SPECS_KEY, null);
        if (specs == null) {
            specs = new IdentityHashMap<>(4);
            context.store(SPECS_KEY, specs);
        }
        List<? extends ParsingContext<?>> attempts = context.parsingContexts();
        specs.put(attempts.isEmpty() ? NO_PARSING_CONTEXT : attempts.get(attempts.size() - 1), spec);
        context.store(LAST_SELECTOR_SPEC_KEY, spec);
    }

    /**
     * The selectors that {@code cmd}'s arguments were parsed from.
     * <p>
     * Cloud tries sibling branches with the same context, so a selector may have been recorded by
     * a branch that was abandoned. Parsing is depth-first and stops at the first match, so the last
     * attempt at each of {@code cmd}'s arguments is the one that produced its value.
     */
    public static List<TargetSelectorSpec> selectorSpecs(Command<?> cmd, CommandContext<?> context) {
        Map<Object, TargetSelectorSpec> specs = context.getOrDefault(SPECS_KEY, null);
        if (specs == null || specs.isEmpty()) {
            return List.of();
        }

        List<TargetSelectorSpec> result = new ArrayList<>(specs.size());
        TargetSelectorSpec detached = specs.get(NO_PARSING_CONTEXT);
        if (detached != null) {
            result.add(detached);
        }

        List<? extends ParsingContext<?>> attempts = context.parsingContexts();
        for (CommandComponent<?> component : cmd.components()) {
            ListIterator<? extends ParsingContext<?>> it = attempts.listIterator(attempts.size());
            while (it.hasPrevious()) {
                ParsingContext<?> attempt = it.previous();
                if (attempt.component().name().equals(component.name())) {
                    TargetSelectorSpec spec = specs.get(attempt);
                    if (spec != null) {
                        result.add(spec);
                    }
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Checks every selector {@code cmd}'s arguments were parsed from against its restrictions.
     */
    public static void enforceAll(Command<?> cmd, CommandContext<?> context) {
        List<TargetSelectorSpec> specs = selectorSpecs(cmd, context);
        if (specs.isEmpty()) {
            return;
        }
        SelectorRestrictions restrictions = SelectorRestrictions.from(cmd.commandMeta());
        for (TargetSelectorSpec spec : specs) {
            restrictions.enforce(spec);
        }
    }

    public static void enforce(Command<?> cmd, TargetSelectorSpec spec) {
        SelectorRestrictions.from(cmd.commandMeta()).enforce(spec);
    }
}
