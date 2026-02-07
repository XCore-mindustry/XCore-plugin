package org.xcore.cloud.mindustry;

import arc.util.CommandHandler;
import mindustry.gen.Player;

public final class MindustryCloudCommand<C> extends CommandHandler.Command {
    MindustryCloudCommand(String registeredName, String inputName, String description, MindustryCommandManager<C> manager) {
        super(registeredName, "[args...]", description, (args, player) -> {
            MindustrySender rawSender = (player == null)
                    ? new MindustrySender.ConsoleSender()
                    : new MindustrySender.PlayerSender((Player) player);

            C sender = manager.senderMapper().map(rawSender);

            StringBuilder inputBuilder = new StringBuilder(inputName);
            for (String arg : args) {
                inputBuilder.append(" ").append(arg);
            }

            manager.commandExecutor().executeCommand(sender, inputBuilder.toString());
        });
    }
}
