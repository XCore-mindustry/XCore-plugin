package org.xcore.plugin.modules.votes;

import arc.func.Cons;
import arc.util.Log;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Packets;
import org.xcore.plugin.listeners.SocketEvents;
import org.xcore.plugin.utils.NetSock;
import org.xcore.plugin.utils.models.PlayerData;

import static arc.util.Strings.stripColors;
import static com.ospx.flubundle.Bundle.args;
import static org.xcore.plugin.PluginVars.*;

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
        PlayerData playerData = database.getCached(player.uuid());
        PlayerData targetData = database.getCached(target.uuid());
        var args = args(
                "nickname", player.coloredName(),
                "nicknameId", playerData.pid,
                "targetNickname", target.coloredName(),
                "targetNicknameId", targetData.pid,
                "reason", reason,
                "votes", votes(),
                "votesRequired", votesRequired());
        bundle.send("votekick-vote", args);
        var message = bundle.format(bundle.defaultLocale, "votekick-vote", args);
        Log.info(message);

        if (votes() == 1) {
            database.getCachedAdminTools("1.3", (v) -> v >= 0, data ->
                    Call.clientPacketReliable(data.player.con, "adm_mod_votekick", targetData.pid + "," + targetData.nickname));
        }

        NetSock.post(new SocketEvents.ServerActionEvent(stripColors(message), config.server));
    }

    @Override
    public void left(Player player) {
        if (voted.remove(player.id) != 0)
            bundle.send("votekick-left", args(
                    "nickname", player.coloredName(),
                    "votes", votes(),
                    "votesRequired", votesRequired()));

        if (target == player && votes() > 0)
            success();
    }

    @Override
    public void success() {
        stop();
        var args = args(
                "nickname", target.coloredName(),
                "minutes", kickDuration / 60000);
        bundle.send("votekick-success", args);
        target.kick(Packets.KickReason.vote, kickDuration);
        NetSock.post(new SocketEvents.ServerActionEvent(bundle.format(bundle.defaultLocale, "votekick-success", args), config.server));
        onKick.get(target);
    }

    @Override
    public void fail() {
        stop();
        bundle.send("votekick-fail", args("nickname", target.coloredName()));
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
