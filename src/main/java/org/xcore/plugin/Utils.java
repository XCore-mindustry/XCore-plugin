package org.xcore.plugin;

import arc.struct.Seq;
import org.xcore.plugin.comp.Database;
import org.xcore.plugin.comp.PlayerData;

public class Utils {
    public static String getLeaderboard() {
        var builder = new StringBuilder();
        Seq<PlayerData> sorted = Database.cachedPlayerData.copy().values().toSeq().sort().reverse();
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
}
