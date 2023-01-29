package org.xcore.plugin.listeners;

import arc.Events;
import fr.xpdustry.javelin.JavelinConfig;
import fr.xpdustry.javelin.JavelinPlugin;
import mindustry.game.EventType.*;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.xcore.plugin.XcorePlugin;
import org.xcore.plugin.modules.discord.Bot;

import static org.xcore.plugin.PluginVars.*;

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

            if (isSocketServer) {
                Bot.sendJoinLeaveEventMessage(event.player.plainName(), true);
            } else {
                JavelinPlugin.getJavelinSocket().sendEvent(
                        new SocketEvents.PlayerJoinLeaveEvent(event.player.plainName(), config.server, true)
                );
            }
        });

        Events.on(PlayerLeave.class, event -> {
            Player player = event.player;
            int cur = rtvVotes.size();
            int req = (int) Math.ceil(rtvRatio * Groups.player.size());
            if(rtvVotes.contains(player.uuid())) {
                rtvVotes.remove(player.uuid());
                Call.sendMessage("RTV: [accent]" + player.name + "[] left, [green]" + cur + "[] votes, [green]" + req + "[] required");
            }

            if (isSocketServer) {
                Bot.sendJoinLeaveEventMessage(player.plainName(), false);
            } else {
                JavelinPlugin.getJavelinSocket().sendEvent(
                        new SocketEvents.PlayerJoinLeaveEvent(player.plainName(), config.server, false)
                );
            }
        });
        Events.on(GameOverEvent.class, e -> rtvVotes.clear());
    }
}
