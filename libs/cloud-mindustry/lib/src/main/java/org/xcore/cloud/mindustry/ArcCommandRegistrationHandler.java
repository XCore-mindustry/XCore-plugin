package org.xcore.cloud.mindustry;

import arc.struct.ObjectMap;
import arc.util.CommandHandler;
import org.incendo.cloud.Command;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.internal.CommandRegistrationHandler;

import java.lang.reflect.Field;
import java.util.*;

@SuppressWarnings("unchecked")
final class ArcCommandRegistrationHandler<C> implements CommandRegistrationHandler<C> {

    private static final Field COMMANDS_FIELD;

    static {
        try {
            COMMANDS_FIELD = CommandHandler.class.getDeclaredField("commands");
            COMMANDS_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Failed to access CommandHandler commands map", e);
        }
    }

    private final MindustryCommandManager<C> manager;
    private final CommandHandler handler;
    private final ObjectMap<String, CommandHandler.Command> arcCommands;

    private final Set<CommandComponent<?>> registeredRoots = new HashSet<>();
    private final Map<CommandComponent<?>, Set<String>> registeredNames = new HashMap<>();

    ArcCommandRegistrationHandler(MindustryCommandManager<C> manager, CommandHandler handler) {
        this.manager = manager;
        this.handler = handler;
        try {
            this.arcCommands = (ObjectMap<String, CommandHandler.Command>) COMMANDS_FIELD.get(handler);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean registerCommand(Command<C> command) {
        CommandComponent<C> root = command.rootComponent();

        if (registeredRoots.contains(root)) {
            return false;
        }

        Set<String> names = new HashSet<>();

        String rootName = registerSingleCommand(command, root.name(), root.name(), true);
        if (rootName == null) return false;

        names.add(rootName);

        for (String alias : root.alternativeAliases()) {
            String aliasName = registerSingleCommand(command, alias, alias, false);
            if (aliasName != null) names.add(aliasName);
        }

        registeredRoots.add(root);
        registeredNames.put(root, names);
        return true;
    }

    private String registerSingleCommand(Command<C> command, String displayName, String inputName, boolean isRoot) {
        ConflictStrategy strategy = manager.getConflictStrategy();

        if (arcCommands.containsKey(displayName)) {
            switch (strategy) {
                case SKIP -> { return null; }
                case FAIL -> throw new IllegalStateException("Command already registered: " + displayName);
                case OVERRIDE -> handler.removeCommand(displayName);
                case PREFIX -> {
                    displayName = manager.getCommandPrefix() + ":" + displayName;
                    if (arcCommands.containsKey(displayName)) {
                        return null;
                    }
                }
            }
        }

        Description desc = command.rootComponent().description();
        if (desc.isEmpty()) {
            desc = command.commandDescription().description();
        }

        String description = desc.textDescription();
        MindustryCloudCommand<C> wrapper = new MindustryCloudCommand<>(displayName, inputName, description, manager);

        arcCommands.put(displayName, wrapper);
        handler.getCommandList().add(wrapper);
        return displayName;
    }

    @Override
    public void unregisterRootCommand(CommandComponent<C> root) {
        if (!registeredRoots.remove(root)) return;

        Set<String> names = registeredNames.remove(root);
        if (names == null) return;

        for (String name : names) {
            handler.removeCommand(name);
        }
    }
}
