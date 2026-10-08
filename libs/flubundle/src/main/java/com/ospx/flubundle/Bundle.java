package com.ospx.flubundle;

import arc.files.Fi;
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
import fluent.syntax.ast.Message;
import fluent.syntax.parser.FTLParser;

import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.mod.Mod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static mindustry.Vars.mods;

@SuppressWarnings("unused")
public class Bundle {
    public static Bundle INSTANCE = new Bundle();

    public Locale defaultLocale = Locale.of("en");
    public DefaultValueFactory defaultValueFactory = new NoopDefaultValueFactory();

    private static final int MAX_REPORTED_FORMAT_ERRORS = 512;

    private final Map<Locale, FluentBundle> sources = new ConcurrentHashMap<>();
    private final Map<Locale, Map<String, String>> messageOrigins = new HashMap<>();
    private final Map<String, Locale> localeAliases = new ConcurrentHashMap<>();
    private final FluentFunctionRegistry.Builder registryBuilder = FluentFunctionRegistry.builder();
    private final Set<String> reportedFormatErrors = ConcurrentHashMap.newKeySet();
    private FluentFunctionCache functionCache = LRUFunctionCache.of();
    private FluentFunctionRegistry functionRegistry;
    private volatile LocaleResolver localeResolver = LocaleResolver.CLIENT;
    private volatile Consumer<FluentBundle.ErrorContext> formatErrorHandler = this::logFormatError;

    /**
     * Loads every {@code bundles/*.ftl} file shipped in the jar of the given mod or plugin.
     */
    public void addSource(Class<? extends Mod> main) {
        var mod = mods.getMod(main);
        if (mod == null) {
            throw new IllegalStateException("Could not find mod for " + main.getName());
        }
        addSource(mod.root.child("bundles"), mod.name + ":");
    }

    public void addSource(Fi directory) {
        addSource(directory, "");
    }

    private void addSource(Fi directory, String originPrefix) {
        directory.walk(fi -> {
            if (!fi.extEquals("ftl")) return;

            var localeCode = LocaleCodes.fromFileName(fi.nameWithoutExtension());
            if (localeCode == null) {
                Log.warn("Could not parse locale from file name: " + fi.name());
                return;
            }

            addSource(fi, parseLocaleCode(localeCode), originPrefix + fi.path());
        });
    }

    public void addSource(Fi file, Locale locale) {
        addSource(file, locale, file.path());
    }

    /**
     * Adds an FTL file. Messages already loaded from a different origin are overridden, and the
     * override is logged so that key collisions between plugins are visible.
     */
    private synchronized void addSource(Fi file, Locale locale, String origin) {
        locale = normalizeLocale(locale);
        FluentResource resource = FTLParser.parse(file.readString());

        if (resource.hasErrors()) {
            Log.err("Error parsing " + file.name() + ": ");
            for (var error : resource.errors()) {
                Log.err(error);
            }
        }

        reportOverrides(locale, origin, resource);

        var source = sources.get(locale);

        if (source == null) {
            sources.put(locale, FluentBundle.builder(locale, ensureRegistry(), functionCache)
                    .withLogger(this::onFormatError)
                    .addResource(resource)
                    .build());
            return;
        }

        sources.put(locale, FluentBundle.builderFrom(source, functionCache)
                .withLogger(this::onFormatError)
                .addResourceOverriding(resource)
                .build());
    }

    private void reportOverrides(Locale locale, String origin, FluentResource resource) {
        var origins = messageOrigins.computeIfAbsent(locale, l -> new HashMap<>());
        var overridden = new ArrayList<String>();

        for (var entry : resource.entries()) {
            if (!(entry instanceof Message message)) continue;

            var id = message.identifier().name();
            var previous = origins.put(id, origin);
            if (previous != null && !previous.equals(origin)) {
                overridden.add(id + " (from " + previous + ")");
            }
        }

        if (!overridden.isEmpty()) {
            Log.warn("[FluBundle] @ overrides @ key(s) for locale @: @@", origin, overridden.size(), locale,
                    String.join(", ", overridden.subList(0, Math.min(10, overridden.size()))),
                    overridden.size() > 10 ? ", ..." : "");
        }
    }

    public Seq<Locale> getAvailableLocales() {
        var locales = new Seq<Locale>();
        for (var locale : sources.keySet()) {
            locales.add(locale);
        }
        return locales;
    }

    /**
     * Installs the strategy that picks a player's locale, for example from a language the player
     * selected in settings. Affects every player-bound call on this bundle.
     */
    public void setLocaleResolver(LocaleResolver localeResolver) {
        this.localeResolver = localeResolver == null ? LocaleResolver.CLIENT : localeResolver;
    }

    public LocaleResolver getLocaleResolver() {
        return localeResolver;
    }

    /**
     * Sets the handler for errors that occur while rendering a message, such as a missing
     * {@code $variable} or a failing function. By default each error is logged once.
     */
    public void setFormatErrorHandler(Consumer<FluentBundle.ErrorContext> formatErrorHandler) {
        this.formatErrorHandler = formatErrorHandler == null ? context -> {} : formatErrorHandler;
    }

    /**
     * @return whether the message exists in any loaded locale
     */
    public boolean has(String id) {
        for (var bundle : sources.values()) {
            if (bundle.message(id).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return whether {@link #format(Locale, String, Map)} would find the message for this locale,
     *         including the regional, language and default locale fallbacks
     */
    public boolean has(Locale locale, String id) {
        for (var candidate : localeCandidates(locale == null ? defaultLocale : locale, true)) {
            if (sources.get(candidate).message(id).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the message ids loaded for exactly this locale (after alias resolution)
     */
    public Set<String> keys(Locale locale) {
        var bundle = sources.get(applyAlias(normalizeLocale(locale)));
        return bundle == null ? Collections.emptySet() : Collections.unmodifiableSet(bundle.messages().keySet());
    }

    /**
     * Returns the {@code $variables} a message expects, read from the default locale when possible.
     * Variables of referenced messages are included.
     *
     * @return variable names, or an empty set if the message is unknown
     */
    public Set<String> variables(String id) {
        var ordered = new ArrayList<FluentBundle>();
        for (var candidate : localeCandidates(defaultLocale, true)) {
            ordered.add(sources.get(candidate));
        }
        ordered.addAll(sources.values());

        for (var bundle : ordered) {
            var message = bundle.message(id);
            if (message.isPresent()) {
                return MessageVariables.of(message.get(), key -> bundle.message(key).orElse(null));
            }
        }
        return Collections.emptySet();
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

    public String format(Locale locale, String id) {
        return format(locale, id, Collections.emptyMap(), defaultValueFactory);
    }

    public String format(Locale locale, String id, Map<String, Object> args) {
        return format(locale, id, args, defaultValueFactory);
    }

    /**
     * Formats a message attribute ({@code id.attribute = ...}) using the same locale fallback
     * chain as {@link #format(Locale, String, Map)}.
     */
    public String formatAttribute(Locale locale, String id, String attribute, Map<String, Object> args) {
        ensureRegistry();
        Map<String, Object> safeArgs = args == null ? Collections.emptyMap() : args;
        var requestedLocale = locale == null ? defaultLocale : locale;

        for (var candidate : localeCandidates(requestedLocale, false)) {
            var bundle = sources.get(candidate);
            if (bundle == null) {
                continue;
            }

            var message = bundle.message(id);
            if (message.isPresent() && message.get().hasAttribute(attribute)) {
                return bundle.format(id, attribute, safeArgs);
            }
        }

        return defaultValueFactory.getDefaultValue(id + "." + attribute, safeArgs, normalizeLocale(requestedLocale));
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

    /**
     * Resolves the locale a player receives messages in: the {@link LocaleResolver} choice if any,
     * otherwise the client locale, narrowed to a supported locale.
     */
    public Locale locale(Player player) {
        if (player == null) {
            return resolveLocale((Locale) null);
        }

        var requested = localeResolver.resolve(player);
        return requested != null ? resolveLocale(requested) : resolveLocale(player.locale);
    }

    public Locale locale(String code) {
        return resolveLocale(code);
    }

    public void send(Player player, String id) {
        send(player, id, Collections.emptyMap(), defaultValueFactory);
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

    public void announce(Player player, String id) {
        announce(player, id, Collections.emptyMap(), defaultValueFactory);
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
        broadcast(id, args, defaultValue, Player::sendMessage);
    }

    public void infoMessage(String id, Map<String, Object> args) {
        infoMessage(id, args, defaultValueFactory);
    }

    public void infoMessage(String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        broadcast(id, args, defaultValue, (p, text) -> Call.infoMessage(p.con, text));
    }

    public void setHud(String id, Map<String, Object> args) {
        setHud(id, args, defaultValueFactory);
    }

    public void setHud(String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        broadcast(id, args, defaultValue, (p, text) -> Call.setHudText(p.con, text));
    }

    public void announce(String id, Map<String, Object> args) {
        announce(id, args, defaultValueFactory);
    }

    public void announce(String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        broadcast(id, args, defaultValue, (p, text) -> Call.announce(p.con, text));
    }

    public void toast(int icon, String id, Map<String, Object> args) {
        toast(icon, id, args, defaultValueFactory);
    }

    public void toast(int icon, String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        broadcast(id, args, defaultValue, (p, text) -> Call.warningToast(p.con, icon, text));
    }

    public void label(float duration, float x, float y, String id, Map<String, Object> args) {
        label(duration, x, y, id, args, defaultValueFactory);
    }

    public void label(float duration, float x, float y, String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        broadcast(id, args, defaultValue, (p, text) -> Call.label(p.con, text, duration, x, y));
    }

    public void popup(float duration, int align, int top, int left, int bottom, int right,
                      String id, Map<String, Object> args) {
        popup(duration, align, top, left, bottom, right, id, args, defaultValueFactory);
    }

    public void popup(float duration, int align, int top, int left, int bottom, int right,
                      String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        broadcast(id, args, defaultValue,
                (p, text) -> Call.infoPopup(p.con, text, duration, align, top, left, bottom, right));
    }

    /**
     * Delivers a message to every online player, formatting it once per locale.
     */
    private void broadcast(String id, Map<String, Object> args, DefaultValueFactory defaultValue,
                           BiConsumer<Player, String> delivery) {
        var formatted = new HashMap<Locale, String>();
        Groups.player.each(p -> delivery.accept(p,
                formatted.computeIfAbsent(locale(p), locale -> format(locale, id, args, defaultValue))));
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
            functionRegistry = registryBuilder.build();
        }
        return functionRegistry;
    }

    /**
     * Rebuilds the registry and every loaded bundle after a registration, so that functions and
     * formatters can be registered at any time, including by plugins loaded later.
     */
    private void registryChanged() {
        if (functionRegistry == null) {
            return;
        }

        functionRegistry = registryBuilder.build();
        functionCache = LRUFunctionCache.of();
        sources.replaceAll((locale, bundle) -> FluentBundle.builderFrom(bundle, functionCache)
                .withRegistry(functionRegistry)
                .withLogger(this::onFormatError)
                .build());
    }

    public synchronized Bundle registerFunction(FluentFunctionFactory<?> factory) {
        registryBuilder.addFactory(factory);
        registryChanged();
        return this;
    }

    public synchronized Bundle registerFunctions(Collection<FluentFunctionFactory<?>> factories) {
        registryBuilder.addFactories(factories);
        registryChanged();
        return this;
    }

    public synchronized <T> Bundle registerFormatterExact(Class<T> type, BiFunction<T, Scope, String> formatter) {
        registryBuilder.addDefaultFormatterExact(type, formatter);
        registryChanged();
        return this;
    }

    public synchronized <T> Bundle registerFormatter(Class<T> supertype, BiFunction<T, Scope, String> formatter) {
        registryBuilder.addDefaultFormatter(supertype, formatter);
        registryChanged();
        return this;
    }

    private void onFormatError(FluentBundle.ErrorContext context) {
        formatErrorHandler.accept(context);
    }

    private void logFormatError(FluentBundle.ErrorContext context) {
        var key = context.locale() + "/" + context.entryName();
        if (reportedFormatErrors.size() >= MAX_REPORTED_FORMAT_ERRORS || !reportedFormatErrors.add(key)) {
            return;
        }

        var messages = new ArrayList<String>();
        for (var exception : context.exceptions()) {
            messages.add(exception.getMessage());
        }
        Log.warn("[FluBundle] Error formatting '@' for locale @: @", context.entryName(), context.locale(),
                String.join("; ", messages));
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
        return LocaleCodes.parse(code);
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
        return LocaleCodes.normalize(locale);
    }

    private String normalizeLocaleCode(String code) {
        return LocaleCodes.normalize(code);
    }
}
