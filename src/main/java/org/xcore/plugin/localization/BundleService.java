package org.xcore.plugin.localization;

import arc.struct.Seq;
import com.ospx.flubundle.BundleContext;
import com.ospx.flubundle.Bundle;
import com.ospx.flubundle.Localizer;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.XcorePlugin;

import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

@Singleton
public class BundleService {

    private final Bundle bundle = Bundle.INSTANCE;

    @PostConstruct
    public void init() {
        bundle.addSource(XcorePlugin.class);
        bundle.addLocaleAlias("uk", "uk_UA");
    }

    public Bundle getBundle() {
        return bundle;
    }

    public Locale locale(Player player) {
        return bundle.resolveLocale(player == null ? null : player.locale);
    }

    public Locale locale(String code) {
        return bundle.resolveLocale(code);
    }

    public Locale locale(Locale locale) {
        return bundle.resolveLocale(locale);
    }

    public String format(Locale locale, String key, Map<String, Object> args) {
        return bundle.format(locale, key, args);
    }

    public Localizer localizer() {
        return bundle.localizer();
    }

    public Localizer localizer(Locale locale) {
        return bundle.localizer(locale);
    }

    public Localizer localizer(Supplier<Locale> localeSupplier) {
        return bundle.localizer(localeSupplier);
    }

    public BundleContext context(Player player) {
        return bundle.context(player);
    }

    public BundleContext context(Player player, Supplier<Locale> localeSupplier) {
        return bundle.context(player, localeSupplier);
    }

    public void send(Player player, String key, Map<String, Object> args) {
        bundle.send(player, key, args);
    }

    public void send(String key, Map<String, Object> args) {
        bundle.send(key, args);
    }

    public Locale getDefaultLocale() {
        return bundle.getDefaultLocale();
    }

    public Seq<Locale> getAvailableLocales() {
        return bundle.getAvailableLocales();
    }
}
