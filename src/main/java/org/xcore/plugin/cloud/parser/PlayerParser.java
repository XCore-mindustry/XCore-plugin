package org.xcore.plugin.cloud.parser;

import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.cloud.exception.XCoreCommandException;
import org.xcore.plugin.common.TextUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses an input string into an online {@link Player}.
 * <p>
 * Logic:
 * 1. Checks for ID format (#123).
 * 2. Checks for Exact Name match.
 * 3. Checks for UUID/IP match (Only if sender is Console).
 */
public class PlayerParser implements ArgumentParser<XCoreSender, Player>, BlockingSuggestionProvider.Strings<XCoreSender> {

    public static ParserDescriptor<XCoreSender, Player> parser() {
        return ParserDescriptor.of(new PlayerParser(), Player.class);
    }

    @Override
    public @NonNull ArgumentParseResult<Player> parse(@NonNull CommandContext<XCoreSender> commandContext, @NonNull CommandInput commandInput) {
        String input = commandInput.readString();
        XCoreSender sender = commandContext.sender();
        boolean isServer = !sender.isPlayer();

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
        List<String> suggestions = new ArrayList<>();

        Groups.player.each(p -> suggestions.add(arc.util.Strings.stripColors(p.name)));

        Groups.player.each(p -> suggestions.add("#" + p.id));

        return suggestions;
    }
}
