package org.xcore.plugin.command.controller.server;

import org.xcore.plugin.config.ServerLocalConfigTomlStore;
import org.xcore.plugin.config.TomlXcoreConfig;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

final class RuntimeToggleConfigService {
    enum ToggleTarget {
        COMMAND,
        FEATURE
    }

    record ToggleMutationResult(boolean changed, String value) {
    }

    private final TomlXcoreConfig config;
    private final ServerLocalConfigTomlStore tomlStore;

    RuntimeToggleConfigService(TomlXcoreConfig config, ServerLocalConfigTomlStore tomlStore) {
        this.config = config;
        this.tomlStore = tomlStore;
    }

    ToggleMutationResult disable(ToggleTarget target, String value) {
        Set<String> values = mutableSet(target);
        if (!values.add(value)) {
            return new ToggleMutationResult(false, value);
        }

        try {
            save();
        } catch (RuntimeException e) {
            values.remove(value);
            throw e;
        }
        return new ToggleMutationResult(true, value);
    }

    ToggleMutationResult enable(ToggleTarget target, String value) {
        Set<String> values = mutableSet(target);
        if (!values.remove(value)) {
            return new ToggleMutationResult(false, value);
        }

        try {
            save();
        } catch (RuntimeException e) {
            values.add(value);
            throw e;
        }
        return new ToggleMutationResult(true, value);
    }

    boolean isEmpty(ToggleTarget target) {
        Set<String> values = values(target);
        return values == null || values.isEmpty();
    }

    String list(ToggleTarget target) {
        var ordered = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> values = values(target);
        if (values != null) {
            ordered.addAll(values);
        }
        return String.join(", ", ordered);
    }

    String normalizeCommandName(String commandName) {
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

    String extractRootCommand(String normalizedCommand) {
        return normalizedCommand.split(" ", 2)[0];
    }

    private Set<String> values(ToggleTarget target) {
        return switch (target) {
            case COMMAND -> config.runtime.disabledCommands;
            case FEATURE -> config.runtime.disabledFeatures;
        };
    }

    private Set<String> mutableSet(ToggleTarget target) {
        Set<String> current = values(target);
        if (current == null) {
            current = new HashSet<>();
        } else if (!(current instanceof HashSet<?>)) {
            current = new HashSet<>(current);
        }

        switch (target) {
            case COMMAND -> config.runtime.disabledCommands = current;
            case FEATURE -> config.runtime.disabledFeatures = current;
        }

        return current;
    }

    private void save() {
        tomlStore.write(config);
    }
}
