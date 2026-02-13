package org.xcore.plugin.cloud.parser;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.cloud.exception.XCoreCommandException;
import org.xcore.plugin.service.TimeService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SmartDurationParser implements ArgumentParser<XCoreSender, Duration>, BlockingSuggestionProvider.Strings<XCoreSender> {
    private final TimeService timeService;
    private final TimeUnit defaultUnit;
    private final boolean allowNegative;

    public SmartDurationParser(TimeService timeService, TimeUnit defaultUnit, boolean allowNegative) {
        this.timeService = timeService;
        this.defaultUnit = defaultUnit;
        this.allowNegative = allowNegative;
    }

    public static ParserDescriptor<XCoreSender, Duration> parser(TimeService timeService, TimeUnit defaultUnit, boolean allowNegative) {
        return ParserDescriptor.of(new SmartDurationParser(timeService, defaultUnit, allowNegative), Duration.class);
    }

    @Override
    public @NonNull ArgumentParseResult<Duration> parse(@NonNull CommandContext<XCoreSender> ctx, @NonNull CommandInput input) {
        String str = input.readString();
        Instant parsed = timeService.parsePeriod(str, defaultUnit, allowNegative);

        if (parsed == null) return ArgumentParseResult.failure(new XCoreCommandException("error-wrong-period-format"));

        return ArgumentParseResult.success(Duration.ofMillis(parsed.toEpochMilli()));
    }

    @Override
    public @NonNull Iterable<@NonNull String> stringSuggestions(@NonNull CommandContext<XCoreSender> commandContext,
                                                                @NonNull CommandInput input) {
        return List.of("10m", "1h", "1d", "1w", "1y");
    }
}
