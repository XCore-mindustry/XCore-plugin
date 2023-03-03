package org.xcore.plugin.modules.votes;

import arc.util.Strings;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Packets;
import org.xcore.plugin.listeners.SocketEvents;
import org.xcore.plugin.modules.discord.Bot;
import org.xcore.plugin.utils.JavelinCommunicator;

import static org.xcore.plugin.PluginVars.*;

public class VoteKick extends VoteSession {

    public final Player starter;
    public final Player target;

    public VoteKick(Player starter, Player target) {
        this.starter = starter;
        this.target = target;
    }

    @Override
    public void vote(Player player, int sign) {
        super.vote(player, sign);
        Call.sendMessage(Strings.format("@[lightgray] voted to kick @[lightgray] from the server. ([accent]@[]/[accent]@[])\n[lightgray]Type [orange]/vote <y/n>[] to vote.",
                player.coloredName(), target.coloredName(), votes(), votesRequired()));
        JavelinCommunicator.sendEvent(
                new SocketEvents.ServerActionEvent(Strings.format("@ voted to kick @ from the server. (@/@)",
                        player.plainName(), target.plainName(), votes(), votesRequired()), config.server),
                e -> Bot.sendServerAction(e.message));
    }

    @Override
    public void left(Player player) {
        if (voted.remove(player.id) != 0)
            Call.sendMessage(Strings.format("@[lightgray] left. His vote for kicking a player was cancelled. ([accent]@[]/[accent]@[])",
                    player.coloredName(), votes(), votesRequired()));

        if (target == player && votes() > 0)
            success();
    }

    @Override
    public void success() {
        stop();
        Call.sendMessage(Strings.format("[orange]Vote passed. @[orange] kicked from the server for [scarlet]@[] minutes",
                target.coloredName(), kickDuration / 60000));
        target.kick(Packets.KickReason.vote, kickDuration);
        JavelinCommunicator.sendEvent(new SocketEvents.ServerActionEvent(Strings.format("Vote passed. @ kicked from the server for @ minutes",
                        target.plainName(), kickDuration / 60000), config.server),
                e -> Bot.sendServerAction(e.message));
    }

    @Override
    public void fail() {
        stop();
        Call.sendMessage(Strings.format("[lightgray]Vote failed. Not enough votes to kick @[lightgray] from the server."));
    }

    @Override
    public void stop() {
        voteKick = null;
        end.cancel();
    }

    @Override
    public int votesRequired() {
        return Groups.player.size() > 3 ? 3 : 2;
    }
}
