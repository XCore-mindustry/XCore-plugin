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
import org.xcore.plugin.service.TimeService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SmartDurationParser implements ArgumentParser<XCoreSender, Duration>, BlockingSuggestionProvider.Strings<XCoreSender> {

    private final TimeService timeService;
    private final TimeUnit defaultUnit;

    @Inject
    public SmartDurationParser(TimeService timeService, TimeUnit defaultUnit) {
        this.timeService = timeService;
        this.defaultUnit = defaultUnit;
    }

    public static ParserDescriptor<XCoreSender, Duration> parser(TimeService timeService, TimeUnit defaultUnit) {
        return ParserDescriptor.of(new SmartDurationParser(timeService, defaultUnit), Duration.class);
    }

    @Override
    public @NonNull ArgumentParseResult<Duration> parse(@NonNull CommandContext<XCoreSender> commandContext,
                                                        @NonNull CommandInput commandInput) {
        String input = commandInput.readString();

        Instant parsed = timeService.parsePeriod(input, defaultUnit);

        if (parsed == null) {
            return ArgumentParseResult.failure(new XCoreCommandException("error-wrong-period-format"));
        }

        return ArgumentParseResult.success(Duration.ofMillis(parsed.toEpochMilli()));
    }

    @Override
    public @NonNull Iterable<@NonNull String> stringSuggestions(@NonNull CommandContext<XCoreSender> commandContext,
                                                                @NonNull CommandInput input) {
        return List.of("10m", "1h", "1d", "1w", "1y");
    }
}
