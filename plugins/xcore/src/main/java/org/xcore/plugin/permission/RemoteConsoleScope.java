package org.xcore.plugin.permission;

import jakarta.inject.Singleton;

/**
 * Marks the console commands that arrive from another server while they run.
 * <p>
 * A relayed command goes through the same Arc handler as a line typed into the local console,
 * so the only way to tell them apart is to note the origin around the call. Senders read it
 * when they are created. Game thread only.
 */
@Singleton
public class RemoteConsoleScope {

    private Actor.RemoteConsole current;

    public void run(String sourceServer, Runnable command) {
        Actor.RemoteConsole previous = current;
        current = new Actor.RemoteConsole(sourceServer);
        try {
            command.run();
        } finally {
            current = previous;
        }
    }

    /** The console a command issued right now comes from. */
    public Actor consoleActor() {
        return current != null ? current : Actor.LOCAL_CONSOLE;
    }
}
