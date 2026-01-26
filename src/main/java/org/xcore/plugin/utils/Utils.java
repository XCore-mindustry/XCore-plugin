package org.xcore.plugin.utils;

import arc.Core;
import arc.func.Boolf;
import arc.struct.Seq;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.game.Gamemode;
import mindustry.maps.Map;
import mindustry.maps.MapException;
import mindustry.net.WorldReloader;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static arc.util.Strings.parseInt;
import static arc.util.Strings.stripColors;
import static arc.util.Strings.stripGlyphs;
import static mindustry.Vars.charset;
import static mindustry.Vars.maps;

public class Utils {
    public static <T> T notNullElse(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }

    public static int compareVersions(String version1, String version2) {
        if (version1 == null && version2 == null) return 0;
        if (version1 == null) return -1;
        if (version2 == null) return 1;

        String[] parts1 = version1.split("\\.");
        String[] parts2 = version2.split("\\.");
        int length = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < length; i++) {
            int part1 = (i < parts1.length) ? Strings.parseInt(parts1[i], 0) : 0;
            int part2 = (i < parts2.length) ? Strings.parseInt(parts2[i], 0) : 0;
            if (part1 < part2) return -1;
            if (part1 > part2) return 1;
        }
        return 0;
    }

    public static Seq<Map> getAvailableMaps() {
        return maps.customMaps().isEmpty() ? maps.defaultMaps() : maps.customMaps();
    }

    public static int voteChoice(String vote) {
        return switch (stripFooCharacters(vote.toLowerCase())) {
            case "y" -> 1;
            case "n" -> -1;
            default -> 0;
        };
    }

    public static String stripFooCharacters(String text) {
        var builder = new StringBuilder(text);
        for (int i = text.length() - 1; i >= 0; i--)
            if (builder.charAt(i) >= 0xF80 && builder.charAt(i) <= 0x107F)
                builder.deleteCharAt(i);
        return builder.toString();
    }

    public static <T> T findInSeq(String name, Seq<T> values, Boolf<T> filter) {
        int index = parseInt(name) - 1;
        return values.find(value -> values.indexOf(value) == index || filter.get(value));
    }

    public static boolean deepEquals(String first, String second) {
        first = stripColors(stripGlyphs(first));
        second = stripColors(stripGlyphs(second));
        return first.equalsIgnoreCase(second) || first.toLowerCase().contains(second.toLowerCase());
    }

    public static boolean equalsHasNull(String query, String name) {
        if (query == null || query.isEmpty() || name == null || name.isEmpty()) return false;
        return query.equals(name);
    }

    public static Map findMap(String name) {
        return findInSeq(name, getAvailableMaps(), map -> deepEquals(map.name(), name));
    }

    public static String findServer(String name, Iterable<String> serverNames) {
        String result = null;
        for (String s : serverNames) {
            if (s.equals(name)) return name;
            if (s.startsWith(name) || s.contains(name)) result = s;
        }
        return result;
    }

    public static void reloadWorld(Runnable runnable) {
        try {
            var reloader = new WorldReloader();
            reloader.begin();
            runnable.run();
            Vars.state.rules = Vars.state.map.applyRules(Gamemode.valueOf(Core.settings.getString("lastServerMode")));
            Vars.logic.play();
            reloader.end();
        } catch (MapException e) {
            arc.util.Log.err("@: @", e.map.name(), e.getMessage());
        }
    }

    public static void writeString(ByteBuffer buffer, String string, int maxlen) {
        byte[] bytes = string.getBytes(charset);
        if (bytes.length > maxlen) bytes = Arrays.copyOfRange(bytes, 0, maxlen);
        buffer.put((byte) bytes.length);
        buffer.put(bytes);
    }

    public static void writeString(ByteBuffer buffer, String string) {
        writeString(buffer, string, 32);
    }

    public enum UnitState { IDLE, ATTACK }
}