package org.xcore.cloud.mindustry.selector.engine;

import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.key.CloudKey;
import org.xcore.cloud.mindustry.selector.SelectorKind;
import org.xcore.cloud.mindustry.selector.TargetSelectorSpec;
import org.xcore.cloud.mindustry.selector.exception.SelectorDeniedException;

public final class SelectorGuard {

    public static final CloudKey<Boolean> DENY_SELECTORS_KEY =
            CloudKey.of("mindustry:deny_selectors", Boolean.class);
    public static final CloudKey<String> DENY_REASON_KEY =
            CloudKey.of("mindustry:deny_selectors_reason", String.class);
    public static final CloudKey<SelectorKind[]> ALLOWED_SELECTORS_KEY =
            CloudKey.of("mindustry:allowed_selectors", SelectorKind[].class);
    public static final CloudKey<TargetSelectorSpec> LAST_SELECTOR_SPEC_KEY =
            CloudKey.of("mindustry:last_selector_spec", TargetSelectorSpec.class);

    private SelectorGuard() {}

    public static void checkGuard(CommandContext<?> context, TargetSelectorSpec spec) {
        if (spec == null || spec.kind() == SelectorKind.LITERAL_PLAYER) {
            return;
        }

        context.store(LAST_SELECTOR_SPEC_KEY, spec);

        Command<?> cmd = null;
        try {
            cmd = context.command();
        } catch (IllegalStateException ignored) {
            // Command is not yet bound during early parse phase; preprocessor will enforce guard.
        }

        if (cmd != null) {
            enforce(cmd, spec);
        }
    }

    public static void enforce(Command<?> cmd, TargetSelectorSpec spec) {
        if (spec == null || spec.kind() == SelectorKind.LITERAL_PLAYER) {
            return;
        }

        boolean deny = cmd.commandMeta().getOrDefault(DENY_SELECTORS_KEY, false);
        if (deny) {
            String reason = cmd.commandMeta().getOrDefault(DENY_REASON_KEY, "Target selectors are forbidden for this command.");
            throw new SelectorDeniedException(reason);
        }

        SelectorKind[] allowed = cmd.commandMeta().getOrDefault(ALLOWED_SELECTORS_KEY, null);
        if (allowed != null) {
            boolean permitted = false;
            for (SelectorKind k : allowed) {
                if (k == spec.kind()) {
                    permitted = true;
                    break;
                }
            }
            if (!permitted) {
                throw new SelectorDeniedException("Selector '" + spec.kind().token() + "' is not allowed for this command.");
            }
        }
    }
}
