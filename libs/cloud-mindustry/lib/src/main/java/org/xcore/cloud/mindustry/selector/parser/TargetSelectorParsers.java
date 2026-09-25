package org.xcore.cloud.mindustry.selector.parser;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.xcore.cloud.mindustry.selector.SelectorKind;
import org.xcore.cloud.mindustry.selector.TargetSelector.MultiplePlayerSelector;
import org.xcore.cloud.mindustry.selector.TargetSelector.MultipleUnitSelector;
import org.xcore.cloud.mindustry.selector.TargetSelector.SinglePlayerSelector;
import org.xcore.cloud.mindustry.selector.TargetSelector.SingleUnitSelector;
import org.xcore.cloud.mindustry.selector.TargetSelectorSpec;
import org.xcore.cloud.mindustry.selector.engine.SelectorGuard;
import org.xcore.cloud.mindustry.selector.engine.SpatialSelectorEngine;
import org.xcore.cloud.mindustry.selector.exception.SelectorDeniedException;
import org.xcore.cloud.mindustry.selector.exception.TooManyTargetsException;
import org.xcore.cloud.mindustry.selector.impl.MultiplePlayerSelectorImpl;
import org.xcore.cloud.mindustry.selector.impl.MultipleUnitSelectorImpl;
import org.xcore.cloud.mindustry.selector.impl.SinglePlayerSelectorImpl;
import org.xcore.cloud.mindustry.selector.impl.SingleUnitSelectorImpl;

public final class TargetSelectorParsers {

    private TargetSelectorParsers() {}

    public static <C> ParserDescriptor<C, SinglePlayerSelector> singlePlayerSelector(SpatialSelectorEngine engine) {
        return ParserDescriptor.of(new SinglePlayerSelectorParser<>(engine), SinglePlayerSelector.class);
    }

    public static <C> ParserDescriptor<C, MultiplePlayerSelector> multiplePlayerSelector(SpatialSelectorEngine engine) {
        return ParserDescriptor.of(new MultiplePlayerSelectorParser<>(engine), MultiplePlayerSelector.class);
    }

    public static <C> ParserDescriptor<C, SingleUnitSelector> singleUnitSelector(SpatialSelectorEngine engine) {
        return ParserDescriptor.of(new SingleUnitSelectorParser<>(engine), SingleUnitSelector.class);
    }

    public static <C> ParserDescriptor<C, MultipleUnitSelector> multipleUnitSelector(SpatialSelectorEngine engine) {
        return ParserDescriptor.of(new MultipleUnitSelectorParser<>(engine), MultipleUnitSelector.class);
    }

    public static final class SinglePlayerSelectorParser<C>
            implements ArgumentParser<C, SinglePlayerSelector>, TargetSelectorSuggestionProvider<C> {

        private final SpatialSelectorEngine engine;

        public SinglePlayerSelectorParser(SpatialSelectorEngine engine) {
            this.engine = engine;
        }

        @Override
        public @NonNull ArgumentParseResult<SinglePlayerSelector> parse(
                @NonNull CommandContext<C> context,
                @NonNull CommandInput input
        ) {
            String token = input.readString();
            try {
                TargetSelectorSpec spec = SelectorSyntaxParser.parse(token);

                SelectorGuard.checkGuard(context, spec);

                if (spec.kind() == SelectorKind.ALL_ENTITIES) {
                    return ArgumentParseResult.failure(new SelectorDeniedException("Entity selector '@e' not allowed for player parameter"));
                }

                if (spec.kind().defaultMultiple() && spec.limit() > 1) {
                    return ArgumentParseResult.failure(
                            new TooManyTargetsException(token, "Selector '" + token + "' targets multiple players where single target expected")
                    );
                }

                return ArgumentParseResult.success(new SinglePlayerSelectorImpl(token, spec.kind(), spec, engine));
            } catch (Exception ex) {
                return ArgumentParseResult.failure(ex);
            }
        }
    }

    public static final class MultiplePlayerSelectorParser<C>
            implements ArgumentParser<C, MultiplePlayerSelector>, TargetSelectorSuggestionProvider<C> {

        private final SpatialSelectorEngine engine;

        public MultiplePlayerSelectorParser(SpatialSelectorEngine engine) {
            this.engine = engine;
        }

        @Override
        public @NonNull ArgumentParseResult<MultiplePlayerSelector> parse(
                @NonNull CommandContext<C> context,
                @NonNull CommandInput input
        ) {
            String token = input.readString();
            try {
                TargetSelectorSpec spec = SelectorSyntaxParser.parse(token);

                if (spec.kind() == SelectorKind.ALL_ENTITIES) {
                    return ArgumentParseResult.failure(new SelectorDeniedException("Entity selector '@e' not allowed for player parameter"));
                }

                SelectorGuard.checkGuard(context, spec);

                return ArgumentParseResult.success(new MultiplePlayerSelectorImpl(token, spec.kind(), spec, engine));
            } catch (Exception ex) {
                return ArgumentParseResult.failure(ex);
            }
        }
    }

    public static final class SingleUnitSelectorParser<C>
            implements ArgumentParser<C, SingleUnitSelector>, TargetSelectorSuggestionProvider<C> {

        private final SpatialSelectorEngine engine;

        public SingleUnitSelectorParser(SpatialSelectorEngine engine) {
            this.engine = engine;
        }

        @Override
        public @NonNull ArgumentParseResult<SingleUnitSelector> parse(
                @NonNull CommandContext<C> context,
                @NonNull CommandInput input
        ) {
            String token = input.readString();
            try {
                TargetSelectorSpec spec = SelectorSyntaxParser.parse(token);

                SelectorGuard.checkGuard(context, spec);

                if (spec.isPlayerOnly() && spec.kind() != SelectorKind.SELF) {
                    return ArgumentParseResult.failure(new SelectorDeniedException("Player selector not allowed for unit parameter"));
                }

                if (spec.limit() > 1 && spec.kind().defaultMultiple()) {
                    return ArgumentParseResult.failure(
                            new TooManyTargetsException(token, "Selector '" + token + "' targets multiple units where single unit expected")
                    );
                }

                return ArgumentParseResult.success(new SingleUnitSelectorImpl(token, spec.kind(), spec, engine));
            } catch (Exception ex) {
                return ArgumentParseResult.failure(ex);
            }
        }
    }

    public static final class MultipleUnitSelectorParser<C>
            implements ArgumentParser<C, MultipleUnitSelector>, TargetSelectorSuggestionProvider<C> {

        private final SpatialSelectorEngine engine;

        public MultipleUnitSelectorParser(SpatialSelectorEngine engine) {
            this.engine = engine;
        }

        @Override
        public @NonNull ArgumentParseResult<MultipleUnitSelector> parse(
                @NonNull CommandContext<C> context,
                @NonNull CommandInput input
        ) {
            String token = input.readString();
            try {
                TargetSelectorSpec spec = SelectorSyntaxParser.parse(token);

                SelectorGuard.checkGuard(context, spec);

                return ArgumentParseResult.success(new MultipleUnitSelectorImpl(token, spec.kind(), spec, engine));
            } catch (Exception ex) {
                return ArgumentParseResult.failure(ex);
            }
        }
    }
}
