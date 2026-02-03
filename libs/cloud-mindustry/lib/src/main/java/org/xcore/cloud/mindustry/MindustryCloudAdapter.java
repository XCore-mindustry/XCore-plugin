package org.xcore.cloud.mindustry;

import arc.util.CommandHandler;
import mindustry.gen.Player;

final class MindustryCloudAdapter<C> extends CommandHandler.Command {

    MindustryCloudAdapter(String text, String description, MindustryCommandManager<C> manager) {
        super(text, "[args...]", description, (args, player) -> {
            var sender = manager.senderMapper().map(
                    player == null ? new MindustrySender.ConsoleSender() : new MindustrySender.PlayerSender((Player) player)
            );

            var input = text + (args.length > 0 ? " " + args[0] : "");

            manager.commandExecutor().executeCommand(sender, input);
        });
    }
}