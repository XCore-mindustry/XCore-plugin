package org.xcore.cloud.mindustry.selector.parser;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.xcore.cloud.mindustry.selector.SelectorKind;
import org.xcore.cloud.mindustry.selector.SelectorRestrictions;
import org.xcore.cloud.mindustry.selector.TargetSelector.MultiplePlayerSelector;
import org.xcore.cloud.mindustry.selector.TargetSelector.MultipleUnitSelector;
import org.xcore.cloud.mindustry.selector.TargetSelector.SinglePlayerSelector;
import org.xcore.cloud.mindustry.selector.TargetSelector.SingleUnitSelector;
import org.xcore.cloud.mindustry.selector.TargetSelectorSpec;
import org.xcore.cloud.mindustry.selector.engine.SelectorGuard;
import org.xcore.cloud.mindustry.selector.engine.SpatialSelectorEngine;
import org.xcore.cloud.mindustry.selector.exception.SelectorDeniedException;
import org.xcore.cloud.mindustry.selector.exception.SelectorException;
import org.xcore.cloud.mindustry.selector.exception.SelectorParseException;
import org.xcore.cloud.mindustry.selector.exception.TooManyTargetsException;
import org.xcore.cloud.mindustry.selector.impl.MultiplePlayerSelectorImpl;
import org.xcore.cloud.mindustry.selector.impl.MultipleUnitSelectorImpl;
import org.xcore.cloud.mindustry.selector.impl.SinglePlayerSelectorImpl;
import org.xcore.cloud.mindustry.selector.impl.SingleUnitSelectorImpl;

public final class TargetSelectorParsers {

    private TargetSelectorParsers() {}

    public static <C> ParserDescriptor<C, SinglePlayerSelector> singlePlayerSelector(SpatialSelectorEngine engine) {
        return singlePlayerSelector(engine, SelectorRestrictions.NONE);
    }

    public static <C> ParserDescriptor<C, SinglePlayerSelector> singlePlayerSelector(SpatialSelectorEngine engine, SelectorRestrictions restrictions) {
        return ParserDescriptor.of(new SinglePlayerSelectorParser<>(engine, restrictions), SinglePlayerSelector.class);
    }

    public static <C> ParserDescriptor<C, MultiplePlayerSelector> multiplePlayerSelector(SpatialSelectorEngine engine) {
        return multiplePlayerSelector(engine, SelectorRestrictions.NONE);
    }

    public static <C> ParserDescriptor<C, MultiplePlayerSelector> multiplePlayerSelector(SpatialSelectorEngine engine, SelectorRestrictions restrictions) {
        return ParserDescriptor.of(new MultiplePlayerSelectorParser<>(engine, restrictions), MultiplePlayerSelector.class);
    }

    public static <C> ParserDescriptor<C, SingleUnitSelector> singleUnitSelector(SpatialSelectorEngine engine) {
        return singleUnitSelector(engine, SelectorRestrictions.NONE);
    }

    public static <C> ParserDescriptor<C, SingleUnitSelector> singleUnitSelector(SpatialSelectorEngine engine, SelectorRestrictions restrictions) {
        return ParserDescriptor.of(new SingleUnitSelectorParser<>(engine, restrictions), SingleUnitSelector.class);
    }

    public static <C> ParserDescriptor<C, MultipleUnitSelector> multipleUnitSelector(SpatialSelectorEngine engine) {
        return multipleUnitSelector(engine, SelectorRestrictions.NONE);
    }

    public static <C> ParserDescriptor<C, MultipleUnitSelector> multipleUnitSelector(SpatialSelectorEngine engine, SelectorRestrictions restrictions) {
        return ParserDescriptor.of(new MultipleUnitSelectorParser<>(engine, restrictions), MultipleUnitSelector.class);
    }

    /**
     * Shared parse flow: read one token, parse it into a spec, apply the argument's restrictions,
     * record it for the command-level guard, then let {@link #create} validate and build.
     * Selector failures are reported as {@link SelectorParseException}s.
     */
    abstract static class AbstractSelectorParser<C, T>
            implements ArgumentParser<C, T>, TargetSelectorSuggestionProvider<C> {

        protected final SpatialSelectorEngine engine;
        protected final SelectorRestrictions restrictions;

        AbstractSelectorParser(SpatialSelectorEngine engine, SelectorRestrictions restrictions) {
            this.engine = engine;
            this.restrictions = restrictions;
        }

        @Override
        public final @NonNull ArgumentParseResult<T> parse(
                @NonNull CommandContext<C> context,
                @NonNull CommandInput input
        ) {
            String token = input.readString();
            try {
                TargetSelectorSpec spec = SelectorSyntaxParser.parse(token);
                restrictions.enforce(spec);
                SelectorGuard.checkGuard(context, spec);
                return ArgumentParseResult.success(create(context, token, spec));
            } catch (SelectorException ex) {
                return ArgumentParseResult.failure(new SelectorParseException(getClass(), context, ex));
            } catch (Exception ex) {
                return ArgumentParseResult.failure(ex);
            }
        }

        /**
         * Builds the parsed value; throws a {@link SelectorException} to reject the spec.
         */
        protected abstract T create(CommandContext<C> context, String token, TargetSelectorSpec spec);
    }

    public static final class SinglePlayerSelectorParser<C> extends AbstractSelectorParser<C, SinglePlayerSelector> {

        public SinglePlayerSelectorParser(SpatialSelectorEngine engine) {
            this(engine, SelectorRestrictions.NONE);
        }

        public SinglePlayerSelectorParser(SpatialSelectorEngine engine, SelectorRestrictions restrictions) {
            super(engine, restrictions);
        }

        @Override
        protected SinglePlayerSelector create(CommandContext<C> context, String token, TargetSelectorSpec spec) {
            if (spec.kind() == SelectorKind.ALL_ENTITIES) {
                throw new SelectorDeniedException("Entity selector '@e' not allowed for player parameter");
            }
            if (spec.kind().defaultMultiple() && spec.limit() > 1) {
                throw new TooManyTargetsException(token, "Selector '" + token + "' targets multiple players where single target expected");
            }
            return new SinglePlayerSelectorImpl(token, spec.kind(), spec, engine);
        }
    }

    public static final class MultiplePlayerSelectorParser<C> extends AbstractSelectorParser<C, MultiplePlayerSelector> {

        public MultiplePlayerSelectorParser(SpatialSelectorEngine engine) {
            this(engine, SelectorRestrictions.NONE);
        }

        public MultiplePlayerSelectorParser(SpatialSelectorEngine engine, SelectorRestrictions restrictions) {
            super(engine, restrictions);
        }

        @Override
        protected MultiplePlayerSelector create(CommandContext<C> context, String token, TargetSelectorSpec spec) {
            if (spec.kind() == SelectorKind.ALL_ENTITIES) {
                throw new SelectorDeniedException("Entity selector '@e' not allowed for player parameter");
            }
            return new MultiplePlayerSelectorImpl(token, spec.kind(), spec, engine);
        }
    }

    public static final class SingleUnitSelectorParser<C> extends AbstractSelectorParser<C, SingleUnitSelector> {

        public SingleUnitSelectorParser(SpatialSelectorEngine engine) {
            this(engine, SelectorRestrictions.NONE);
        }

        public SingleUnitSelectorParser(SpatialSelectorEngine engine, SelectorRestrictions restrictions) {
            super(engine, restrictions);
        }

        @Override
        protected SingleUnitSelector create(CommandContext<C> context, String token, TargetSelectorSpec spec) {
            if (spec.isPlayerOnly() && spec.kind() != SelectorKind.SELF) {
                throw new SelectorDeniedException("Player selector not allowed for unit parameter");
            }
            if (spec.limit() > 1 && spec.kind().defaultMultiple()) {
                throw new TooManyTargetsException(token, "Selector '" + token + "' targets multiple units where single unit expected");
            }
            return new SingleUnitSelectorImpl(token, spec.kind(), spec, engine);
        }
    }

    public static final class MultipleUnitSelectorParser<C> extends AbstractSelectorParser<C, MultipleUnitSelector> {

        public MultipleUnitSelectorParser(SpatialSelectorEngine engine) {
            this(engine, SelectorRestrictions.NONE);
        }

        public MultipleUnitSelectorParser(SpatialSelectorEngine engine, SelectorRestrictions restrictions) {
            super(engine, restrictions);
        }

        @Override
        protected MultipleUnitSelector create(CommandContext<C> context, String token, TargetSelectorSpec spec) {
            return new MultipleUnitSelectorImpl(token, spec.kind(), spec, engine);
        }
    }
}
