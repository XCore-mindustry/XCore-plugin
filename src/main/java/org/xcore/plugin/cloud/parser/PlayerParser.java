package org.xcore.plugin.cloud.parser;

import arc.struct.Seq;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.cloud.mindustry.selector.SelectorKind;
import org.xcore.cloud.mindustry.selector.TargetSelectorSpec;
import org.xcore.cloud.mindustry.selector.engine.SelectorResolutionBridge;
import org.xcore.cloud.mindustry.selector.engine.SpatialSelectorEngine;
import org.xcore.cloud.mindustry.selector.exception.NoSuchTargetException;
import org.xcore.cloud.mindustry.selector.exception.TooManyTargetsException;
import org.xcore.cloud.mindustry.selector.parser.SelectorSyntaxParser;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.cloud.exception.XCoreCommandException;
import org.xcore.plugin.common.TextUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses an input string into an online {@link Player}.
 * <p>
 * Logic:
 * 1. Checks for Target Selectors (@p, @s, @r, @a[limit=1]).
 * 2. Checks for ID format (#123).
 * 3. Checks for Exact Name match.
 * 4. Checks for UUID/IP match (Only if sender is Console).
 */
public class PlayerParser implements ArgumentParser<XCoreSender, Player>, BlockingSuggestionProvider.Strings<XCoreSender> {

    private static final SpatialSelectorEngine SPATIAL_ENGINE = new SpatialSelectorEngine();

    public static ParserDescriptor<XCoreSender, Player> parser() {
        return ParserDescriptor.of(new PlayerParser(), Player.class);
    }

    @Override
    public @NonNull ArgumentParseResult<Player> parse(@NonNull CommandContext<XCoreSender> commandContext, @NonNull CommandInput commandInput) {
        String input = commandInput.readString();
        XCoreSender sender = commandContext.sender();
        boolean isServer = !sender.isPlayer();

        if (input.startsWith("@")) {
            try {
                TargetSelectorSpec spec = SelectorSyntaxParser.parse(input);
                org.xcore.cloud.mindustry.selector.engine.SelectorGuard.checkGuard(commandContext, spec);
                if (spec.kind() == SelectorKind.ALL_ENTITIES) {
                    return ArgumentParseResult.failure(new NoSuchTargetException(input));
                }
                if ((spec.kind() == SelectorKind.ALL_PLAYERS || spec.kind() == SelectorKind.ALL_ENTITIES) && spec.limit() > 1) {
                    return ArgumentParseResult.failure(new TooManyTargetsException(input, "Expected single player but selector allows multiple"));
                }
                MindustrySender baseSender = sender.getHandle();
                Seq<Player> list = SelectorResolutionBridge.resolveSync(
                        () -> SPATIAL_ENGINE.resolvePlayers(baseSender, spec)
                );
                if (list.isEmpty()) {
                    return ArgumentParseResult.failure(new NoSuchTargetException(input));
                }
                if (list.size > 1) {
                    return ArgumentParseResult.failure(new TooManyTargetsException(input, "Expected single player for '" + input + "' but found " + list.size));
                }
                return ArgumentParseResult.success(list.first());
            } catch (Exception ex) {
                return ArgumentParseResult.failure(ex);
            }
        }

        if (input.startsWith("#")) {
            int id = arc.util.Strings.parseInt(input.substring(1), -1);
            if (id != -1) {
                Player player = Groups.player.getByID(id);
                if (player != null) {
                    return ArgumentParseResult.success(player);
                }
            }
        }

        Player exactMatch = Groups.player.find(p -> TextUtils.deepEquals(p.name, input));
        if (exactMatch != null) {
            return ArgumentParseResult.success(exactMatch);
        }

        if (isServer) {
            Player uuidMatch = Groups.player.find(p -> p.uuid().equals(input));
            if (uuidMatch != null) {
                return ArgumentParseResult.success(uuidMatch);
            }

            Player ipMatch = Groups.player.find(p -> p.ip().equals(input));
            if (ipMatch != null) {
                return ArgumentParseResult.success(ipMatch);
            }
        }

        return ArgumentParseResult.failure(new XCoreCommandException("error-player-not-found"));
    }

    @Override
    public @NonNull Iterable<@NonNull String> stringSuggestions(@NonNull CommandContext<XCoreSender> commandContext, @NonNull CommandInput input) {
        String token = input.peekString();
        List<String> suggestions = new ArrayList<>();

        if (token.startsWith("@")) {
            for (String sel : List.of("@p", "@s", "@r", "@a")) {
                if (sel.startsWith(token)) suggestions.add(sel);
            }
            return suggestions;
        }

        Groups.player.each(p -> suggestions.add(arc.util.Strings.stripColors(p.name)));
        Groups.player.each(p -> suggestions.add("#" + p.id));

        return suggestions;
    }
}
