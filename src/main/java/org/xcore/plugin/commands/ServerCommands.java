package org.xcore.plugin.commands;

import arc.util.CommandHandler;
import org.xcore.plugin.XcorePlugin;
import org.xcore.plugin.commands.controllers.server.*;
import org.xcore.plugin.infra.commands.CommandBus;

public class ServerCommands {
    public static void register(CommandHandler handler) {
        CommandBus bus = new CommandBus(handler);

        bus.register(
                new InformationController(),
                new DataController(),
                new ModerationController(),
                new MaintainController()

        );

        XcorePlugin.info("Registered @ unique server commands.", bus.getTotalCommands());
    }
}