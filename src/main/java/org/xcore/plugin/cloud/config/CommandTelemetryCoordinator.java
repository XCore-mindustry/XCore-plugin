package org.xcore.plugin.cloud.config;

import org.incendo.cloud.Command;
import org.incendo.cloud.CommandTree;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.execution.CommandResult;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.metrics.Counter;
import org.xcore.plugin.metrics.Histogram;
import org.xcore.plugin.metrics.MetricsService;
import org.xcore.plugin.metrics.Tags;
import org.xcore.plugin.metrics.XcoreMetrics;

import java.util.concurrent.CompletableFuture;

final class CommandTelemetryCoordinator implements ExecutionCoordinator<XCoreSender> {
    private final ExecutionCoordinator<XCoreSender> delegate;
    private final MetricsService metrics;

    CommandTelemetryCoordinator(ExecutionCoordinator<XCoreSender> delegate, MetricsService metrics) {
        this.delegate = delegate;
        this.metrics = metrics;
    }

    @Override
    public CompletableFuture<CommandResult<XCoreSender>> coordinateExecution(CommandTree<XCoreSender> commandTree,
                                                                              CommandContext<XCoreSender> commandContext,
                                                                              CommandInput commandInput) {
        long startedAt = System.nanoTime();
        return delegate.coordinateExecution(commandTree, commandContext, commandInput)
                .whenComplete((result, throwable) -> record(commandContext, throwable, startedAt));
    }

    @Override
    public <S extends org.incendo.cloud.suggestion.Suggestion> CompletableFuture<org.incendo.cloud.suggestion.Suggestions<XCoreSender, S>> coordinateSuggestions(CommandTree<XCoreSender> commandTree,
                                                                                                                                                                    CommandContext<XCoreSender> commandContext,
                                                                                                                                                                    CommandInput commandInput,
                                                                                                                                                                    org.incendo.cloud.suggestion.SuggestionMapper<S> suggestionMapper) {
        return delegate.coordinateSuggestions(commandTree, commandContext, commandInput, suggestionMapper);
    }

    private void record(CommandContext<XCoreSender> commandContext, Throwable throwable, long startedAt) {
        Command<XCoreSender> command = commandContext.command();
        if (command == null) {
            return;
        }

        String commandName = commandName(command);
        String source = commandContext.sender().isPlayer() ? "player" : "server";
        String result = throwable == null ? "success" : "error";

        Counter counter = metrics.counter(XcoreMetrics.COMMANDS_TOTAL, Tags.of(
                "command", commandName,
                "source", source,
                "result", result
        ));
        counter.increment();

        Histogram histogram = metrics.histogram(XcoreMetrics.COMMAND_DURATION_SECONDS, Tags.of(
                "command", commandName,
                "source", source
        ));
        histogram.observe((System.nanoTime() - startedAt) / 1_000_000_000d);
    }

    private static String commandName(Command<XCoreSender> command) {
        StringBuilder builder = new StringBuilder();
        for (var component : command.components()) {
            if (component.type() != org.incendo.cloud.component.CommandComponent.ComponentType.LITERAL) {
                break;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(component.name());
        }
        return builder.isEmpty() ? "unknown" : builder.toString();
    }
}
