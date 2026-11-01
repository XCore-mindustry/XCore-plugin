package org.xcore.plugin.cloud.parser;

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
import org.xcore.plugin.localization.TranslatorLanguagesProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        String input = commandInput.readString().trim();

        if (input.equalsIgnoreCase("off")) return ArgumentParseResult.success("off");
        if (input.equalsIgnoreCase("auto")) return ArgumentParseResult.success("auto");

        String languageCode = provider.findLanguageCode(input);
        if (languageCode != null) {
            return ArgumentParseResult.success(languageCode);
        }

        return ArgumentParseResult.failure(new XCoreCommandException("commands-tr-not-found"));
    }

    @Override
    public @NonNull Iterable<@NonNull String> stringSuggestions(@NonNull CommandContext<XCoreSender> commandContext, @NonNull CommandInput input) {
        List<String> suggestions = new ArrayList<>();
        suggestions.add("off");
        suggestions.add("auto");
        String normalizedInput = input.lastRemainingToken().trim().toLowerCase(Locale.ROOT);
        for (String languageCode : provider.languageCodes()) {
            if (normalizedInput.isEmpty() || languageCode.startsWith(normalizedInput)) {
                suggestions.add(languageCode);
            }
        }
        return suggestions;
    }
}
