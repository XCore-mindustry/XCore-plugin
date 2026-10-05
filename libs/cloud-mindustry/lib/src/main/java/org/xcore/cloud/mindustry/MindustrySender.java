package org.xcore.cloud.mindustry;

import arc.util.Log;
import arc.util.Nullable;
import arc.util.Strings;
import mindustry.gen.Player;

import java.util.Optional;

public interface MindustrySender {
    void sendMessage(String message);
    String name();
    boolean isPlayer();
    @Nullable Player player();

    /**
     * @return the sender for {@code player}, or the console when {@code player} is null
     */
    static MindustrySender of(@Nullable Player player) {
        return player == null ? new ConsoleSender() : new PlayerSender(player);
    }

    default boolean isConsole() {
        return !isPlayer();
    }

    default Optional<Player> optionalPlayer() {
        return Optional.ofNullable(player());
    }

    /**
     * @return true for the console and for players with Mindustry's admin flag
     */
    default boolean isAdmin() {
        Player player = player();
        return player == null ? !isPlayer() : player.admin;
    }

    record PlayerSender(Player player) implements MindustrySender {
        @Override public void sendMessage(String m) { player.sendMessage(m); }
        @Override public String name() { return player.plainName(); }
        @Override public boolean isPlayer() { return true; }
        @Override public @Nullable Player player() { return player; }
    }

    /**
     * The server console. Mindustry color tags mean nothing in a terminal, so they are stripped.
     */
    record ConsoleSender() implements MindustrySender {
        @Override public void sendMessage(String m) { Log.info(Strings.stripColors(m)); }
        @Override public String name() { return "Console"; }
        @Override public boolean isPlayer() { return false; }
        @Override public @Nullable Player player() { return null; }
    }
}
