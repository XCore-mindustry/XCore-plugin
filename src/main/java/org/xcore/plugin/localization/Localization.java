package org.xcore.plugin.localization;

import com.ospx.flubundle.BundleContext;
import com.ospx.flubundle.Localizer;
import org.xcore.plugin.session.Session;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static com.ospx.flubundle.Bundle.args;

public class Localization {

    private final BundleService bundle;
    private final Session session;
    private final Localizer localizer;
    private final BundleContext context;

    public Localization(BundleService bundle, Session session) {
        this.bundle = bundle;
        this.session = session;
        this.localizer = bundle.localizer(() -> resolveLocale(session));
        this.context = bundle.context(session.player, () -> resolveLocale(session));
    }

    public Localization(BundleService bundle, Locale locale) {
        this.bundle = bundle;
        this.session = null;
        Locale resolvedLocale = bundle.locale(locale);
        this.localizer = bundle.localizer(resolvedLocale);
        this.context = null;
    }

    public Localization(BundleService bundle) {
        this(bundle, (Locale) null);
    }

    public Locale getLocale() {
        return localizer.locale();
    }

    public String getLanguageName(String langCode, String fallbackKey) {
        if (langCode == null || "auto".equals(langCode) || "off".equals(langCode)) {
            return t(fallbackKey);
        }

        Locale loc = bundle.locale(langCode);
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
            context.send(key, args);
        } else {
            bundle.send(key, args);
        }
    }

    public void infoMessage(String key, Map<String, Object> args) {
        if (session != null) {
            context.infoMessage(key, args);
        } else {
            bundle.getBundle().infoMessage(key, args);
        }
    }

    public void announce(String key, Map<String, Object> args) {
        if (session != null) {
            context.announce(key, args);
        } else {
            bundle.getBundle().announce(key, args);
        }
    }

    public void toast(int icon, String key, Map<String, Object> args) {
        if (session != null) {
            context.toast(icon, key, args);
        } else {
            bundle.getBundle().toast(icon, key, args);
        }
    }

    public void setHud(String key, Map<String, Object> args) {
        if (session != null) {
            context.setHud(key, args);
        } else {
            bundle.getBundle().setHud(key, args);
        }
    }

    public Localization setLocale(Locale locale) {
        if (session == null) {return this;}

        Locale resolved = bundle.locale(locale);
        String languageCode = resolved.toString();
        if (Objects.equals(languageCode, session.data.language)) return this;
        session.playerDataRepository.updateLanguage(session.data.uuid, languageCode);
        session.data.language = languageCode;
        return this;
    }

    public Localization setLocale(String language) {
        if (session == null) {return this;}

        if (Objects.equals(language, session.data.language)) return this;
        session.playerDataRepository.updateLanguage(session.data.uuid, language);
        session.data.language = language;
        return this;
    }

    public Localization resetLocale() {
        if (session == null) {return this;}

        if (Objects.equals("auto", session.data.language)) return this;
        session.playerDataRepository.updateLanguage(session.data.uuid, "auto");
        session.data.language = "auto";
        return this;
    }

    private Locale resolveLocale(Session session) {
        if (session == null) {
            return bundle.getDefaultLocale();
        }

        if (session.data.language == null || session.data.language.equals("auto")) {
            return bundle.locale(session.player);
        }

        return bundle.locale(session.data.language);
    }
}
