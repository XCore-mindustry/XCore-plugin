package org.xcore.plugin.cloud.parser;

import arc.struct.Seq;
import mindustry.Vars;
import mindustry.maps.Map;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.cloud.exception.XCoreCommandException;

import java.util.ArrayList;
import java.util.List;

public class MapParser implements ArgumentParser<XCoreSender, Map>, BlockingSuggestionProvider.Strings<XCoreSender> {

    public static ParserDescriptor<XCoreSender, Map> parser() {
        return ParserDescriptor.of(new MapParser(), Map.class);
    }

    @Override
    public @NonNull ArgumentParseResult<Map> parse(@NonNull CommandContext<XCoreSender> commandContext, @NonNull CommandInput commandInput) {
        String input = commandInput.readString();

        Seq<Map> maps = Vars.maps.customMaps().isEmpty() ? Vars.maps.all() : Vars.maps.customMaps();

        if (arc.util.Strings.canParseInt(input)) {
            int id = arc.util.Strings.parseInt(input);
            if (id > 0 && id <= maps.size) {
                return ArgumentParseResult.success(maps.get(id - 1));
            }
        }

        Map match = maps.find(m -> arc.util.Strings.stripColors(m.name()).equalsIgnoreCase(input));

        if (match == null) {
            match = maps.find(m -> arc.util.Strings.stripColors(m.name()).toLowerCase().contains(input.toLowerCase()));
        }

        if (match == null) {
            match = maps.find(m -> m.file.name().equalsIgnoreCase(input));
        }

        if (match != null) {
            return ArgumentParseResult.success(match);
        }

        return ArgumentParseResult.failure(new XCoreCommandException("error-map-not-found"));
    }

    @Override
    public @NonNull Iterable<@NonNull String> stringSuggestions(@NonNull CommandContext<XCoreSender> commandContext, @NonNull CommandInput input) {
        Seq<Map> maps = Vars.maps.customMaps().isEmpty() ? Vars.maps.all() : Vars.maps.customMaps();
        List<String> suggestions = new ArrayList<>();

        maps.each(m -> suggestions.add(arc.util.Strings.stripColors(m.name())));

        return suggestions;
    }
}
