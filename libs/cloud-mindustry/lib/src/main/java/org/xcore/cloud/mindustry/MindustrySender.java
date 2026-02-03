package org.xcore.cloud.mindustry;

import arc.util.Nullable;

import mindustry.gen.Player;

public sealed interface MindustrySender {
    void sendMessage(String message);
    boolean isPlayer();
    @Nullable Player player();

    record PlayerSender(Player player) implements MindustrySender {
        @Override public void sendMessage(String m) { player.sendMessage(m); }
        @Override public boolean isPlayer() { return true; }
        @Override public @Nullable Player player() { return player; }
    }

    record ConsoleSender() implements MindustrySender {
        @Override public void sendMessage(String m) { arc.util.Log.info(m); }
        @Override public boolean isPlayer() { return false; }
        @Override public @Nullable Player player() { return null; }
    }
}