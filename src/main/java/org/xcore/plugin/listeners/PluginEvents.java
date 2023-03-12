package org.xcore.plugin.listeners;

import arc.Events;
import arc.util.Strings;
import fr.xpdustry.javelin.JavelinPlugin;
import mindustry.game.EventType;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.EventType.PlayerJoin;
import mindustry.game.EventType.PlayerLeave;
import mindustry.game.EventType.ServerLoadEvent;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.xcore.plugin.XcorePlugin;
import org.xcore.plugin.modules.discord.Bot;
import org.xcore.plugin.modules.hexed.HexedRanks;
import org.xcore.plugin.modules.history.History;
import org.xcore.plugin.modules.history.HistoryEntry;
import org.xcore.plugin.utils.Database;
import org.xcore.plugin.utils.JavelinCommunicator;
import org.xcore.plugin.utils.Utils;
import org.xcore.plugin.utils.models.BanData;

import static mindustry.Vars.state;
import static org.xcore.plugin.PluginVars.*;

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

            } else {
                JavelinPlugin.getJavelinSocket().subscribe(SocketEvents.DiscordMessageEvent.class, e -> {
                    if (!e.server.equals(config.server)) return;

                    XcorePlugin.sendMessageFromDiscord(e.authorName, e.message);
                });
            }
            JavelinPlugin.getJavelinSocket().subscribe(BanData.class, Utils::handleBanData);
        });
        Events.on(PlayerJoin.class, event -> {
            if (event.player.getInfo().timesJoined < 5)
                Call.openURI(event.player.con, discordURL);

            var data = Database.getPlayerData(event.player).setNickname(event.player.coloredName());
            HexedRanks.updateRank(event.player, data);
            Database.setCached(data);

            if (data.translatorLanguage.equals("off")) {
                event.player.sendMessage("[accent]I see that you have automatic chat translator turned off, so I recommend turning it on using the [grey]/tr auto[] command.");
            }

            JavelinCommunicator.sendEvent(
                    new SocketEvents.PlayerJoinLeaveEvent(event.player.plainName(), config.server, true),
                    e -> Bot.sendJoinLeaveEventMessage(e.playerName, true));
        });

        Events.on(PlayerLeave.class, event -> {
            Player player = event.player;

            Database.removeCached(event.player.uuid());

            if (vote != null) vote.left(event.player);
            if (voteKick != null) voteKick.left(event.player);

            JavelinCommunicator.sendEvent(
                    new SocketEvents.PlayerJoinLeaveEvent(player.plainName(), config.server, false),
                    e -> Bot.sendJoinLeaveEventMessage(e.playerName, false));
        });

        Events.on(EventType.WorldLoadEvent.class, event -> {
            History.clear();
        });

        Events.on(GameOverEvent.class, event -> {
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

        Events.on(EventType.ConfigEvent.class, event -> {
            if (History.enabled() && event.player != null)
                History.put(new HistoryEntry(event), event.tile.tile);
        });

        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            if (!event.unit.isPlayer()) return;

            if (History.enabled() && event.tile.build != null)
                History.put(new HistoryEntry(event), event.tile);
        });

        Events.on(EventType.TapEvent.class, event -> {
            if (!History.enabled() || event.tile == null) return;

            var data = Database.getCached(event.player.uuid());
            if (!data.history) return;

            var stack = History.get(event.tile.array());
            if (stack == null) return;

            var builder = new StringBuilder();

            if (stack.isEmpty()) builder.append("Empty.");
            else stack.each(entry -> builder.append("\n").append(entry.getMessage()));

            event.player.sendMessage(Strings.format("[yellow]History of tile (@, @) @", event.tile.x, event.tile.y, builder.toString()));
        });
    }
}
