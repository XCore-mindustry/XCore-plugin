package org.xcore.example;

import arc.util.Log;
import mindustry.mod.Plugin;
import org.xcore.cloud.mindustry.MindustryCommandManager;
import org.xcore.cloud.mindustry.MindustrySender;

public class ExamplePlugin extends Plugin {

    @Override
    public void init() {
        Log.info("Example Plugin Loading...");
        var remoteCommands = MindustryCommandManager.create(mindustry.Vars.netServer.clientCommands);

        remoteCommands.command(remoteCommands.commandBuilder("hello")
                .handler(context -> {
                    MindustrySender sender = context.sender();
                    sender.sendMessage("Hello from Cloud v2!");
                }));

        Log.info("Example Plugin Loaded!");
    }
}
