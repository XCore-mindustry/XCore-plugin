package com.ospx.flubundle;

import mindustry.gen.Player;

import java.util.Locale;

/**
 * Decides which locale a player should receive messages in.
 *
 * <p>A plugin that lets players pick their language (for example, stored in a session) installs
 * one resolver on the shared {@link Bundle} via {@link Bundle#setLocaleResolver(LocaleResolver)}.
 * Every {@code Bundle#locale(Player)}, {@code Bundle#localizer(Player)} and
 * {@link com.ospx.flubundle.mindustry.Messenger} call then honours that choice, including calls
 * made by other plugins.
 */
@FunctionalInterface
public interface LocaleResolver {

    /** Uses the locale reported by the player's client. */
    LocaleResolver CLIENT = player -> null;

    /**
     * @param player a non-null player
     * @return the requested locale, or {@code null} to fall back to the client locale
     *         ({@code player.locale})
     */
    Locale resolve(Player player);
}
