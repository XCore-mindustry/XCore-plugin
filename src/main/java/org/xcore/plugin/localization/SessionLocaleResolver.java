package org.xcore.plugin.localization;

import com.ospx.flubundle.Bundle;
import com.ospx.flubundle.LocaleCodes;
import com.ospx.flubundle.LocaleResolver;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.Locale;

/**
 * Makes the shared {@link Bundle} honour the language a player selected in settings, so every
 * {@code bundle.locale(player)} / {@code messenger.to(player).send(...)} call, including the ones made by
 * other plugins, uses it instead of the client locale.
 */
@Singleton
public class SessionLocaleResolver implements LocaleResolver {

    private final Bundle bundle;
    private final SessionService sessionService;

    @Inject
    public SessionLocaleResolver(Bundle bundle, SessionService sessionService) {
        this.bundle = bundle;
        this.sessionService = sessionService;
    }

    @PostConstruct
    public void install() {
        bundle.setLocaleResolver(this);
    }

    @Override
    public Locale resolve(Player player) {
        return selectedLocale(sessionService.get(player));
    }

    /**
     * The language the player selected in settings, or {@code null} when there is no session or the
     * selection is {@code auto} (use the client locale).
     */
    public static Locale selectedLocale(Session session) {
        if (session == null || session.data == null) {
            return null;
        }

        String language = session.data.language;
        if (language == null || "auto".equals(language)) {
            return null;
        }
        return LocaleCodes.parse(language);
    }
}
