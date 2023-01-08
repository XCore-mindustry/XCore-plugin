package org.xcore.plugin.listeners;

import arc.Events;
import fr.xpdustry.javelin.JavelinConfig;
import fr.xpdustry.javelin.JavelinPlugin;
import mindustry.game.EventType.*;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.xcore.plugin.XcorePlugin;
import org.xcore.plugin.comp.Database;
import org.xcore.plugin.discord.Bot;

import static org.xcore.plugin.PluginVars.*;
import static org.xcore.plugin.XcorePlugin.*;

public class PluginEvents {
    public static void load() {
        Events.on(ServerLoadEvent.class, event -> {
            isSocketServer = JavelinPlugin.getJavelinConfig().getMode() == JavelinConfig.Mode.SERVER;
            if (isSocketServer) {
                Bot.connect();

                Bot.sendServerAction("Server loaded");

                JavelinPlugin.getJavelinSocket().subscribe(SocketEvents.MessageEvent.class, e ->
                        Bot.sendMessageEventMessage(e.authorName, e.message, e.server));

                JavelinPlugin.getJavelinSocket().subscribe(SocketEvents.ServerActionEvent.class, e ->
                        Bot.sendServerAction(e.message, e.server));

                JavelinPlugin.getJavelinSocket().subscribe(SocketEvents.PlayerJoinLeaveEvent.class, e ->
                        Bot.sendJoinLeaveEventMessage(e.playerName, e.server, e.join));
            } else {
                JavelinPlugin.getJavelinSocket().subscribe(SocketEvents.DiscordMessageEvent.class, e -> {
                    if (!e.server.equals(config.server)) return;

                    XcorePlugin.sendMessageFromDiscord(e.authorName, e.message);
                });

                JavelinPlugin.getJavelinSocket().sendEvent(new SocketEvents.ServerActionEvent("Server loaded", config.server));
            }
        });

        Events.on(PlayerChatEvent.class, (event -> {
            if (event.message.startsWith("/")) return;

            if (isSocketServer) {
                Bot.sendMessageEventMessage(event.player.plainName(), event.message);
            } else {
                JavelinPlugin.getJavelinSocket().sendEvent(
                        new SocketEvents.MessageEvent(event.player.plainName(), event.message, config.server));
            }
        }));

        Events.on(PlayerJoin.class, event -> {
            Call.openURI(event.player.con, discordURL);

            if (config.isMiniPvP()) {
                Database.cachedPlayerData.put(event.player.uuid() , Database.getPlayerData(event.player));
            }

            if (isSocketServer) {
                Bot.sendJoinLeaveEventMessage(event.player.plainName(), true);
            } else {
                JavelinPlugin.getJavelinSocket().sendEvent(
                        new SocketEvents.PlayerJoinLeaveEvent(event.player.plainName(), config.server, true)
                );
            }
        });

        Events.on(PlayerLeave.class, event -> {
            if (config.isMiniPvP()) {
                Database.cachedPlayerData.remove(event.player.uuid());
            }
            Player player = event.player;
            int cur = rtvVotes.size();
            int req = (int) Math.ceil(rtvRatio * Groups.player.size());
            if(rtvVotes.contains(player.uuid())) {
                rtvVotes.remove(player.uuid());
                Call.sendMessage("RTV: [accent]" + player.name + "[] left, [green]" + cur + "[] votes, [green]" + req + "[] required");
            }

            if (isSocketServer) {
                Bot.sendJoinLeaveEventMessage(event.player.plainName(), false);
            } else {
                JavelinPlugin.getJavelinSocket().sendEvent(
                        new SocketEvents.PlayerJoinLeaveEvent(event.player.plainName(), config.server, false)
                );
            }
        });

        Events.on(GameOverEvent.class, e -> {
            rtvVotes.clear();
            if (!config.isMiniPvP() || e.winner == Team.derelict) return;

            e.winner.data().players.each(p -> {
                var data = Database.cachedPlayerData.get(p.uuid());

                int increased = 100 / e.winner.data().players.size + 1;
                data.rating += increased;
                p.sendMessage("Your team has won. Your rating has increased by " + increased);
                Database.setPlayerData(data);
            });

            Groups.player.each(p-> {
                if (p.team() == e.winner) return;

                var data = Database.cachedPlayerData.get(p.uuid());

                int reduced = 50 / e.winner.data().players.size + 1;

                if ((data.rating - reduced) < 0) {
                    data.rating = 0;
                    p.sendMessage("Your team lost. Your rating is 0");
                    return;
                } else {
                    data.rating -= reduced;
                    p.sendMessage("Your team lost. Your rating is reduced by " + reduced);
                }

                Database.setPlayerData(data);
            });
        });
    }
}
