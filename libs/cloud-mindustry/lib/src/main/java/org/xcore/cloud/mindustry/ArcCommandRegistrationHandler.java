package org.xcore.cloud.mindustry;

import arc.struct.ObjectMap;
import arc.struct.Seq;
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

    /**
     * What this handler put into the Arc handler under one physical name, and what it pushed out.
     * {@code displaced} is the command {@link ConflictStrategy#OVERRIDE} replaced, or null.
     */
    private record OwnedRegistration(String name, CommandHandler.Command wrapper,
                                     CommandHandler.Command displaced, int previousIndex) {}

    private final Map<CommandComponent<?>, List<OwnedRegistration>> registrations = new HashMap<>();

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

        if (registrations.containsKey(root)) {
            return false;
        }

        List<OwnedRegistration> owned = new ArrayList<>();

        try {
            OwnedRegistration rootRegistration = registerSingleCommand(command, root.name(), root.name());
            if (rootRegistration == null) return false;

            owned.add(rootRegistration);

            for (String alias : root.alternativeAliases()) {
                OwnedRegistration aliasRegistration = registerSingleCommand(command, alias, alias);
                if (aliasRegistration != null) owned.add(aliasRegistration);
            }
        } catch (RuntimeException e) {
            // FAIL on an alias: take back what this attempt already published.
            unregister(owned);
            throw e;
        }

        registrations.put(root, owned);
        return true;
    }

    private OwnedRegistration registerSingleCommand(Command<C> command, String displayName, String inputName) {
        ConflictStrategy strategy = manager.getConflictStrategy();

        CommandHandler.Command displaced = null;
        int previousIndex = -1;

        if (arcCommands.containsKey(displayName)) {
            switch (strategy) {
                case SKIP -> { return null; }
                case FAIL -> throw new IllegalStateException("Command already registered: " + displayName);
                case OVERRIDE -> {
                    displaced = arcCommands.get(displayName);
                    previousIndex = handler.getCommandList().indexOf(displaced, true);
                    arcCommands.remove(displayName);
                    handler.getCommandList().remove(displaced, true);
                }
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
        return new OwnedRegistration(displayName, wrapper, displaced, previousIndex);
    }

    @Override
    public void unregisterRootCommand(CommandComponent<C> root) {
        List<OwnedRegistration> owned = registrations.remove(root);
        if (owned != null) unregister(owned);
    }

    /**
     * Removes the wrappers and puts displaced commands back where they were. Runs in reverse
     * registration order, so the recorded indices are valid again by the time they are used.
     * A name that someone else has re-registered since is left alone.
     */
    private void unregister(List<OwnedRegistration> owned) {
        Seq<CommandHandler.Command> list = handler.getCommandList();

        for (int i = owned.size() - 1; i >= 0; i--) {
            OwnedRegistration registration = owned.get(i);
            list.remove(registration.wrapper(), true);

            if (arcCommands.get(registration.name()) != registration.wrapper()) continue;
            arcCommands.remove(registration.name());

            CommandHandler.Command displaced = registration.displaced();
            if (displaced == null) continue;

            arcCommands.put(registration.name(), displaced);
            int index = registration.previousIndex();
            if (index < 0 || index > list.size) index = list.size;
            list.insert(index, displaced);
        }
    }
}
