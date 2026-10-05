package org.xcore.cloud.mindustry.selector;

import io.leangen.geantyref.TypeToken;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.incendo.cloud.meta.CommandMeta;
import org.incendo.cloud.parser.ParserParameter;
import org.incendo.cloud.parser.ParserParameters;
import org.xcore.cloud.mindustry.selector.engine.SelectorGuard;
import org.xcore.cloud.mindustry.selector.exception.SelectorDeniedException;

import java.util.EnumSet;
import java.util.Set;

/**
 * Which selector kinds an argument (or a whole command) accepts. Literal player names and
 * {@code #id}s are never restricted.
 *
 * @param allowed    the permitted kinds, or {@code null} for every kind
 * @param denyReason when non-null, every selector is refused with this reason
 */
public record SelectorRestrictions(@Nullable Set<SelectorKind> allowed, @Nullable String denyReason) {

    /** Parser parameter set by a parameter-level {@code @DenySelectors}: the denial reason. */
    public static final ParserParameter<String> DENY_PARAMETER =
            new ParserParameter<>("mindustry:deny_selectors", TypeToken.get(String.class));
    /** Parser parameter set by a parameter-level {@code @AllowedSelectors}. */
    public static final ParserParameter<SelectorKind[]> ALLOWED_PARAMETER =
            new ParserParameter<>("mindustry:allowed_selectors", TypeToken.get(SelectorKind[].class));

    public static final SelectorRestrictions NONE = new SelectorRestrictions(null, null);

    public SelectorRestrictions {
        allowed = allowed == null ? null : Set.copyOf(allowed);
    }

    public static @NonNull SelectorRestrictions deny(@NonNull String reason) {
        return new SelectorRestrictions(null, reason);
    }

    public static @NonNull SelectorRestrictions allow(@NonNull SelectorKind... kinds) {
        Set<SelectorKind> set = EnumSet.noneOf(SelectorKind.class);
        set.addAll(java.util.Arrays.asList(kinds));
        return new SelectorRestrictions(set, null);
    }

    public static @NonNull SelectorRestrictions from(@NonNull ParserParameters parameters) {
        String reason = parameters.get(DENY_PARAMETER, null);
        if (reason != null) {
            return deny(reason);
        }
        SelectorKind[] kinds = parameters.get(ALLOWED_PARAMETER, null);
        return kinds == null ? NONE : allow(kinds);
    }

    public static @NonNull SelectorRestrictions from(@NonNull CommandMeta meta) {
        if (meta.getOrDefault(SelectorGuard.DENY_SELECTORS_KEY, false)) {
            return deny(meta.getOrDefault(SelectorGuard.DENY_REASON_KEY, "Target selectors are forbidden for this command."));
        }
        SelectorKind[] kinds = meta.getOrDefault(SelectorGuard.ALLOWED_SELECTORS_KEY, null);
        return kinds == null ? NONE : allow(kinds);
    }

    /**
     * @throws SelectorDeniedException when {@code spec} uses a selector these restrictions refuse
     */
    public void enforce(@Nullable TargetSelectorSpec spec) {
        if (spec == null || spec.kind() == SelectorKind.LITERAL_PLAYER) {
            return;
        }
        if (denyReason != null) {
            throw new SelectorDeniedException(denyReason);
        }
        if (allowed != null && !allowed.contains(spec.kind())) {
            throw new SelectorDeniedException("Selector '" + spec.kind().token() + "' is not allowed for this command.");
        }
    }
}
