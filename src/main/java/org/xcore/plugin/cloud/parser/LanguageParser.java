package org.xcore.plugin.cloud.parser;

import arc.struct.ObjectMap;
import jakarta.inject.Inject;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.cloud.exception.XCoreCommandException;
import org.xcore.plugin.command.controller.client.TranslatorLanguagesProvider;

import java.util.ArrayList;
import java.util.List;

public class LanguageParser implements ArgumentParser<XCoreSender, String>, BlockingSuggestionProvider.Strings<XCoreSender> {

    private final TranslatorLanguagesProvider provider;

    @Inject
    public LanguageParser(TranslatorLanguagesProvider provider) {
        this.provider = provider;
    }

    public static ParserDescriptor<XCoreSender, String> parser(TranslatorLanguagesProvider provider) {
        return ParserDescriptor.of(new LanguageParser(provider), String.class);
    }

    @Override
    public @NonNull ArgumentParseResult<String> parse(@NonNull CommandContext<XCoreSender> commandContext, @NonNull CommandInput commandInput) {
        String input = commandInput.readString();

        if (input.equalsIgnoreCase("off")) return ArgumentParseResult.success("off");
        if (input.equalsIgnoreCase("auto")) return ArgumentParseResult.success("auto");

        String lang = provider.getLanguages().orderedKeys().find(key -> key.equalsIgnoreCase(input));

        if (lang != null) {
            return ArgumentParseResult.success(lang);
        }

        String keyByName = null;

        for (ObjectMap.Entry<String, String> entry : provider.getLanguages()) {
            if (entry.value.equalsIgnoreCase(input)) {
                keyByName = entry.key;
                break;
            }
        }

        if (keyByName != null) {
            return ArgumentParseResult.success(keyByName);
        }

        return ArgumentParseResult.failure(new XCoreCommandException("commands-tr-not-found"));
    }

    @Override
    public @NonNull Iterable<@NonNull String> stringSuggestions(@NonNull CommandContext<XCoreSender> commandContext, @NonNull CommandInput input) {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("off");
        suggestions.add("auto");
        suggestions.addAll(provider.getLanguages().keys().toSeq().list());
        return suggestions;
    }
}
