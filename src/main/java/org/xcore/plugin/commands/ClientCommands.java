package org.xcore.plugin.commands;

import arc.util.CommandHandler;
import org.xcore.plugin.PluginVars;
import org.xcore.plugin.XcorePlugin;
import org.xcore.plugin.commands.controllers.HexedController;
import org.xcore.plugin.infra.commands.CommandBus;

import org.xcore.plugin.commands.controllers.*;

public class ClientCommands {
    public static void register(CommandHandler handler) {
        CommandBus bus = new CommandBus(handler);
        bus.register(
                new InformationController(handler),
                new SocialController(),
                new VoteController(),
                new MapController(),
                new StatsController(),
                new AuthController(),
                new ModerationController()
        );

        if (PluginVars.config.isMiniHexed()) {
            bus.register(new HexedController());
        }

        XcorePlugin.info("Registered @ unique client commands.", bus.getTotalCommands());
    }
}