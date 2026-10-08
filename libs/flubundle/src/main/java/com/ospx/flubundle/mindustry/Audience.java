package com.ospx.flubundle.mindustry;

import com.ospx.flubundle.Bundle;
import com.ospx.flubundle.Text;
import mindustry.gen.Call;
import mindustry.gen.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A set of players that receive the same message, each in their own locale. A message is
 * rendered once per locale, not once per player.
 *
 * <p>Created by {@link Messenger}. Every delivery method takes either a key, a key with
 * arguments, or a {@link Text}.
 */
public final class Audience {

    private final Bundle bundle;
    private final Supplier<? extends Iterable<? extends Player>> players;

    Audience(Bundle bundle, Supplier<? extends Iterable<? extends Player>> players) {
        this.bundle = bundle;
        this.players = players;
    }

    /** Narrows this audience to the players matching a condition. */
    public Audience filter(Predicate<? super Player> condition) {
        Objects.requireNonNull(condition, "condition");
        var source = players;
        return new Audience(bundle, () -> {
            var matching = new ArrayList<Player>();
            for (Player player : source.get()) {
                if (condition.test(player)) {
                    matching.add(player);
                }
            }
            return matching;
        });
    }

    /** Chat message. */
    public void send(String key) {
        send(Text.of(key));
    }

    public void send(String key, Map<String, Object> args) {
        send(Text.of(key, args));
    }

    public void send(Text text) {
        deliver(text, Player::sendMessage);
    }

    /** Text in the middle of the screen that fades out. */
    public void announce(String key) {
        announce(Text.of(key));
    }

    public void announce(String key, Map<String, Object> args) {
        announce(Text.of(key, args));
    }

    public void announce(Text text) {
        deliver(text, (player, message) -> Call.announce(player.con, message));
    }

    /** Dialog the player has to close. */
    public void infoMessage(String key) {
        infoMessage(Text.of(key));
    }

    public void infoMessage(String key, Map<String, Object> args) {
        infoMessage(Text.of(key, args));
    }

    public void infoMessage(Text text) {
        deliver(text, (player, message) -> Call.infoMessage(player.con, message));
    }

    /** Persistent HUD text, replaced by the next call. */
    public void hud(String key) {
        hud(Text.of(key));
    }

    public void hud(String key, Map<String, Object> args) {
        hud(Text.of(key, args));
    }

    public void hud(Text text) {
        deliver(text, (player, message) -> Call.setHudText(player.con, message));
    }

    /** Warning toast with an icon from {@code mindustry.gen.Iconc}. */
    public void toast(int icon, String key) {
        toast(icon, Text.of(key));
    }

    public void toast(int icon, String key, Map<String, Object> args) {
        toast(icon, Text.of(key, args));
    }

    public void toast(int icon, Text text) {
        deliver(text, (player, message) -> Call.warningToast(player.con, icon, message));
    }

    /** Text shown at world coordinates. */
    public void label(String key, float duration, float x, float y) {
        label(Text.of(key), duration, x, y);
    }

    public void label(String key, Map<String, Object> args, float duration, float x, float y) {
        label(Text.of(key, args), duration, x, y);
    }

    public void label(Text text, float duration, float x, float y) {
        deliver(text, (player, message) -> Call.label(player.con, message, duration, x, y));
    }

    /** Info popup placed on screen. */
    public void popup(String key, Popup popup) {
        popup(Text.of(key), popup);
    }

    public void popup(String key, Map<String, Object> args, Popup popup) {
        popup(Text.of(key, args), popup);
    }

    public void popup(Text text, Popup popup) {
        Objects.requireNonNull(popup, "popup");
        deliver(text, (player, message) -> Call.infoPopup(player.con, message, popup.duration(), popup.align(),
                popup.top(), popup.left(), popup.bottom(), popup.right()));
    }

    /** Disconnects the players with a localized reason. */
    public void kick(String key) {
        kick(Text.of(key));
    }

    public void kick(String key, Map<String, Object> args) {
        kick(Text.of(key, args));
    }

    public void kick(Text text) {
        deliver(text, (player, message) -> player.kick(message));
    }

    /**
     * Renders the text for each player's locale and hands it to a custom transport. The text is
     * formatted once per locale.
     */
    public void deliver(Text text, BiConsumer<? super Player, String> transport) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(transport, "transport");
        var rendered = new HashMap<Locale, String>();
        for (Player player : players.get()) {
            var message = rendered.computeIfAbsent(bundle.locale(player), locale -> bundle.format(locale, text));
            transport.accept(player, message);
        }
    }
}
