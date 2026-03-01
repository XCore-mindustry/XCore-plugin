package org.xcore.plugin.cloud.parser;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;
import org.xcore.plugin.cloud.XCoreSender;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class EnumValueParser<E extends Enum<E>> implements ArgumentParser<XCoreSender, E>, BlockingSuggestionProvider.Strings<XCoreSender> {

    private final Class<E> enumType;
    private final Map<String, E> values;
    private final List<String> suggestions;

    public EnumValueParser(Class<E> enumType) {
        this.enumType = enumType;
        this.values = new LinkedHashMap<>();
        for (E constant : enumType.getEnumConstants()) {
            values.put(token(constant.name()), constant);
        }
        this.suggestions = List.copyOf(values.keySet());
    }

    public static <E extends Enum<E>> ParserDescriptor<XCoreSender, E> parser(Class<E> enumType) {
        return ParserDescriptor.of(new EnumValueParser<>(enumType), enumType);
    }

    @Override
    public @NonNull ArgumentParseResult<E> parse(@NonNull CommandContext<XCoreSender> commandContext, @NonNull CommandInput commandInput) {
        String raw = commandInput.readString();
        E value = values.get(token(raw));
        if (value != null) {
            return ArgumentParseResult.success(value);
        }

        return ArgumentParseResult.failure(new IllegalArgumentException(
                "Invalid value '" + raw + "' for " + enumType.getSimpleName() + ". Allowed: " + String.join(", ", suggestions)
        ));
    }

    @Override
    public @NonNull Iterable<@NonNull String> stringSuggestions(@NonNull CommandContext<XCoreSender> commandContext,
                                                                @NonNull CommandInput input) {
        String query = token(input.remainingInput());
        if (query.isBlank()) {
            return suggestions;
        }
        return suggestions.stream()
                .filter(value -> value.startsWith(query))
                .toList();
    }

    private static String token(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', '-')
                .replace(' ', '-');
    }
}
