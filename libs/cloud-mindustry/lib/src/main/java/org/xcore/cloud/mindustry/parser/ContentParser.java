package org.xcore.cloud.mindustry.parser;

import arc.struct.Seq;
import mindustry.Vars;
import mindustry.ctype.ContentType;
import mindustry.ctype.MappableContent;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.caption.CaptionVariable;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.exception.parsing.ParserException;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses named game content ({@code UnitType}, {@code Block}, {@code Item}, ...) by its internal
 * name, case-insensitively, e.g. {@code dagger}, {@code router}, {@code copper}.
 * <p>
 * Content is looked up when parsing, so the parser can be registered before content loads.
 */
public final class ContentParser<C, T extends MappableContent>
        implements ArgumentParser<C, T>, BlockingSuggestionProvider.Strings<C> {

    private final ContentType type;
    private final Class<T> valueType;

    public ContentParser(@NonNull ContentType type, @NonNull Class<T> valueType) {
        this.type = type;
        this.valueType = valueType;
    }

    public @NonNull ContentType contentType() {
        return type;
    }

    @Override
    public @NonNull ArgumentParseResult<T> parse(@NonNull CommandContext<C> context, @NonNull CommandInput input) {
        String token = input.readString();
        T found = find(token);
        if (found == null) {
            return ArgumentParseResult.failure(new ContentParseException(token, type, context));
        }
        return ArgumentParseResult.success(found);
    }

    private T find(String name) {
        if (Vars.content == null) {
            return null;
        }
        // Internal content names are lower case, so one lookup covers any casing.
        MappableContent found = Vars.content.getByName(type, name.toLowerCase(Locale.ROOT));
        return valueType.isInstance(found) ? valueType.cast(found) : null;
    }

    @Override
    public @NonNull Iterable<@NonNull String> stringSuggestions(@NonNull CommandContext<C> context, @NonNull CommandInput input) {
        if (Vars.content == null) {
            return List.of();
        }
        Seq<? extends MappableContent> all = Vars.content.getBy(type);
        List<String> names = new ArrayList<>(all.size);
        for (MappableContent content : all) {
            names.add(content.name);
        }
        return names;
    }

    public static final class ContentParseException extends ParserException {

        private final String input;
        private final ContentType type;

        public ContentParseException(@NonNull String input, @NonNull ContentType type, @NonNull CommandContext<?> context) {
            super(
                    ContentParser.class,
                    context,
                    MindustryCaptionKeys.ARGUMENT_PARSE_FAILURE_CONTENT,
                    CaptionVariable.of("input", input),
                    CaptionVariable.of("type", type.name())
            );
            this.input = input;
            this.type = type;
        }

        public @NonNull String input() {
            return input;
        }

        public @NonNull ContentType contentType() {
            return type;
        }
    }
}
