package org.xcore.plugin.localization;

import org.xcore.plugin.session.Session;

import java.util.Locale;
import java.util.Map;

import static com.ospx.flubundle.Bundle.args;

public class Localization {

    private final BundleService bundle;
    private final Session session;

    public Localization(BundleService bundle, Session session) {
        this.bundle = bundle;
        this.session = session;
    }

    public Locale getLocale() {
        if (session.data.language == null || session.data.language.equals("auto")) {
            return bundle.locale(session.player);
        }
        return bundle.locale(session.data.language);
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
        bundle.send(session.player, key, args);
    }

    public void infoMessage(String key, Map<String, Object> args) {
        bundle.getBundle().infoMessage(session.player, key, args);
    }

    public void announce(String key, Map<String, Object> args) {
        bundle.getBundle().announce(session.player, key, args);
    }

    public void toast(int icon, String key, Map<String, Object> args) {
        bundle.getBundle().toast(session.player, icon, key, args);
    }

    public void setHud(String key, Map<String, Object> args) {
        bundle.getBundle().setHud(session.player, key, args);
    }

    public Localization setLocale(Locale locale) {
        if (session.data.language.equals(locale.getLanguage())) return this;
        session.data.language = locale.getLanguage();
        session.save();
        return this;
    }

    public Localization setLocale(String language) {
        if (session.data.language.equals(language)) return this;
        session.data.language = language;
        session.save();
        return this;
    }

    public Localization resetLocale() {
        if (session.data.language.equals("auto")) return this;
        session.data.language = "auto";
        session.save();
        return this;
    }
}
