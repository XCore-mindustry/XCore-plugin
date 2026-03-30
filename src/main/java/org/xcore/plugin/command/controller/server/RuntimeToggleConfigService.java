package org.xcore.plugin.command.controller.server;

import arc.files.Fi;
import com.google.gson.Gson;
import org.xcore.plugin.config.Config;

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

    private final Config config;
    private final Fi configFile;
    private final Gson prettyGson;

    RuntimeToggleConfigService(Config config, Fi configFile, Gson prettyGson) {
        this.config = config;
        this.configFile = configFile;
        this.prettyGson = prettyGson;
    }

    ToggleMutationResult disable(ToggleTarget target, String value) {
        Set<String> values = mutableSet(target);
        if (!values.add(value)) {
            return new ToggleMutationResult(false, value);
        }

        save();
        return new ToggleMutationResult(true, value);
    }

    ToggleMutationResult enable(ToggleTarget target, String value) {
        Set<String> values = mutableSet(target);
        if (!values.remove(value)) {
            return new ToggleMutationResult(false, value);
        }

        save();
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
            case COMMAND -> config.disabledCommands;
            case FEATURE -> config.disabledFeatures;
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
            case COMMAND -> config.disabledCommands = current;
            case FEATURE -> config.disabledFeatures = current;
        }

        return current;
    }

    private void save() {
        configFile.writeString(prettyGson.toJson(config));
    }
}
