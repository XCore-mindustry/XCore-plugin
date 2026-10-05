package org.xcore.cloud.mindustry.selector.engine;

import io.leangen.geantyref.TypeToken;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.key.CloudKey;
import org.xcore.cloud.mindustry.selector.SelectorKind;
import org.xcore.cloud.mindustry.selector.SelectorRestrictions;
import org.xcore.cloud.mindustry.selector.TargetSelectorSpec;

import java.util.ArrayList;
import java.util.List;

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
    /** Every non-literal selector parsed for the current command, in input order. */
    public static final CloudKey<List<TargetSelectorSpec>> SELECTOR_SPECS_KEY =
            CloudKey.of("mindustry:selector_specs", new TypeToken<List<TargetSelectorSpec>>() {});
    /**
     * @deprecated only holds the last parsed selector; use {@link #SELECTOR_SPECS_KEY}
     */
    @Deprecated
    public static final CloudKey<TargetSelectorSpec> LAST_SELECTOR_SPEC_KEY =
            CloudKey.of("mindustry:last_selector_spec", TargetSelectorSpec.class);

    private SelectorGuard() {}

    /**
     * Records {@code spec} for the command-level check that runs after parsing.
     */
    public static void checkGuard(CommandContext<?> context, TargetSelectorSpec spec) {
        if (spec == null || spec.kind() == SelectorKind.LITERAL_PLAYER) {
            return;
        }

        List<TargetSelectorSpec> specs = context.getOrDefault(SELECTOR_SPECS_KEY, null);
        if (specs == null) {
            specs = new ArrayList<>(2);
            context.store(SELECTOR_SPECS_KEY, specs);
        }
        specs.add(spec);
        context.store(LAST_SELECTOR_SPEC_KEY, spec);
    }

    /**
     * Checks every selector recorded in {@code context} against the restrictions of {@code cmd}.
     */
    public static void enforceAll(Command<?> cmd, CommandContext<?> context) {
        List<TargetSelectorSpec> specs = context.getOrDefault(SELECTOR_SPECS_KEY, null);
        if (specs == null || specs.isEmpty()) {
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
