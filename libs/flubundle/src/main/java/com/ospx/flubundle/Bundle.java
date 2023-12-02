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
import java.util.Locale;
import java.util.Map;

import static mindustry.Vars.mods;

@SuppressWarnings("unused")
public class Bundle {
    public static Bundle INSTANCE = new Bundle();

    public Locale defaultLocale = new Locale("en");
    public DefaultValueFactory defaultValueFactory = new NoopDefaultValueFactory();

    private final ObjectMap<Locale, FluentBundle> sources = new ObjectMap<>();

    public void addSource(Class<? extends Mod> main) {
        addSource(mods.getMod(main).root.child("bundles"));
    }

    public void addSource(Fi directory) {
        directory.walk(fi -> {
            if (!fi.extEquals("ftl")) return;

            var codes = fi.nameWithoutExtension().split("_");
            var locale = codes.length == 2 ? new Locale(codes[1]) : new Locale(codes[1], codes[2]);

            addSource(fi, locale);
        });
    }

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

    public String format(Locale locale, String id, Map<String, Object> args) {
        return format(locale, id, args, defaultValueFactory);
    }

    public String format(Locale locale, String id, String defaultValue, Map<String, Object> args) {
        return format(locale, id, args, (k, l) -> defaultValue);
    }

    public String format(Locale locale, String id, Map<String, Object> args, DefaultValueFactory defaultValue) {
        var bundle = sources.get(locale);

        if (bundle == null) {
            throw new RuntimeException("No bundle for locale " + locale);
        }

        var message = bundle.getMessage(id);
        if (message.isPresent()) {
            return bundle.format(id, args == null ? Collections.emptyMap() : args);
        } else {
            return defaultValue.getDefaultValue(id, locale);
        }
    }

    public Locale locale(Player player) {
        return locale(player.locale);
    }

    public Locale locale(String code) {
        var locale = new Locale(code);
        return sources.containsKey(locale) ? locale : defaultLocale;
    }

    // region single
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
        Call.infoMessage(format(locale(player), id, args, defaultValue));
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
    // endregion
    // region group
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

    // endregion
    public Bundle() {
    }

    public Bundle(Locale defaultLocale) {
        this.defaultLocale = defaultLocale;
    }
}
