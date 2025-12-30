package com.ospx.flubundle;

import arc.files.Fi;
import arc.struct.ObjectMap;
import arc.util.Log;

import fluent.bundle.FluentBundle;
import fluent.bundle.FluentResource;
import fluent.functions.cldr.CLDRFunctionFactory;
import fluent.syntax.parser.FTLParser;
import fluent.syntax.parser.FTLStream;

import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.mod.Mod;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static mindustry.Vars.mods;

@SuppressWarnings("unused")
public class Bundle {
    public static Bundle INSTANCE = new Bundle();

    public Locale defaultLocale = new Locale("en");
    public DefaultValueFactory defaultValueFactory = new NoopDefaultValueFactory();

    private final ObjectMap<Locale, FluentBundle> sources = new ObjectMap<>();

    /**
     * Adds all bundles from the mod.
     * From the bundles folder inside mod root.
     * @param main The main class of the mod
     */
    public void addSource(Class<? extends Mod> main) {
        addSource(mods.getMod(main).root.child("bundles"));
    }

    /**
     * Adds all bundles from the directory.
     * @param directory The directory to add bundles from
     */
    public void addSource(Fi directory) {
        directory.walk(fi -> {
            if (!fi.extEquals("ftl")) return;

            var name = fi.nameWithoutExtension();
            var localeCode = name.startsWith("bundle_") ? name.substring(7) : name;
            var codes = localeCode.split("_");
            
            var locale = codes.length >= 2 ? new Locale(codes[0], codes[1]) : new Locale(codes[0]);

            addSource(fi, locale);
        });
    }

    /**
     * Adds a bundle from the file.
     * @param file The file to add bundle from
     * @param locale The locale of the bundle
     */
    public void addSource(Fi file, Locale locale) {
        FluentResource resource = FTLParser.parse(FTLStream.of(file.readString()));

        if (resource.hasErrors()) {
            Log.err("Error parsing " + file.name() + ": ");
            for (var error : resource.errors()) {
                Log.err(error);
            }
        }

        var source = sources.get(locale);

        if (source == null) {
            sources.put(locale, FluentBundle.builder(locale, CLDRFunctionFactory.INSTANCE)
                    .addResource(resource)
                    .build());
            return;
        }

        sources.put(locale, FluentBundle.builderFrom(source)
                        .withFunctionFactory(CLDRFunctionFactory.INSTANCE)
                        .addResourceOverriding(resource)
                .build());
    }

    /**
     * Formats the message with the given id.
     * @param locale The locale to format the message with
     * @param id The id of the message
     * @param args The arguments to format the message with
     * @return Formatted message
     */
    public String format(Locale locale, String id, Map<String, Object> args) {
        return format(locale, id, args, defaultValueFactory);
    }

    /**
     * Formats the message with the given id.
     * @param locale The locale to format the message with
     * @param id The id of the message
     * @param args The arguments to format the message with
     * @param defaultValue The default value to return if the message is not found
     * @return Formatted message
     */
    public String format(Locale locale, String id, String defaultValue, Map<String, Object> args) {
        return format(locale, id, args, (k, a, l) -> defaultValue);
    }

    /**
     * Formats the message with the given id.
     * @param locale The locale to format the message with
     * @param id The id of the message
     * @param args The arguments to format the message with
     * @param defaultValue The default value to return if the message is not found.
     * @return Formatted message
     */
    public String format(Locale locale, String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        var bundle = sources.get(locale);

        if (bundle == null) {
            throw new RuntimeException("No bundle for locale " + locale);
        }

        var message = bundle.getMessage(id);
        if (message.isPresent()) {
            return bundle.format(id, args == null ? Collections.emptyMap() : args);
        } else {
            return defaultValue.getDefaultValue(id, args, locale);
        }
    }

    public Locale locale(Player player) {
        return locale(player.locale);
    }

public Locale locale(String code) {
        if (code == null) return defaultLocale;
        
        var codes = code.split("_");
        var locale = codes.length >= 2 ? new Locale(codes[0], codes[1]) : new Locale(codes[0]);
        
        return sources.containsKey(locale) ? locale : defaultLocale;
    }

    // region single
    /**
     * Sends a message to the player.
     * <p>
     * Locale is taken from the {@code Player.locale}
     * <p>
     * Default value is taken from {@link #defaultValueFactory}
     * @param player The player to send the message to
     * @param id The id of the message
     * @param args The arguments to format the message with
     */
    public void send(Player player, String id, Map<String, Object> args) {
        send(player, id, args, defaultValueFactory);
    }
    /**
     * Sends a message to the player.
     * <p>
     * Locale is taken from the {@code Player.locale}
     * @param player The player to send the message to
     * @param id The id of the message
     * @param args The arguments to format the message with
     * @param defaultValue The default value to return if the message is not found
     */
    public void send(Player player, String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        player.sendMessage(format(locale(player), id, args, defaultValue));
    }

    /**
     * Sends a infoMessage to the player.
     * <p>
     * Locale is taken from the {@code Player.locale}
     * <p>
     * Default value is taken from {@link #defaultValueFactory}
     * @param player The player to send the message to
     * @param id The id of the message
     * @param args The arguments to format the message with
     */
    public void infoMessage(Player player, String id, Map<String, Object> args) {
        infoMessage(player, id, args, defaultValueFactory);
    }
    /**
     * Sends a infoMessage to the player.
     * <p>
     * Locale is taken from the {@code Player.locale}
     * @param player The player to send the message to
     * @param id The id of the message
     * @param args The arguments to format the message with
     * @param defaultValue The default value to return if the message is not found
     */
    public void infoMessage(Player player, String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        Call.infoMessage(format(locale(player), id, args, defaultValue));
    }

    /**
     * Sets the HUD text of the player.
     * <p>
     * Locale is taken from the {@code Player.locale}
     * <p>
     * Default value is taken from {@link #defaultValueFactory}
     * @param player The player to set the HUD text to
     * @param id The id of the message
     * @param args The arguments to format the message with
     */
    public void setHud(Player player, String id, Map<String, Object> args) {
        setHud(player, id, args, defaultValueFactory);
    }
    /**
     * Sets the HUD text of the player.
     * <p>
     * Locale is taken from the {@code Player.locale}
     * @param player The player to set the HUD text to
     * @param id The id of the message
     * @param args The arguments to format the message with
     * @param defaultValue The default value to return if the message is not found
     */
    public void setHud(Player player, String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        Call.setHudText(player.con, format(locale(player), id, args, defaultValue));
    }

    /**
     * Announces a message to the player.
     * <p>
     * Locale is taken from the {@code Player.locale}
     * <p>
     * Default value is taken from {@link #defaultValueFactory}
     * @param player The player to announce the message to
     * @param id The id of the message
     * @param args The arguments to format the message with
     */
    public void announce(Player player, String id, Map<String, Object> args) {
        announce(player, id, args, defaultValueFactory);
    }
    /**
     * Announces a message to the player.
     * <p>
     * Locale is taken from the {@code Player.locale}
     * @param player The player to announce the message to
     * @param id The id of the message
     * @param args The arguments to format the message with
     * @param defaultValue The default value to return if the message is not found
     */
    public void announce(Player player, String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        Call.announce(player.con, format(locale(player), id, args, defaultValue));
    }

    /**
     * Shows a toast to the player.
     * <p>
     * Locale is taken from the {@code Player.locale}
     * <p>
     * Default value is taken from {@link #defaultValueFactory}
     * @param player The player to show the toast to
     * @param icon The icon of the toast
     * @param id The id of the message
     * @param args The arguments to format the message with
     */
    public void toast(Player player, int icon, String id, Map<String, Object> args) {
        toast(player, icon, id, args, defaultValueFactory);
    }
    /**
     * Shows a toast to the player.
     * <p>
     * Locale is taken from the {@code Player.locale}
     * @param player The player to show the toast to
     * @param icon The icon of the toast
     * @param id The id of the message
     * @param args The arguments to format the message with
     * @param defaultValue The default value to return if the message is not found
     */
    public void toast(Player player, int icon, String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        Call.warningToast(player.con, icon, format(locale(player), id, args, defaultValue));
    }

    /**
     * Shows a label to the player.
     * <p>
     * Locale is taken from the {@code Player.locale}
     * <p>
     * Default value is taken from {@link #defaultValueFactory}
     * @param player The player to show the label to
     * @param duration The duration of the label
     * @param x The x position of the label
     * @param y The y position of the label
     * @param id The id of the message
     * @param args The arguments to format the message with
     */
    public void label(Player player, float duration, float x, float y, String id, Map<String, Object> args) {
        label(player, duration, x, y, id, args, defaultValueFactory);
    }
    /**
     * Shows a label to the player.
     * <p>
     * Locale is taken from the {@code Player.locale}
     * @param player The player to show the label to
     * @param duration The duration of the label
     * @param x The x position of the label
     * @param y The y position of the label
     * @param id The id of the message
     * @param args The arguments to format the message with
     * @param defaultValue The default value to return if the message is not found
     */
    public void label(Player player, float duration, float x, float y, String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        Call.label(player.con, format(locale(player), id, args, defaultValue), duration, x, y);
    }

    /**
     * Shows a popup to the player.
     * <p>
     * Locale is taken from the {@code Player.locale}
     * <p>
     * Default value is taken from {@link #defaultValueFactory}
     * @param player The player to show the popup to
     * @param duration The duration of the popup
     * @param align The alignment of the popup
     * @param top The top position of the popup
     * @param left The left position of the popup
     * @param bottom The bottom position of the popup
     * @param right The right position of the popup
     * @param id The id of the message
     * @param args The arguments to format the message with
     */
    public void popup(Player player, float duration, int align, int top, int left, int bottom, int right,
                             String id, Map<String, Object> args) {
        popup(player, duration, align, top, left, bottom, right, id, args, defaultValueFactory);
    }
    /**
     * Shows a popup to the player.
     * <p>
     * Locale is taken from the {@code Player.locale}
     * @param player The player to show the popup to
     * @param duration The duration of the popup
     * @param align The alignment of the popup
     * @param top The top position of the popup
     * @param left The left position of the popup
     * @param bottom The bottom position of the popup
     * @param right The right position of the popup
     * @param id The id of the message
     * @param args The arguments to format the message with
     * @param defaultValue The default value to return if the message is not found
     */
    public void popup(Player player, float duration, int align, int top, int left, int bottom, int right,
                                String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        Call.infoPopup(player.con, format(locale(player), id, args, defaultValue), duration, align, top, left, bottom, right);
    }

    /**
     * Kicks the player.
     * <p>
     * Locale is taken from the {@code Player.locale}
     * <p>
     * Default value is taken from {@link #defaultValueFactory}
     * @param player The player to kick
     * @param id The id of the message
     * @param args The arguments to format the message with
     */
    public void kick(Player player, String id, Map<String, Object> args) {
        kick(player, id, args, defaultValueFactory);
    }
    /**
     * Kicks the player.
     * <p>
     * Locale is taken from the {@code Player.locale}
     * @param player The player to kick
     * @param id The id of the message
     * @param args The arguments to format the message with
     * @param defaultValue The default value to return if the message is not found
     */
    public void kick(Player player, String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        Call.kick(player.con, format(locale(player), id, args, defaultValue));
    }
    // endregion
    // region group
    /**
     * Sends a message to the all players.
     * <p>
     * Locale is taken from the {@code Player.locale}
     * <p>
     * Default value is taken from {@link #defaultValueFactory}
     * @param id The id of the message
     * @param args The arguments to format the message with
     */
    public void send(String id, Map<String, Object> args) {
        send(id, args, defaultValueFactory);
    }
    /**
     * Sends a message to the all players.
     * <p>
     * Locale is taken from the {@code Player.locale}
     * @param id The id of the message
     * @param args The arguments to format the message with
     * @param defaultValue The default value to return if the message is not found
     */
    public void send(String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        Groups.player.each(p -> send(p, id, args, defaultValue));
    }

    /**
     * Sends a infoMessage to the all players.
     * <p>
     * Locale is taken from the {@code Player.locale}
     * <p>
     * Default value is taken from {@link #defaultValueFactory}
     * @param id The id of the message
     * @param args The arguments to format the message with
     */
    public void infoMessage(String id, Map<String, Object> args) {
        infoMessage(id, args, defaultValueFactory);
    }
    /**
     * Sends a infoMessage to the all players.
     * <p>
     * Locale is taken from the {@code Player.locale}
     * @param id The id of the message
     * @param args The arguments to format the message with
     * @param defaultValue The default value to return if the message is not found
     */
    public void infoMessage(String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        Groups.player.each(p -> infoMessage(p, id, args, defaultValue));
    }

    /**
     * Sets the HUD text of the all players.
     * <p>
     * Locale is taken from the {@code Player.locale}
     * <p>
     * Default value is taken from {@link #defaultValueFactory}
     * @param id The id of the message
     * @param args The arguments to format the message with
     */
    public void setHud(String id, Map<String, Object> args) {
        setHud(id, args, defaultValueFactory);
    }
    /**
     * Sets the HUD text of the all players.
     * <p>
     * Locale is taken from the {@code Player.locale}
     * @param id The id of the message
     * @param args The arguments to format the message with
     * @param defaultValue The default value to return if the message is not found
     */
    public void setHud(String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        Groups.player.each(p -> setHud(p, id, args, defaultValue));
    }

    /**
     * Announces a message to the all players.
     * <p>
     * Locale is taken from the {@code Player.locale}
     * <p>
     * Default value is taken from {@link #defaultValueFactory}
     * @param id The id of the message
     * @param args The arguments to format the message with
     */
    public void announce(String id, Map<String, Object> args) {
        announce(id, args, defaultValueFactory);
    }
    /**
     * Announces a message to the all players.
     * <p>
     * Locale is taken from the {@code Player.locale}
     * @param id The id of the message
     * @param args The arguments to format the message with
     * @param defaultValue The default value to return if the message is not found
     */
    public void announce(String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        Groups.player.each(p -> announce(p, id, args, defaultValue));
    }

    /**
     * Shows a toast to the all players.
     * <p>
     * Locale is taken from the {@code Player.locale}
     * <p>
     * Default value is taken from {@link #defaultValueFactory}
     * @param icon The icon of the toast
     * @param id The id of the message
     * @param args The arguments to format the message with
     */
    public void toast(int icon, String id, Map<String, Object> args) {
        toast(icon, id, args, defaultValueFactory);
    }
    /**
     * Shows a toast to the all players.
     * <p>
     * Locale is taken from the {@code Player.locale}
     * @param icon The icon of the toast
     * @param id The id of the message
     * @param args The arguments to format the message with
     * @param defaultValue The default value to return if the message is not found.
     */
    public void toast(int icon, String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        Groups.player.each(p -> toast(p, icon, id, args, defaultValue));
    }

    /**
     * Shows a label to the all players.
     * <p>
     * Locale is taken from the {@code Player.locale}
     * <p>
     * Default value is taken from {@link #defaultValueFactory}
     * @param duration The duration of the label
     * @param x The x position of the label
     * @param y The y position of the label
     * @param id The id of the message
     * @param args The arguments to format the message with
     */
    public void label(float duration, float x, float y, String id, Map<String, Object> args) {
        label(duration, x, y, id, args, defaultValueFactory);
    }
    /**
     * Shows a label to the all players.
     * <p>
     * Locale is taken from the {@code Player.locale}
     * @param duration The duration of the label
     * @param x The x position of the label
     * @param y The y position of the label
     * @param id The id of the message
     * @param args The arguments to format the message with
     * @param defaultValue The default value to return if the message is not found.
     */
    public void label(float duration, float x, float y, String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        Groups.player.each(p -> label(p, duration, x, y, id, args, defaultValue));
    }

    /**
     * Shows a popup to the all players.
     * <p>
     * Locale is taken from the {@code Player.locale}
     * <p>
     * Default value is taken from {@link #defaultValueFactory}
     * @param duration The duration of the popup
     * @param align The alignment of the popup
     * @param top The top position of the popup
     * @param left The left position of the popup
     * @param bottom The bottom position of the popup
     * @param right The right position of the popup
     * @param id The id of the message
     * @param args The arguments to format the message with
     */
    public void popup(float duration, int align, int top, int left, int bottom, int right,
                             String id, Map<String, Object> args) {
        popup(duration, align, top, left, bottom, right, id, args, defaultValueFactory);
    }
    /**
     * Shows a popup to the all players.
     * <p>
     * Locale is taken from the {@code Player.locale}
     * @param duration The duration of the popup
     * @param align The alignment of the popup
     * @param top The top position of the popup
     * @param left The left position of the popup
     * @param bottom The bottom position of the popup
     * @param right The right position of the popup
     * @param id The id of the message
     * @param args The arguments to format the message with
     * @param defaultValue The default value to return if the message is not found.
     */
    public void popup(float duration, int align, int top, int left, int bottom, int right,
                                String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        Groups.player.each(p -> popup(p, duration, align, top, left, bottom, right, id, args, defaultValue));
    }
    // endregion

    /**
     * Transforms your arguments into a map.
     * The first argument is the key, the second is the value, the third is the key, etc.
     * @exception IllegalArgumentException If the number of arguments is odd
     * @param values The values to transform
     * @return Arguments map
     * @see #numArgs(Object...)
     */
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

    /**
     * Transforms your arguments into a map with keys a0, a1, a2, etc.
     * <p>
     * Where number is the index of the argument. Starts from 0.
     * @param values The values to transform
     * @return Arguments map
     * @see #args(Object...)
     */
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
        this.defaultLocale = defaultLocale;
    }

    public Bundle() {
    }

    public Bundle(Locale defaultLocale) {
        this.defaultLocale = defaultLocale;
    }
}
