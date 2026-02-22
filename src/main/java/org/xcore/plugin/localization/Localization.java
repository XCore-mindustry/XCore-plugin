package org.xcore.plugin.localization;

import org.xcore.plugin.session.Session;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static com.ospx.flubundle.Bundle.args;

public class Localization {

    private final BundleService bundle;
    private final Session session;
    public Locale systemLocale;

    public Localization(BundleService bundle, Session session) {
        this.bundle = bundle;
        this.session = session;
        this.systemLocale = null;
    }

    public Localization(BundleService bundle, Locale locale) {
        this.bundle = bundle;
        this.session = null;
        this.systemLocale = locale;
    }

    public Localization(BundleService bundle) {
        this(bundle, (Locale) null);
    }


    public Locale getLocale() {
        if (session == null) {
            return systemLocale != null ? systemLocale : bundle.getDefaultLocale();
        }

        if (session.data.language == null || session.data.language.equals("auto")) {
            return bundle.locale(session.player);
        }
        return session.data.language.equals("uk") ? bundle.locale("uk_UA") : bundle.locale(session.data.language);
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
        return bundle.format(getLocale(), key, args);
    }

    public String format(String key) {
        return bundle.format(getLocale(), key, args());
    }

    public void send(String key) {
        send(key, args());
    }

    public void send(String key, Map<String, Object> args) {
        if (session != null) {
            bundle.send(session.player, key, args);
        } else {
            bundle.send(key, args);
        }
    }

    public void infoMessage(String key, Map<String, Object> args) {
        if (session != null) {
            bundle.getBundle().infoMessage(session.player, key, args);
        } else {
            bundle.getBundle().infoMessage(key, args);
        }
    }

    public void announce(String key, Map<String, Object> args) {
        if (session != null) {
            bundle.getBundle().announce(session.player, key, args);
        } else {
            bundle.getBundle().announce(key, args);
        }
    }

    public void toast(int icon, String key, Map<String, Object> args) {
        if (session != null) {
            bundle.getBundle().toast(session.player, icon, key, args);
        } else {
            bundle.getBundle().toast(icon, key, args);
        }
    }

    public void setHud(String key, Map<String, Object> args) {
        if (session != null) {
            bundle.getBundle().setHud(session.player, key, args);
        } else {
            bundle.getBundle().setHud(key, args);
        }
    }

    public Localization setLocale(Locale locale) {
        if (session == null) {return this;}

        if (Objects.equals(locale.getLanguage(), session.data.language)) return this;
        session.data.language = locale.getLanguage();
        session.save();
        return this;
    }

    public Localization setLocale(String language) {
        if (session == null) {return this;}

        if (Objects.equals(language, session.data.language)) return this;
        session.data.language = language;
        session.save();
        return this;
    }

    public Localization resetLocale() {
        if (session == null) {return this;}

        if (Objects.equals("auto", session.data.language)) return this;
        session.data.language = "auto";
        session.save();
        return this;
    }
}
