package com.ospx.flubundle;

import arc.files.Fi;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;

import com.ospx.flubundle.functions.ColorFunction;
import com.ospx.flubundle.functions.DurationFunction;
import com.ospx.flubundle.functions.StripFunction;

import fluent.bundle.FluentBundle;
import fluent.bundle.FluentFunctionCache;
import fluent.bundle.FluentFunctionRegistry;
import fluent.bundle.FluentResource;
import fluent.bundle.LRUFunctionCache;
import fluent.bundle.resolver.Scope;
import fluent.function.FluentFunctionFactory;
import fluent.function.functions.DefaultFunctionFactories;
import fluent.syntax.parser.FTLParser;

import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.mod.Mod;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;

import static mindustry.Vars.mods;

@SuppressWarnings("unused")
public class Bundle {
    public static Bundle INSTANCE = new Bundle();

    public Locale defaultLocale = Locale.of("en");
    public DefaultValueFactory defaultValueFactory = new NoopDefaultValueFactory();

    private final ObjectMap<Locale, FluentBundle> sources = new ObjectMap<>();
    private final Map<String, Locale> localeAliases = new HashMap<>();
    private final FluentFunctionRegistry.Builder registryBuilder = FluentFunctionRegistry.builder();
    private final FluentFunctionCache functionCache = LRUFunctionCache.of();
    private FluentFunctionRegistry functionRegistry;
    private boolean frozen = false;

    public void addSource(Class<? extends Mod> main) {
        addSource(mods.getMod(main).root.child("bundles"));
    }

    public void addSource(Fi directory) {
        directory.walk(fi -> {
            if (!fi.extEquals("ftl")) return;

            var name = fi.nameWithoutExtension();

            String localeCode;
            int lastUnderscore = name.lastIndexOf('_');
            if (lastUnderscore == -1) {
                Log.warn("Could not parse locale from file name: " + fi.name());
                return;
            }

            int secondLastUnderscore = name.lastIndexOf('_', lastUnderscore - 1);

            if (secondLastUnderscore != -1 && lastUnderscore - secondLastUnderscore == 3) {
                localeCode = name.substring(secondLastUnderscore + 1);
            } else {
                localeCode = name.substring(lastUnderscore + 1);
            }

            addSource(fi, parseLocaleCode(localeCode));
        });
    }

    public void addSource(Fi file, Locale locale) {
        locale = normalizeLocale(locale);
        FluentResource resource = FTLParser.parse(file.readString());

        if (resource.hasErrors()) {
            Log.err("Error parsing " + file.name() + ": ");
            for (var error : resource.errors()) {
                Log.err(error);
            }
        }

        var source = sources.get(locale);

        if (source == null) {
            sources.put(locale, FluentBundle.builder(locale, ensureRegistry(), functionCache)
                    .addResource(resource)
                    .build());
            return;
        }

        sources.put(locale, FluentBundle.builderFrom(source, functionCache)
                .addResourceOverriding(resource)
                .build());
    }

    public Seq<Locale> getAvailableLocales() {
        return sources.keys().toSeq();
    }

    public Locale getDefaultLocale() {
        return defaultLocale;
    }

    public DefaultValueFactory getDefaultValueFactory() {
        return defaultValueFactory;
    }

    public Bundle addLocaleAlias(String alias, String targetCode) {
        var normalizedAlias = normalizeLocaleCode(alias);
        var normalizedTarget = normalizeLocaleCode(targetCode);

        if (normalizedAlias == null) {
            throw new IllegalArgumentException("Alias locale code must not be blank");
        }
        if (normalizedTarget == null) {
            throw new IllegalArgumentException("Target locale code must not be blank");
        }

        localeAliases.put(normalizedAlias, parseLocaleCode(normalizedTarget));
        return this;
    }

    public Bundle addLocaleAlias(String alias, Locale target) {
        var normalizedAlias = normalizeLocaleCode(alias);
        if (normalizedAlias == null) {
            throw new IllegalArgumentException("Alias locale code must not be blank");
        }

        localeAliases.put(normalizedAlias, normalizeLocale(target));
        return this;
    }

    public Locale normalizeLocale(Locale locale) {
        var normalizedCode = normalizeLocaleCode(locale);
        if (normalizedCode == null) {
            return defaultLocale;
        }

        return parseLocaleCode(normalizedCode);
    }

    public Locale resolveLocale(String code) {
        return resolveLocale(parseLocaleCodeOrNull(code));
    }

    public Locale resolveLocale(Locale locale) {
        var defaultCandidate = applyAlias(normalizeLocale(defaultLocale));
        if (locale == null) {
            return defaultCandidate;
        }

        for (var candidate : localeCandidates(locale, true)) {
            if (sources.containsKey(candidate)) {
                return candidate;
            }
        }

        return defaultCandidate;
    }

    public Localizer localizer() {
        return new Localizer(this, () -> defaultLocale);
    }

    public Localizer localizer(Locale locale) {
        return new Localizer(this, () -> locale);
    }

    public Localizer localizer(java.util.function.Supplier<Locale> localeSupplier) {
        return new Localizer(this, localeSupplier);
    }

    public Localizer localizer(Player player) {
        return new Localizer(this, () -> player == null ? defaultLocale : locale(player));
    }

    public BundleContext context(Player player) {
        return new BundleContext(player, localizer(player));
    }

    public BundleContext context(Player player, Locale locale) {
        return new BundleContext(player, localizer(locale));
    }

    public BundleContext context(Player player, java.util.function.Supplier<Locale> localeSupplier) {
        return new BundleContext(player, localizer(localeSupplier));
    }

    public String format(Locale locale, String id, Map<String, Object> args) {
        return format(locale, id, args, defaultValueFactory);
    }

    public String format(Locale locale, String id, String defaultValue, Map<String, Object> args) {
        return format(locale, id, args, (k, a, l) -> defaultValue);
    }

    public String format(Locale locale, String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        ensureRegistry();
        Map<String, Object> safeArgs = args == null ? Collections.emptyMap() : args;
        var requestedLocale = locale == null ? defaultLocale : locale;

        for (var candidate : localeCandidates(requestedLocale, false)) {
            var bundle = sources.get(candidate);
            if (bundle == null) {
                continue;
            }

            if (bundle.message(id).isPresent()) {
                return bundle.format(id, safeArgs);
            }
        }

        return defaultValue.getDefaultValue(id, safeArgs, normalizeLocale(requestedLocale));
    }

    public String formatStrict(Locale locale, String id, Map<String, Object> args) {
        return formatStrict(locale, id, args, defaultValueFactory);
    }

    public String formatStrict(Locale locale, String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        ensureRegistry();
        var requestedLocale = applyAlias(normalizeLocale(locale));
        var bundle = sources.get(requestedLocale);

        if (bundle == null) {
            throw new RuntimeException("No bundle for locale " + requestedLocale);
        }

        Map<String, Object> safeArgs = args == null ? Collections.emptyMap() : args;
        if (bundle.message(id).isPresent()) {
            return bundle.format(id, safeArgs);
        }

        return defaultValue.getDefaultValue(id, safeArgs, requestedLocale);
    }

    public Locale locale(Player player) {
        return player == null ? resolveLocale((Locale) null) : resolveLocale(player.locale);
    }

    public Locale locale(String code) {
        return resolveLocale(code);
    }

    public void send(Player player, String id, Map<String, Object> args) {
        send(player, id, args, defaultValueFactory);
    }

    public void send(Player player, String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        player.sendMessage(format(locale(player), id, args, defaultValue));
    }

    public void infoMessage(Player player, String id, Map<String, Object> args) {
        infoMessage(player, id, args, defaultValueFactory);
    }

    public void infoMessage(Player player, String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        Call.infoMessage(player.con, format(locale(player), id, args, defaultValue));
    }

    public void setHud(Player player, String id, Map<String, Object> args) {
        setHud(player, id, args, defaultValueFactory);
    }

    public void setHud(Player player, String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        Call.setHudText(player.con, format(locale(player), id, args, defaultValue));
    }

    public void announce(Player player, String id, Map<String, Object> args) {
        announce(player, id, args, defaultValueFactory);
    }

    public void announce(Player player, String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        Call.announce(player.con, format(locale(player), id, args, defaultValue));
    }

    public void toast(Player player, int icon, String id, Map<String, Object> args) {
        toast(player, icon, id, args, defaultValueFactory);
    }

    public void toast(Player player, int icon, String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        Call.warningToast(player.con, icon, format(locale(player), id, args, defaultValue));
    }

    public void label(Player player, float duration, float x, float y, String id, Map<String, Object> args) {
        label(player, duration, x, y, id, args, defaultValueFactory);
    }

    public void label(Player player, float duration, float x, float y, String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        Call.label(player.con, format(locale(player), id, args, defaultValue), duration, x, y);
    }

    public void popup(Player player, float duration, int align, int top, int left, int bottom, int right,
                      String id, Map<String, Object> args) {
        popup(player, duration, align, top, left, bottom, right, id, args, defaultValueFactory);
    }

    public void popup(Player player, float duration, int align, int top, int left, int bottom, int right,
                      String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        Call.infoPopup(player.con, format(locale(player), id, args, defaultValue), duration, align, top, left, bottom, right);
    }

    public void kick(Player player, String id, Map<String, Object> args) {
        kick(player, id, args, defaultValueFactory);
    }

    public void kick(Player player, String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        Call.kick(player.con, format(locale(player), id, args, defaultValue));
    }

    public void send(String id, Map<String, Object> args) {
        send(id, args, defaultValueFactory);
    }

    public void send(String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        Groups.player.each(p -> send(p, id, args, defaultValue));
    }

    public void infoMessage(String id, Map<String, Object> args) {
        infoMessage(id, args, defaultValueFactory);
    }

    public void infoMessage(String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        Groups.player.each(p -> infoMessage(p, id, args, defaultValue));
    }

    public void setHud(String id, Map<String, Object> args) {
        setHud(id, args, defaultValueFactory);
    }

    public void setHud(String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        Groups.player.each(p -> setHud(p, id, args, defaultValue));
    }

    public void announce(String id, Map<String, Object> args) {
        announce(id, args, defaultValueFactory);
    }

    public void announce(String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        Groups.player.each(p -> announce(p, id, args, defaultValue));
    }

    public void toast(int icon, String id, Map<String, Object> args) {
        toast(icon, id, args, defaultValueFactory);
    }

    public void toast(int icon, String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        Groups.player.each(p -> toast(p, icon, id, args, defaultValue));
    }

    public void label(float duration, float x, float y, String id, Map<String, Object> args) {
        label(duration, x, y, id, args, defaultValueFactory);
    }

    public void label(float duration, float x, float y, String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        Groups.player.each(p -> label(p, duration, x, y, id, args, defaultValue));
    }

    public void popup(float duration, int align, int top, int left, int bottom, int right,
                      String id, Map<String, Object> args) {
        popup(duration, align, top, left, bottom, right, id, args, defaultValueFactory);
    }

    public void popup(float duration, int align, int top, int left, int bottom, int right,
                      String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        Groups.player.each(p -> popup(p, duration, align, top, left, bottom, right, id, args, defaultValue));
    }

    public static Map<String, Object> args(Object... values) {
        if (values.length == 0) return Collections.emptyMap();

        if (values.length % 2 != 0) {
            throw new IllegalArgumentException("Odd number of arguments");
        }

        var map = new HashMap<String, Object>();

        for (int i = 0; i < values.length; i += 2) {
            if (!(values[i] instanceof String)) {
                throw new IllegalArgumentException("Key must be a string");
            }

            map.put((String) values[i], values[i + 1]);
        }

        return map;
    }

    public static Map<String, Object> numArgs(Object... values) {
        var map = new HashMap<String, Object>();

        for (int i = 0; i < values.length; i++) {
            map.put("a"+i, values[i]);
        }

        return map;
    }

    public void setDefaultValueFactory(DefaultValueFactory defaultValueFactory) {
        this.defaultValueFactory = defaultValueFactory;
    }

    public void setDefaultLocale(Locale defaultLocale) {
        this.defaultLocale = normalizeLocale(defaultLocale);
    }

    private void initDefaults() {
        registryBuilder.addFactories(DefaultFunctionFactories.allNonImplicits());
        registryBuilder.addFactory(StripFunction.STRIP);
        registryBuilder.addFactory(ColorFunction.COLOR);
        registryBuilder.addFactory(DurationFunction.DURATION);
        registryBuilder.addDefaultFormatterExact(Player.class, (player, scope) -> player.name);
        registryBuilder.addDefaultFormatterExact(Team.class, (team, scope) -> team.name);
    }

    private synchronized FluentFunctionRegistry ensureRegistry() {
        if (functionRegistry == null) {
            frozen = true;
            functionRegistry = registryBuilder.build();
        }
        return functionRegistry;
    }

    private synchronized void checkNotFrozen() {
        if (frozen) {
            throw new IllegalStateException("Functions and formatters must be registered before adding bundle sources or formatting.");
        }
    }

    public synchronized Bundle registerFunction(FluentFunctionFactory<?> factory) {
        checkNotFrozen();
        registryBuilder.addFactory(factory);
        return this;
    }

    public synchronized Bundle registerFunctions(Collection<FluentFunctionFactory<?>> factories) {
        checkNotFrozen();
        registryBuilder.addFactories(factories);
        return this;
    }

    public synchronized <T> Bundle registerFormatterExact(Class<T> type, BiFunction<T, Scope, String> formatter) {
        checkNotFrozen();
        registryBuilder.addDefaultFormatterExact(type, formatter);
        return this;
    }

    public synchronized <T> Bundle registerFormatter(Class<T> supertype, BiFunction<T, Scope, String> formatter) {
        checkNotFrozen();
        registryBuilder.addDefaultFormatter(supertype, formatter);
        return this;
    }

    public Bundle() {
        initDefaults();
    }

    public Bundle(Locale defaultLocale) {
        this();
        setDefaultLocale(defaultLocale);
    }

    private Iterable<Locale> localeCandidates(Locale locale, boolean includeOnlySupported) {
        var requested = applyAlias(normalizeLocale(locale));
        var defaultCandidate = applyAlias(normalizeLocale(defaultLocale));

        var candidates = new LinkedHashSet<Locale>();
        candidates.add(requested);

        var languageFallback = languageLocale(requested);
        if (!languageFallback.equals(requested)) {
            candidates.add(languageFallback);
        }

        candidates.add(defaultCandidate);

        var defaultLanguageFallback = languageLocale(defaultCandidate);
        if (!defaultLanguageFallback.equals(defaultCandidate)) {
            candidates.add(defaultLanguageFallback);
        }

        if (!includeOnlySupported) {
            return candidates;
        }

        var filtered = new LinkedHashSet<Locale>();
        for (var candidate : candidates) {
            if (sources.containsKey(candidate)) {
                filtered.add(candidate);
            }
        }
        return filtered;
    }

    private Locale applyAlias(Locale locale) {
        var normalizedCode = normalizeLocaleCode(locale);
        if (normalizedCode == null) {
            return normalizeLocale(defaultLocale);
        }

        return localeAliases.getOrDefault(normalizedCode, locale);
    }

    private Locale languageLocale(Locale locale) {
        var language = locale.getLanguage();
        if (language == null || language.isBlank()) {
            return normalizeLocale(defaultLocale);
        }
        return Locale.of(language.toLowerCase(Locale.ROOT));
    }

    private Locale parseLocaleCodeOrNull(String code) {
        var normalizedCode = normalizeLocaleCode(code);
        return normalizedCode == null ? null : parseLocaleCode(normalizedCode);
    }

    private Locale parseLocaleCode(String code) {
        var normalizedCode = normalizeLocaleCode(code);
        if (normalizedCode == null) {
            return normalizeLocale(defaultLocale);
        }

        var codes = normalizedCode.split("_");
        return codes.length >= 2 ? Locale.of(codes[0], codes[1]) : Locale.of(codes[0]);
    }

    private String normalizeLocaleCode(Locale locale) {
        if (locale == null) {
            return null;
        }

        var language = locale.getLanguage();
        if (language == null || language.isBlank()) {
            return null;
        }

        var country = locale.getCountry();
        if (country == null || country.isBlank()) {
            return language.toLowerCase(Locale.ROOT);
        }

        return language.toLowerCase(Locale.ROOT) + "_" + country.toUpperCase(Locale.ROOT);
    }

    private String normalizeLocaleCode(String code) {
        if (code == null) {
            return null;
        }

        var normalized = code.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        normalized = normalized.replace('-', '_');
        var codes = normalized.split("_");
        if (codes.length == 0 || codes[0].isBlank()) {
            return null;
        }

        if (codes.length == 1 || codes[1].isBlank()) {
            return codes[0].toLowerCase(Locale.ROOT);
        }

        return codes[0].toLowerCase(Locale.ROOT) + "_" + codes[1].toUpperCase(Locale.ROOT);
    }
}
