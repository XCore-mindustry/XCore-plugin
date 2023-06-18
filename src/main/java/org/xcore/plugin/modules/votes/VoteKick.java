package org.xcore.plugin.modules.votes;

import arc.func.Cons;
import arc.util.Log;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Packets;
import org.xcore.plugin.listeners.SocketEvents;
import org.xcore.plugin.utils.SockCommunicator;
import useful.Bundle;

import static arc.util.Strings.stripColors;
import static org.xcore.plugin.PluginVars.*;
import static useful.Bundle.*;

public class VoteKick extends VoteSession {
    public static Cons<Player> onKick = (player) -> {
    };

    public final Player starter;
    public final Player target;
    public final String reason;

    public VoteKick(Player starter, Player target, String reason) {
        this.starter = starter;
        this.target = target;
        this.reason = reason;
    }

    public static void setOnKick(Cons<Player> onKick) {
        if (onKick == null) return;
        VoteKick.onKick = onKick;
    }

    @Override
    public void vote(Player player, int sign) {
        super.vote(player, sign);
        send("votekick.vote", player.coloredName(), target.coloredName(), reason, votes(), votesRequired());
        Log.info(Bundle.format("votekick.vote", defaultLocale, player.plainName(), target.plainName(), reason, votes(), votesRequired()));

        SockCommunicator.sendEvent(new SocketEvents.ServerActionEvent(stripColors(format("votekick.vote", defaultLocale,
                player.plainName(), target.plainName(), reason, votes(), votesRequired())), config.server));
    }

    @Override
    public void left(Player player) {
        if (voted.remove(player.id) != 0)
            send("votekick.left", player.coloredName(), votes(), votesRequired());

        if (target == player && votes() > 0)
            success();
    }

    @Override
    public void success() {
        stop();
        send("votekick.success", target.coloredName(), kickDuration / 60000);
        target.kick(Packets.KickReason.vote, kickDuration);
        SockCommunicator.sendEvent(new SocketEvents.ServerActionEvent(format("votekick.success", defaultLocale,
                target.plainName(), kickDuration / 60000), config.server));
        onKick.get(target);
    }

    @Override
    public void fail() {
        stop();
        send("votekick.fail", target.coloredName());
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
