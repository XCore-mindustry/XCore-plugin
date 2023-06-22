package org.xcore.plugin.utils;

import arc.Core;
import arc.func.Boolf;
import arc.func.Cons2;
import arc.struct.Seq;
import arc.util.*;
import mindustry.Vars;
import mindustry.game.Gamemode;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.maps.Map;
import mindustry.maps.MapException;
import mindustry.net.WorldReloader;
import org.xcore.plugin.utils.models.PlayerData;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static arc.util.Strings.*;
import static mindustry.Vars.charset;
import static mindustry.Vars.maps;
import static org.xcore.plugin.PluginVars.database;
import static org.xcore.plugin.PluginVars.globalConfig;
import static useful.Bundle.format;

public class Utils {
    private static final Pattern periodPattern = Pattern.compile("([0-9]+)([hdwmy])");

    public static <T> T notNullElse(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }

    public static @Nullable Instant parsePeriod(String period, TimeUnit defaultUnit) {
        if (period == null)
            return null;
        period = period.toLowerCase();
        Matcher matcher = periodPattern.matcher(period);
        Instant instant = Instant.EPOCH;

        while (matcher.find()) {
            int num = Integer.parseInt(matcher.group(1));
            String typ = matcher.group(2);
            switch (typ) {
                case "m" -> instant = instant.plusMillis(TimeUnit.MINUTES.toMillis(num));
                case "h" -> instant = instant.plusMillis(TimeUnit.HOURS.toMillis(num));
                case "d" -> instant = instant.plusMillis(TimeUnit.DAYS.toMillis(num));
                case "w" -> instant = instant.plusMillis(TimeUnit.DAYS.toMillis(7L * num));
                case "y" -> instant = instant.plusMillis(TimeUnit.DAYS.toMillis(365L * num));
            }
        }

        boolean same = instant.plusMillis(Time.millis()).toEpochMilli() == Time.millis();
        if (same && Strings.canParsePositiveInt(period)) {
            return Instant.ofEpochMilli(defaultUnit.toMillis(Strings.parseInt(period)));
        } else if (same) {
            return null;
        }

        return instant;
    }

    public static void getPvPLeaderboard(StringBuilder builder, Player player) {
        Seq<PlayerData> sorted = database.cachedPlayerData.copy().values().toSeq().filter(d -> d.pvpRating != 0)
                .sort(d -> d.pvpRating).reverse();
        sorted.truncate(10);

        builder.append(format("leaderboard", player.locale));
        for (int i = 0; i < sorted.size; i++) {
            var data = sorted.get(i);

            builder.append(format("pvp.leaderboard.content", player.locale, i + 1, data.nickname, data.pvpRating));
        }

    }

    public static void getHexedLeaderboard(StringBuilder builder, Player player) {
        var teams = Vars.state.teams.getActive().copy().filter(t -> !t.players.isEmpty() && t.team != Team.derelict)
                .sort(t -> t.cores.size).reverse();
        teams.truncate(10);

        builder.append(format("leaderboard", player.locale));
        for (int i = 0; i < teams.size; i++) {
            var team = teams.get(i);
            builder.append(format("hexed.leaderboard.content", player.locale, i + 1, team.players.first().coloredName(),
                    team.cores.size));
        }

    }

    public static void showLeaderboard(Cons2<StringBuilder, Player> cons) {
        Timer.schedule(() -> {
            if (Groups.player.isEmpty())
                return;
            Groups.player.each(player -> {
                if (!database.getCached(player.uuid()).leaderboard)
                    return;
                StringBuilder builder = new StringBuilder();
                cons.get(builder, player);
                Call.infoPopup(player.con, builder.toString(), 5f, 8, 0, 2, 50, 0);
            });
        }, 0f, 5f);
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
        if (query == null || query.isEmpty() || name == null || name.isEmpty())
            return false;
        return query.equals(name);
    }

    public static Map findMap(String name) {
        return findInSeq(name, getAvailableMaps(), map -> deepEquals(map.name(), name));
    }

    public static String findServer(String name) {
        String result = null;
        if (!globalConfig.servers.containsKey(name)) {
            for (String s : globalConfig.servers.keys()) {
                if (s.startsWith(name) || s.contains(name)) result = s;
            }
        } else
            result = name;

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
            Log.err("@: @", e.map.name(), e.getMessage());
        }
    }

    // https://github.com/Anuken/Mindustry/blob/b81e9424794ca8eccb7008a1f85ab9c2199bdbd3/core/src/mindustry/net/NetworkIO.java#L132
    public static void writeString(ByteBuffer buffer, String string, int maxlen) {
        byte[] bytes = string.getBytes(charset);
        // todo truncating this way may lead to wierd encoding errors at the ends of
        // strings...
        if (bytes.length > maxlen) {
            bytes = Arrays.copyOfRange(bytes, 0, maxlen);
        }

        buffer.put((byte) bytes.length);
        buffer.put(bytes);
    }

    public static void writeString(ByteBuffer buffer, String string) {
        writeString(buffer, string, 32);
    }

    public enum UnitState {
        IDLE, ATTACK
    }
}
