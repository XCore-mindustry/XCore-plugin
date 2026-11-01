package com.ospx.flubundle;

import mindustry.gen.Player;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Formats messages of a {@link Bundle} in a locale chosen when each message is formatted.
 */
public final class Localizer {

    private final Bundle bundle;
    private final Supplier<Locale> localeSupplier;

    Localizer(Bundle bundle, Supplier<Locale> localeSupplier) {
        this.bundle = Objects.requireNonNull(bundle, "bundle");
        this.localeSupplier = Objects.requireNonNull(localeSupplier, "localeSupplier");
    }

    public Locale locale() {
        return bundle.resolveLocale(localeSupplier.get());
    }

    public String format(String id) {
        return format(id, Collections.emptyMap());
    }

    public String format(String id, Map<String, Object> args) {
        return bundle.format(locale(), id, args);
    }

    public String format(Text text) {
        return format(text.key(), text.args());
    }

    /**
     * @return whether the message exists in the current locale or one of its fallbacks
     */
    public boolean has(String id) {
        return bundle.has(locale(), id);
    }

    public Bundle bundle() {
        return bundle;
    }

    public String formatStrict(String id, Map<String, Object> args) {
        return bundle.formatStrict(locale(), id, args);
    }

    public String format(String id, String defaultValue, Map<String, Object> args) {
        return bundle.format(locale(), id, defaultValue, args);
    }

    public Localizer withLocale(Locale locale) {
        return new Localizer(bundle, () -> locale);
    }

    public Localizer withLocale(Supplier<Locale> nextLocaleSupplier) {
        return new Localizer(bundle, nextLocaleSupplier);
    }

    public Localizer forPlayer(Player player) {
        return new Localizer(bundle, () -> player == null ? bundle.getDefaultLocale() : bundle.locale(player));
    }
}
