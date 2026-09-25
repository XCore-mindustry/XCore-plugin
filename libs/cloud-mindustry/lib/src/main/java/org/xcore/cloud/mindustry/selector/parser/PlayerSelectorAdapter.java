package org.xcore.cloud.mindustry.selector.parser;

import mindustry.gen.Player;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.xcore.cloud.mindustry.MindustryCommandManager;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.cloud.mindustry.selector.TargetSelector.SinglePlayerSelector;
import org.xcore.cloud.mindustry.selector.engine.SpatialSelectorEngine;

/**
 * Adapter that allows commands declaring a standard {@link Player} parameter
 * to transparently accept single target selectors (@p, @s, @r, @a[limit=1])
 * as well as player names and #IDs.
 */
public final class PlayerSelectorAdapter<C> implements ArgumentParser<C, Player>, TargetSelectorSuggestionProvider<C> {

    private final TargetSelectorParsers.SinglePlayerSelectorParser<C> delegate;
    private final MindustryCommandManager<C> manager;

    public PlayerSelectorAdapter(MindustryCommandManager<C> manager, SpatialSelectorEngine engine) {
        this.manager = manager;
        this.delegate = new TargetSelectorParsers.SinglePlayerSelectorParser<>(engine);
    }

    public static <C> ParserDescriptor<C, Player> playerParser(MindustryCommandManager<C> manager, SpatialSelectorEngine engine) {
        return ParserDescriptor.of(new PlayerSelectorAdapter<>(manager, engine), Player.class);
    }

    @Override
    public @NonNull ArgumentParseResult<Player> parse(
            @NonNull CommandContext<C> context,
            @NonNull CommandInput input
    ) {
        ArgumentParseResult<SinglePlayerSelector> result = delegate.parse(context, input);
        if (result.failure().isPresent()) {
            return ArgumentParseResult.failure(result.failure().get());
        }

        try {
            SinglePlayerSelector selector = result.parsedValue().orElseThrow();
            MindustrySender sender = manager.senderMapper().reverse(context.sender());
            Player player = selector.resolve(sender);
            return ArgumentParseResult.success(player);
        } catch (Exception ex) {
            return ArgumentParseResult.failure(ex);
        }
    }
}
