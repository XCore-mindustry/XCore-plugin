package org.xcore.plugin.cloud.config;

import org.incendo.cloud.Command;
import org.incendo.cloud.CommandTree;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.execution.CommandResult;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.cloud.exception.XCoreCommandException;
import org.xcore.plugin.metrics.MetricsService;

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
        try {
            return delegate.coordinateExecution(commandTree, commandContext, commandInput)
                    .whenComplete((result, throwable) -> record(commandContext, throwable, startedAt));
        } catch (RuntimeException | Error error) {
            record(commandContext, error, startedAt);
            throw error;
        }
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
        String commandName = command != null
                ? commandName(command)
                : commandContext.optional(CloudGuardConfigurer.TELEMETRY_COMMAND_NAME).orElse(null);
        String result = classifyResult(throwable);
        CommandTelemetryRecorder.record(
                metrics,
                commandContext.sender(),
                commandName,
                result,
                (System.nanoTime() - startedAt) / 1_000_000_000d
        );
    }

    private static String classifyResult(Throwable throwable) {
        if (throwable == null) {
            return "success";
        }

        Throwable current = throwable;
        while (current != null) {
            if (current instanceof XCoreCommandException xcore && xcore.isSilent()) {
                return "blocked";
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }
        return "error";
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
