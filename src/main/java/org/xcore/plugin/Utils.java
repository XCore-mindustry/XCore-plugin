package org.xcore.plugin;

import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.Timer;
import fr.xpdustry.javelin.JavelinPlugin;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.maps.Map;
import mindustry.net.Packets;
import org.xcore.plugin.listeners.SocketEvents;
import org.xcore.plugin.modules.Database;
import org.xcore.plugin.modules.discord.Bot;
import org.xcore.plugin.modules.models.PlayerData;

import static mindustry.Vars.maps;
import static mindustry.Vars.netServer;
import static org.xcore.plugin.PluginVars.*;

public class Utils {
    public static String getLeaderboard() {
        var builder = new StringBuilder();
        Seq<PlayerData> sorted = Database.cachedPlayerData.copy().values().toSeq().filter(d -> d.rating != 0).sort(d -> d.rating).reverse();
        sorted.truncate(10);

        builder.append("[blue]Leaderboard\n\n");
        for (int i = 0; i < sorted.size; i++) {
            var data = sorted.get(i);
            builder.append("[orange]").append(i + 1)
                    .append(". ")
                    .append(data.nickname)
                    .append(":[cyan] ")
                    .append(data.rating).append(" []rating\n");
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

    public static int votesRequired() {
        return 2 + (Groups.player.size() > 4 ? 1 : 0);
    }

    public static class VoteSession {
        public Player target;
        public ObjectSet<String> voted = new ObjectSet<>();
        public int votes;
        VoteSession[] map;
        Timer.Task task;

        public VoteSession(VoteSession[] map, Player target) {
            this.target = target;
            this.map = map;
            this.task = Timer.schedule(() -> {
                if (!checkPass()) {
                    Call.sendMessage(Strings.format("[lightgray]Vote failed. Not enough votes to kick[orange] @[lightgray].", target.name));
                    map[0] = null;
                    task.cancel();
                }
            }, voteDuration);
        }

        public void vote(Player player, int d) {
            votes += d;
            voted.addAll(player.uuid(), netServer.admins.getInfo(player.uuid()).lastIP);

            Call.sendMessage(Strings.format("[lightgray]@[lightgray] has voted on kicking[orange] @[lightgray].[accent] (@/@)\n[lightgray]Type[orange] /vote <y/n>[] to agree.",
                    player.name, target.name, votes, votesRequired()));
            String message = Strings.format("@ has voted on kicking @. (@/@)", player.plainName(), target.plainName(), votes, votesRequired());

            if (isSocketServer) {
                Bot.sendServerAction(message);
            } else {
                JavelinPlugin.getJavelinSocket().sendEvent(new SocketEvents.ServerActionEvent(message, config.server));
            }
            checkPass();
        }

        public boolean checkPass() {
            if (votes >= votesRequired()) {
                Call.sendMessage(Strings.format("[orange]Vote passed.[scarlet] @[orange] will be banned from the server for @ minutes.", target.name, (kickDuration / 60)));
                target.kick(Packets.KickReason.vote, kickDuration * 1000L);
                map[0] = null;
                task.cancel();

                String message = Strings.format("Vote passed. @ will be banned from the server for @ minutes.", target.name, (kickDuration / 60));
                if (isSocketServer) {
                    Bot.sendServerAction(message);
                } else {
                    JavelinPlugin.getJavelinSocket().sendEvent(new SocketEvents.ServerActionEvent(message, config.server));
                }
                return true;
            }
            return false;
        }
    }
}
