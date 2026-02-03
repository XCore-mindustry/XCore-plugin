package org.xcore.cloud.mindustry;

import arc.util.Log;
import arc.util.Nullable;
import mindustry.gen.Player;

public sealed interface MindustrySender {
    void sendMessage(String message);
    String name();
    boolean isPlayer();
    @Nullable Player player();

    record PlayerSender(Player player) implements MindustrySender {
        @Override public void sendMessage(String m) { player.sendMessage(m); }
        @Override public String name() { return player.plainName(); }
        @Override public boolean isPlayer() { return true; }
        @Override public @Nullable Player player() { return player; }
    }

    record ConsoleSender() implements MindustrySender {
        @Override public void sendMessage(String m) { Log.info(m); }
        @Override public String name() { return "Console"; }
        @Override public boolean isPlayer() { return false; }
        @Override public @Nullable Player player() { return null; }
    }
}
