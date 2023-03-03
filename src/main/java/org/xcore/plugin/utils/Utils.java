package org.xcore.plugin.utils;

import arc.func.Boolf;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.maps.Map;
import mindustry.maps.MapException;
import mindustry.net.WorldReloader;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.TimeFormat;
import org.xcore.plugin.modules.discord.Bot;
import org.xcore.plugin.utils.models.BanData;
import org.xcore.plugin.utils.models.PlayerData;

import java.awt.*;

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

        EmbedBuilder embed = new EmbedBuilder().setTitle("Ban")
                .setColor(Color.red)
                .addField("Violator", ban.name, false)
                .addField("Admin", ban.adminName, false)
                .addField("Server", ban.server, false)
                .addField("Reason", ban.reason, false)
                .addField("Unban Date", TimeFormat.DATE_LONG.format(ban.unbanDate), false);
        bansChannel.sendMessageEmbeds(embed.build()).addActionRow(
                Button.danger(ban.bid + "-unban", "Unban")).queue();
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

    public static String getLeaderboard() {
        var builder = new StringBuilder();
        Seq<PlayerData> sorted = Database.cachedPlayerData.copy().values().toSeq().filter(d -> (config.isMiniPvP() ? d.pvpRating : d.hexedWins) != 0).sort(d -> config.isMiniPvP() ? d.pvpRating : d.hexedWins).reverse();
        sorted.truncate(10);

        builder.append("[blue]Leaderboard\n\n");
        for (int i = 0; i < sorted.size; i++) {
            var data = sorted.get(i);
            builder.append("[orange]").append(i + 1)
                    .append(". ")
                    .append(data.nickname)
                    .append(":[cyan] ")
                    .append(config.isMiniPvP() ? data.pvpRating : data.hexedWins).append(" []rating\n");
        }

        return builder.toString();
    }

    public static void showLeaderboard() {
        Timer.schedule(() -> {
            if (Groups.player.isEmpty()) return;
            Groups.player.each(player -> Call.infoPopup(player.con, Utils.getLeaderboard(), 5f, 8, 0, 2, 50, 0));
        }, 0f, 5f);
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
            Vars.state.rules = Vars.state.map.applyRules(Vars.state.rules.mode());
            Vars.logic.play();

            reloader.end();
        } catch (MapException e) {
            Log.err("@: @", e.map.name(), e.getMessage());
        }
    }
}
