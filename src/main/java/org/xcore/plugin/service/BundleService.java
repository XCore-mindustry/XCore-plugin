package org.xcore.plugin.service;

import com.ospx.flubundle.Bundle;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.XcorePlugin;

import java.util.Locale;
import java.util.Map;

@Singleton
public class BundleService {

    private final Bundle bundle = Bundle.INSTANCE;

    @PostConstruct
    public void init() {
        bundle.addSource(XcorePlugin.class);
        bundle.setDefaultValueFactory(new FallbackDefaultValueFactory(bundle));
    }

    public Bundle getBundle() {
        return bundle;
    }

    public Locale locale(Player player) {
        return bundle.locale(player);
    }

    public Locale locale(String code) {
        return bundle.locale(code);
    }

    public String format(Locale locale, String key, Map<String, Object> args) {
        return bundle.format(locale, key, args);
    }

    public void send(Player player, String key, Map<String, Object> args) {
        bundle.send(player, key, args);
    }

    public void send(String key, Map<String, Object> args) {
        bundle.send(key, args);
    }

    public Locale getDefaultLocale() {
        return bundle.defaultLocale;
    }
}
