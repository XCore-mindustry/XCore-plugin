package org.xcore.plugin.infra.commands.context;

import arc.util.Log;
import mindustry.gen.Call;

public final class ServerContext extends CommandContext {

    public ServerContext(String[] args) {
        super(args);
    }

    public void log(String message, Object... args) {
        Log.info(message, args);
    }

    public void error(String message, Object... args) {
        Log.err(message, args);
    }

    public void broadcast(String message) {
        Call.sendMessage(message);
    }

    public void logTag(String tag, String message) {
        Log.infoTag(tag, message);
    }
}