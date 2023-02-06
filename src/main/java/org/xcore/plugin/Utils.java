package org.xcore.plugin;

import arc.struct.Seq;
import arc.util.Strings;
import arc.util.Timer;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.maps.Map;
import org.xcore.plugin.modules.Database;
import org.xcore.plugin.modules.models.PlayerData;

import static mindustry.Vars.maps;

public class Utils {
    public static String getLeaderboard() {
        var builder = new StringBuilder();
        Seq<PlayerData> sorted = Database.cachedPlayerData.copy().values().toSeq().sort(d -> d.rating);
        sorted.truncate(10);

        builder.append("[blue]Leaderboard\n\n");
        for (int i = 0; i < sorted.size; i++) {
            var data = sorted.get(i);
            if (data.rating != 0) {
                builder.append("[orange]").append(i + 1)
                        .append(". ")
                        .append(data.nickname)
                        .append(":[cyan] ")
                        .append(data.rating).append(" []rating\n");
            }
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

    public static String colorizedTeam(Team team) {
        return Strings.format("[#@]@", team.color, team);
    }
}
