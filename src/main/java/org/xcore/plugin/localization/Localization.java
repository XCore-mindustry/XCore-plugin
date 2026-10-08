package org.xcore.plugin.localization;

import com.ospx.flubundle.Bundle;
import com.ospx.flubundle.Localizer;
import com.ospx.flubundle.mindustry.Messenger;
import org.xcore.plugin.session.Session;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static com.ospx.flubundle.Bundle.args;

public class Localization {

    private final Bundle bundle;
    private final Session session;
    private final Localizer localizer;

    public Localization(Bundle bundle, Session session) {
        this.bundle = bundle;
        this.session = session;
        this.localizer = bundle.localizer(() -> resolveLocale(bundle, session));
    }

    public Localization(Bundle bundle, Locale locale) {
        this.bundle = bundle;
        this.session = null;
        Locale resolvedLocale = bundle.resolveLocale(locale);
        this.localizer = bundle.localizer(resolvedLocale);
    }

    public Localization(Bundle bundle) {
        this(bundle, (Locale) null);
    }

    public Bundle bundle() {
        return bundle;
    }

    public Localizer localizer() {
        return localizer;
    }

    public Locale getLocale() {
        return localizer.locale();
    }

    public String getLanguageName(String langCode, String fallbackKey) {
        if (langCode == null || "auto".equals(langCode) || "off".equals(langCode)) {
            return t(fallbackKey);
        }

        Locale loc = bundle.resolveLocale(langCode);
        return arc.util.Strings.capitalize(loc.getDisplayLanguage(loc));
    }

    public String t(String key, Map<String, Object> args) {
        return format(key, args);
    }

    public String t(String key) {
        return format(key);
    }

    public String format(String key, Map<String, Object> args) {
        if (session == null) {
            return bundle.format(getLocale(), key, args);
        }
        return localizer.format(key, args);
    }

    public String format(String key) {
        return format(key, args());
    }

    public void send(String key) {
        send(key, args());
    }

    public void send(String key, Map<String, Object> args) {
        if (session != null) {
            session.player.sendMessage(localizer.format(key, args));
        } else {
            Messenger.of(bundle).all().send(key, args);
        }
    }

    public Localization setLocale(Locale locale) {
        if (session == null) {return this;}

        Locale resolved = bundle.resolveLocale(locale);
        String languageCode = resolved.toString();
        if (Objects.equals(languageCode, session.data.language)) return this;
        session.updateLanguage(languageCode);
        return this;
    }

    public Localization setLocale(String language) {
        if (session == null) {return this;}

        if (Objects.equals(language, session.data.language)) return this;
        session.updateLanguage(language);
        return this;
    }

    public Localization resetLocale() {
        if (session == null) {return this;}

        if (Objects.equals("auto", session.data.language)) return this;
        session.updateLanguage("auto");
        return this;
    }

    private static Locale resolveLocale(Bundle bundle, Session session) {
        if (session == null) {
            return bundle.getDefaultLocale();
        }

        Locale selected = SessionLocaleResolver.selectedLocale(session);
        return selected != null ? bundle.resolveLocale(selected) : bundle.locale(session.player);
    }
}
