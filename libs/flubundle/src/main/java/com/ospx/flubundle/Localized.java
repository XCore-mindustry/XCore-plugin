package com.ospx.flubundle;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * A message argument whose text depends on the locale the message is rendered in.
 *
 * <p>{@link Bundle} renders it with the locale of the message, so one argument serves every
 * recipient of a broadcast, and {@link com.ospx.flubundle.mindustry.Audience} still renders the
 * message once per locale:
 *
 * <pre>{@code
 * messenger.all().hud("pvp-hud-status", Args.of(
 *         "teams", Localized.by(locale -> teamsLine(alive, locale)),
 *         "time", time));
 * }</pre>
 *
 * <p>A {@link Text} argument is rendered the same way, so a message can embed another one:
 * {@code Args.of("reason", Text.of("ban-reason-griefing"))}.
 */
@FunctionalInterface
public interface Localized {

    /**
     * @param locale the locale the enclosing message is rendered in
     * @return the argument's text in that locale
     */
    String render(Locale locale);

    /** Lets a lambda be used where the target type would otherwise be {@code Object}. */
    static Localized by(Function<Locale, String> render) {
        Objects.requireNonNull(render, "render");
        return render::apply;
    }
}
