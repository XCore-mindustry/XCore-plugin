package org.xcore.plugin.ui.kit;

import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.session.Session;
import org.xcore.ui.LocalizerResolver;

import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The texts of a menu in the language of the player it is open for.
 *
 * <p>A menu with no localization, one built without a session as in the tests, shows the key
 * itself: that is what the bundle shows for a text it lacks, so either way a missing text names
 * what is missing, and the plugin's English stays in one place, its bundle. A fallback is for a
 * text that has a form of its own without words: a name the data carries (a rank, a badge, a
 * league) or a number with its unit. It is not for a copy of the English text.
 */
public final class Texts {

    private Texts() {
    }

    /**
     * The words of a player's language for the library: what {@link org.xcore.ui.Text#t} and
     * {@link org.xcore.ui.runtime.RenderContext#t} resolve against. The localization is asked for
     * on every use, so a player who changes language is spoken to in the new one at the next
     * render; without one the key itself is shown.
     */
    public static LocalizerResolver resolver(Supplier<Localization> localization) {
        return new LocalizerResolver() {
            @Override
            public String format(String key, Map<String, Object> args) {
                Localization local = localization.get();
                return local != null ? local.format(key, args) : key;
            }

            @Override
            public String format(String key) {
                Localization local = localization.get();
                return local != null ? local.format(key) : key;
            }

            @Override
            public boolean has(String key) {
                Localization local = localization.get();
                return local != null && local.bundle().has(local.getLocale(), key);
            }

            @Override
            public Locale locale() {
                Localization local = localization.get();
                return local != null ? local.getLocale() : Locale.ENGLISH;
            }
        };
    }

    /** {@link #resolver(Supplier)} for a localization that does not change. */
    public static LocalizerResolver resolver(Localization localization) {
        return resolver(() -> localization);
    }

    /** The localization of {@code session}; none without a session. */
    public static Localization locale(Session session) {
        return session != null ? session.locale() : null;
    }

    /** The text of {@code key}; the key itself without a localization. */
    public static String t(Localization local, String key) {
        return local != null ? local.t(key) : key;
    }

    /** The text of {@code key} filled in with {@code args}; the key itself without a localization. */
    public static String t(Localization local, String key, Map<String, Object> args) {
        return local != null ? local.t(key, args) : key;
    }

    /** The text of {@code key}; {@code fallback} without a localization. */
    public static String t(Localization local, String key, String fallback) {
        return local != null ? local.t(key) : fallback;
    }

    /** The text of {@code key} filled in with {@code args}; {@code fallback} without a localization. */
    public static String t(Localization local, String key, Map<String, Object> args, String fallback) {
        return local != null ? local.t(key, args) : fallback;
    }

    /** The text of {@code key} for the player of {@code session}; the key itself without one. */
    public static String t(Session session, String key) {
        return t(locale(session), key);
    }

    /** The text of {@code key} filled in with {@code args} for the player of {@code session}; the key itself without one. */
    public static String t(Session session, String key, Map<String, Object> args) {
        return t(locale(session), key, args);
    }
}
