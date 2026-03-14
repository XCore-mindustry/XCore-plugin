package org.xcore.plugin.localization;

import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.xcore.plugin.session.Session;

import java.util.Locale;
import java.util.Objects;

@Singleton
public class LocalizationFactory {

    private final Provider<BundleService> bundleService;

    public LocalizationFactory(Provider<BundleService> bundleService) {
        this.bundleService = bundleService;
    }

    public Localization system() {
        return new Localization(bundleService.get());
    }

    public Localization forLocale(Locale locale) {
        return new Localization(bundleService.get(), Objects.requireNonNullElse(locale, bundleService.get().getDefaultLocale()));
    }

    public Localization forSession(Session session) {
        return new Localization(bundleService.get(), session);
    }
}
