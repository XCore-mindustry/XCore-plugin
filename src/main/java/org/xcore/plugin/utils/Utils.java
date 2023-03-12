package org.xcore.plugin.utils;

import arc.Core;
import arc.func.Boolf;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Reflect;
import arc.util.Strings;
import discord4j.common.util.TimestampFormat;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.util.Color;
import mindustry.Vars;
import mindustry.ctype.UnlockableContent;
import mindustry.game.Gamemode;
import mindustry.gen.Iconc;
import mindustry.maps.Map;
import mindustry.maps.MapException;
import mindustry.net.WorldReloader;
import org.xcore.plugin.modules.discord.Bot;
import org.xcore.plugin.utils.models.BanData;
import org.xcore.plugin.utils.models.PlayerData;

import java.time.Instant;

import static arc.util.Strings.*;
import static mindustry.Vars.maps;
import static mindustry.Vars.netServer;
import static org.xcore.plugin.PluginVars.*;
import static org.xcore.plugin.modules.discord.Bot.bansChannel;
import static org.xcore.plugin.modules.discord.Bot.isConnected;

public class Utils {
    public static void temporaryBan(BanData ban) {
        Database.setBan(ban);
        if (!isConnected) return;

        bansChannel.flatMap(channel -> channel.createMessage(MessageCreateSpec.builder()
                .addEmbed(EmbedCreateSpec.builder()
                        .title("Ban")
                        .color(Color.RED)
                        .addField("Violator", ban.name, false)
                        .addField("Admin", ban.adminName, false)
                        .addField("Server", ban.server, false)
                        .addField("Reason", ban.reason, false)
                        .addField("Unban Date", TimestampFormat.LONG_DATE.format(Instant.ofEpochMilli(ban.unbanDate)), false)
                        .build()
                )
                .addComponent(ActionRow.of(Button.danger(ban.bid + "-unban", "Unban")))
                .build())).subscribe();
    }

    public static void handleBanData(BanData ban) {
        if (ban.unban) {
            if (!ban.server.equals(config.server)) return;
            netServer.admins.unbanPlayerID(ban.uuid);
            netServer.admins.unbanPlayerIP(ban.ip);
            Database.unBan(ban.uuid, ban.ip);
            return;
        }

        if (!isSocketServer) return;

        if (ban.full) {
            Utils.temporaryBan(ban);
        } else {
            Bot.sendBanEvent(ban);
        }
    }

    public static String getPvPLeaderboard() {
        var builder = new StringBuilder();
        Seq<PlayerData> sorted = Database.cachedPlayerData.copy().values().toSeq().filter(d -> d.pvpRating != 0).sort(d -> d.pvpRating).reverse();
        sorted.truncate(10);

        builder.append("[blue]Leaderboard\n\n");
        for (int i = 0; i < sorted.size; i++) {
            var data = sorted.get(i);
            builder.append("[orange]").append(i + 1)
                    .append(". ")
                    .append(data.nickname)
                    .append("[accent]:[cyan] ")
                    .append(data.pvpRating).append(" [accent]rating\n");
        }

        return builder.toString();
    }

    public static String getHexedLeaderboard() {
        var builder = new StringBuilder();
        var teams = Vars.state.teams.getActive().copy().filter(t -> !t.players.isEmpty()).sort(t -> t.cores.size).reverse();

        builder.append("[blue]Leaderboard\n\n");
        for (int i = 0; i < teams.size; i++) {
            var team = teams.get(i);
            var player = team.players.first();

            builder.append("[orange]").append(i + 1)
                    .append(". ").append(player.coloredName())
                    .append("[accent]: [cyan]")
                    .append(team.cores.size).append(" [accent]hexes\n");
        }

        return builder.toString();
    }

    public static Seq<Map> getAvailableMaps() {
        return maps.customMaps().isEmpty() ? maps.defaultMaps() : maps.customMaps();
    }

    public static int voteChoice(String vote) {
        return switch (vote.toLowerCase()) {
            case "y" -> 1;
            case "n" -> -1;
            default -> 0;
        };
    }

    public static String findTranslatorLanguage(String locale) {
        return translatorLanguages.orderedKeys().find(locale::startsWith);
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

    public static Map findMap(String name) {
        return findInSeq(name, getAvailableMaps(), map -> deepEquals(map.name(), name));
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

    public static char emoji(UnlockableContent content) {
        try {
            return Reflect.get(Iconc.class, Strings.kebabToCamel(content.getContentType().name() + "-" + content.name));
        } catch (Exception e) {
            return '?';
        }
    }

    public enum UnitState {
        IDLE, ATTACK
    }
}
