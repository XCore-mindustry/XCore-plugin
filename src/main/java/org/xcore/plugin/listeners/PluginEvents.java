package org.xcore.plugin.listeners;

import arc.Events;
import arc.util.Strings;
import fr.xpdustry.javelin.JavelinConfig;
import fr.xpdustry.javelin.JavelinPlugin;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.EventType.PlayerJoin;
import mindustry.game.EventType.PlayerLeave;
import mindustry.game.EventType.ServerLoadEvent;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.xcore.plugin.XcorePlugin;
import org.xcore.plugin.modules.discord.Bot;
import org.xcore.plugin.utils.Database;
import org.xcore.plugin.utils.JavelinCommunicator;
import org.xcore.plugin.utils.Utils;
import org.xcore.plugin.utils.models.BanData;

import static mindustry.Vars.state;
import static org.xcore.plugin.PluginVars.*;
import static org.xcore.plugin.utils.Utils.votesRequired;

public class PluginEvents {
    public static void init() {
        Events.on(ServerLoadEvent.class, event -> {
            JavelinCommunicator.init();

            JavelinCommunicator.sendEvent(
                    new SocketEvents.ServerActionEvent("Server loaded", config.server),
                    e -> Bot.sendServerAction(e.message));

            if (isSocketServer) {
                Bot.connect();

                JavelinPlugin.getJavelinSocket().subscribe(SocketEvents.MessageEvent.class, e ->
                        Bot.sendMessageEventMessage(e.authorName, e.message, e.server));

                JavelinPlugin.getJavelinSocket().subscribe(SocketEvents.ServerActionEvent.class, e ->
                        Bot.sendServerAction(e.message, e.server));

                JavelinPlugin.getJavelinSocket().subscribe(SocketEvents.PlayerJoinLeaveEvent.class, e ->
                        Bot.sendJoinLeaveEventMessage(e.playerName, e.server, e.join));

                JavelinPlugin.getJavelinSocket().subscribe(BanData.class, ban -> {
                    if (ban.full) {
                        Utils.temporaryBan(ban);
                    } else {
                        Bot.sendBanEvent(ban);
                    }
                });
            } else {
                JavelinPlugin.getJavelinSocket().subscribe(SocketEvents.DiscordMessageEvent.class, e -> {
                    if (!e.server.equals(config.server)) return;

                    XcorePlugin.sendMessageFromDiscord(e.authorName, e.message);
                });
            }
        });
        Events.on(PlayerJoin.class, event -> {
            if (event.player.getInfo().timesJoined < 5)
                Call.openURI(event.player.con, discordURL);

            var data = Database.getPlayerData(event.player).setNickname(event.player.coloredName());
            Database.cachedPlayerData.put(event.player.uuid(), data);

            if (data.translatorLanguage.equals("off")) {
                event.player.sendMessage("[accent]I see that you have automatic chat translator turned off, so I recommend turning it on using the [grey]/tr auto[] command.");
            }

            JavelinCommunicator.sendEvent(
                    new SocketEvents.PlayerJoinLeaveEvent(event.player.plainName(), config.server, true),
                    e -> Bot.sendJoinLeaveEventMessage(e.playerName, true));
        });

        Events.on(PlayerLeave.class, event -> {
            Player player = event.player;

            Database.cachedPlayerData.remove(player.uuid());

            if (currentlyKicking[0] != null && currentlyKicking[0].target == player) {
                currentlyKicking[0].votes = votesRequired();
                currentlyKicking[0].checkPass();
            }

            int cur = rtvVotes.size();
            int req = (int) Math.ceil(rtvRatio * Groups.player.size());
            if (rtvVotes.contains(player.uuid())) {
                rtvVotes.remove(player.uuid());
                Call.sendMessage("RTV: [accent]" + player.name + "[] left, [green]" + cur + "[] votes, [green]" + req + "[] required");
            }

            JavelinCommunicator.sendEvent(
                    new SocketEvents.PlayerJoinLeaveEvent(player.plainName(), config.server, false),
                    e -> Bot.sendJoinLeaveEventMessage(e.playerName, false));
        });

        Events.on(GameOverEvent.class, event -> {
            rtvVotes.clear();
            String message = null;
            if (state.rules.waves) {
                message = Strings.format(
                        "Game over! Reached wave @ with @ players online on map @.", state.wave, Groups.player.size(), Strings.capitalize(Strings.stripColors(state.map.name())));
            } else if (state.rules.pvp && !config.isMiniHexed()) {
                message = Strings.format(
                        "Game over! Team @ is victorious with @ players online on map @.", event.winner.name, Groups.player.size(), Strings.capitalize(Strings.stripColors(state.map.name())));
            }

            JavelinCommunicator.sendEvent(
                    new SocketEvents.ServerActionEvent(message, config.server),
                    e -> Bot.sendServerAction(e.message));
        });
    }
}
