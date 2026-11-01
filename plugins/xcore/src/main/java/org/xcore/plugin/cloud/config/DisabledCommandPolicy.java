package org.xcore.plugin.cloud.config;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.incendo.cloud.Command;
import org.incendo.cloud.component.CommandComponent;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.config.TomlXcoreConfig;

import java.util.Locale;
import java.util.stream.Collectors;

@Singleton
public class DisabledCommandPolicy {

    private final TomlXcoreConfig config;

    @Inject
    public DisabledCommandPolicy(TomlXcoreConfig config) {
        this.config = config;
    }

    public boolean isCommandDisabled(Command<XCoreSender> command) {
        return disabledCommandKeyFromCommand(command) != null;
    }

    public boolean isCommandDisabled(String commandName) {
        String normalized = normalizeCommandName(commandName);
        return normalized != null && isExplicitlyDisabled(normalized);
    }

    public String disabledCommandKeyFromCommand(Command<XCoreSender> command) {
        if (!hasDisabledCommands()) {
            return null;
        }

        String rootName = command.rootComponent().name();
        if (isCommandDisabled(rootName)) {
            return rootName;
        }

        for (String alias : command.rootComponent().aliases()) {
            if (isCommandDisabled(alias)) {
                return rootName;
            }
        }

        String literalSyntax = command.components().stream()
                .filter(component -> component.type() == CommandComponent.ComponentType.LITERAL)
                .map(CommandComponent::name)
                .collect(Collectors.joining(" "));

        if (!literalSyntax.equalsIgnoreCase(rootName) && isCommandDisabled(literalSyntax)) {
            return literalSyntax;
        }
        return null;
    }

    public String disabledCommandKey(String input) {
        if (!hasDisabledCommands()) {
            return null;
        }

        String normalizedInput = normalizeCommandName(input);
        if (normalizedInput == null) {
            return null;
        }

        for (String disabledCommand : config.runtime.disabledCommands) {
            String normalizedDisabled = normalizeCommandName(disabledCommand);
            if (normalizedDisabled == null) {
                continue;
            }

            if (isFullOrPrefixMatch(normalizedInput, normalizedDisabled)) {
                return normalizedDisabled;
            }
        }
        return null;
    }

    public boolean hasDisabledCommands() {
        return config.runtime.disabledCommands != null && !config.runtime.disabledCommands.isEmpty();
    }

    public String normalizeCommandName(String commandName) {
        if (commandName == null) {
            return null;
        }
        String normalized = commandName.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        normalized = normalized.replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isExplicitlyDisabled(String normalizedCommandName) {
        if (!hasDisabledCommands()) {
            return false;
        }

        for (String disabledCommand : config.runtime.disabledCommands) {
            String normalizedDisabled = normalizeCommandName(disabledCommand);
            if (normalizedCommandName.equals(normalizedDisabled)) {
                return true;
            }
        }
        return false;
    }

    private boolean isFullOrPrefixMatch(String normalizedInput, String normalizedDisabledCommand) {
        return normalizedInput.equals(normalizedDisabledCommand)
                || normalizedInput.startsWith(normalizedDisabledCommand + " ");
    }
}
