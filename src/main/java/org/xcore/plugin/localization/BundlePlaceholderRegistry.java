package org.xcore.plugin.localization;

import arc.files.Fi;
import arc.struct.ObjectMap;
import arc.util.Log;
import mindustry.mod.Mod;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static mindustry.Vars.mods;

public final class BundlePlaceholderRegistry {

    private static final Pattern BUNDLE_KEY_PATTERN = Pattern.compile("^([a-z][a-z0-9-]*)\\s*=");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\s*\\$([A-Za-z0-9_-]+)");

    private final ObjectMap<String, Map<String, Object>> placeholderArgsByKey = new ObjectMap<>();

    private BundlePlaceholderRegistry() {
    }

    public static BundlePlaceholderRegistry fromMod(Class<? extends Mod> mainClass) {
        var mod = mods.getMod(mainClass);
        if (mod == null) {
            throw new IllegalStateException("Could not find mod for " + mainClass.getName());
        }

        return fromDirectory(mod.root.child("bundles"));
    }

    public static BundlePlaceholderRegistry fromDirectory(Fi bundlesDirectory) {
        var registry = new BundlePlaceholderRegistry();
        if (bundlesDirectory == null || !bundlesDirectory.exists()) {
            return registry;
        }

        bundlesDirectory.walk(file -> {
            if (!file.extEquals("ftl")) {
                return;
            }

            registry.readFile(file);
        });

        return registry;
    }

    public Map<String, Object> placeholderArgs(String key) {
        return placeholderArgsByKey.get(key, Collections.emptyMap());
    }

    public boolean containsKey(String key) {
        return placeholderArgsByKey.containsKey(key);
    }

    private void readFile(Fi file) {
        String currentKey = null;
        StringBuilder currentValue = null;

        for (String line : file.readString().split("\\R", -1)) {
            if (!line.isBlank() && !Character.isWhitespace(line.charAt(0))) {
                if (currentKey != null) {
                    store(currentKey, currentValue.toString());
                }

                Matcher matcher = BUNDLE_KEY_PATTERN.matcher(line);
                if (matcher.find()) {
                    currentKey = matcher.group(1);
                    currentValue = new StringBuilder(line.substring(line.indexOf('=') + 1));
                } else {
                    currentKey = null;
                    currentValue = null;
                }
                continue;
            }

            if (currentValue != null) {
                currentValue.append('\n').append(line);
            }
        }

        if (currentKey != null) {
            store(currentKey, currentValue.toString());
        }
    }

    private void store(String key, String value) {
        if (placeholderArgsByKey.containsKey(key)) {
            return;
        }

        var names = new LinkedHashSet<String>();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }

        if (names.isEmpty()) {
            placeholderArgsByKey.put(key, Collections.emptyMap());
            return;
        }

        var placeholderArgs = new java.util.LinkedHashMap<String, Object>();
        for (String name : names) {
            placeholderArgs.put(name, "<" + name + ">");
        }
        placeholderArgsByKey.put(key, Collections.unmodifiableMap(placeholderArgs));
        Log.debug("Loaded bundle placeholders for key '@': @", key, names);
    }
}
