package org.xcore.cloud.mindustry;

import arc.struct.ObjectMap;
import arc.util.CommandHandler;
import org.incendo.cloud.Command;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import java.lang.reflect.Field;

final class ArcCommandInjector<C> implements CommandRegistrationHandler<C> {
    private static final Field COMMANDS_FIELD;

    static {
        try {
            COMMANDS_FIELD = CommandHandler.class.getDeclaredField("commands");
            COMMANDS_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Could not find commands field in CommandHandler", e);
        }
    }

    private final CommandHandler handler;
    private final ObjectMap<String, CommandHandler.Command> arcCommands;
    private final MindustryCommandManager<C> cloudManager;

    @SuppressWarnings("unchecked")
    ArcCommandInjector(CommandHandler handler, MindustryCommandManager<C> cloudManager) {
        this.handler = handler;
        this.cloudManager = cloudManager;
        try {
            this.arcCommands = (ObjectMap<String, CommandHandler.Command>) COMMANDS_FIELD.get(handler);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean registerCommand(Command<C> command) {
        var name = command.rootComponent().name();
        var description = command.commandDescription().description().textDescription();

        var adapter = new MindustryCloudAdapter<>(name, description, cloudManager);

        arcCommands.put(name, adapter);
        handler.getCommandList().add(adapter);
        return true;
    }
}