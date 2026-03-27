package org.xcore.plugin.vote;

import arc.func.Cons;
import arc.util.Log;
import com.ospx.flubundle.Bundle;
import io.avaje.inject.AssistFactory;
import io.avaje.inject.Assisted;
import jakarta.inject.Inject;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Packets;
import org.xcore.plugin.common.VersionComparator;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.model.PlayerData;

import static arc.util.Strings.stripColors;
import static com.ospx.flubundle.Bundle.args;

@AssistFactory(VoteKickFactory.class)
public class VoteKick extends VoteSession {

    public static Cons<Player> onKick = (player) -> {};

    public final Player starter;
    public final Player target;
    public final String reason;
    public Localization systemLocal;

    private final SessionService sessionService;
    private final NetworkService network;
    private final VoteService voteService;
    private final Config config;
    private final GlobalConfig globalConfig;

    @Inject
    public VoteKick(
            @Assisted Player starter,
            @Assisted Player target,
            @Assisted String reason,
            Bundle bundle,

            SessionService sessionService,
            NetworkService network,
            VoteService voteService,
            Config config,
            GlobalConfig globalConfig) {
        super(globalConfig);
        this.starter = starter;
        this.target = target;
        this.reason = reason;
        this.systemLocal = new Localization(bundle);
        this.sessionService = sessionService;
        this.network = network;
        this.voteService = voteService;
        this.config = config;
        this.globalConfig = globalConfig;
    }

    public static void setOnKick(Cons<Player> onKick) {
        if (onKick == null) return;
        VoteKick.onKick = onKick;
    }

    public boolean isTarget(Player player) {
        return target == player;
    }

    @Override
    public void vote(Player player, int sign) {
        super.vote(player, sign);
        var playerSession = sessionService.get(player.uuid());
        var targetSession = sessionService.get(target.uuid());
        PlayerData playerData = playerSession != null ? playerSession.data : sessionService.getOrLoadFromDb(player.uuid());
        PlayerData targetData = targetSession != null ? targetSession.data : sessionService.getOrLoadFromDb(target.uuid());

        int playerPid = playerData != null ? playerData.pid : -1;
        int targetPid = targetData != null ? targetData.pid : -1;
        String targetNickname = targetData != null ? targetData.nickname : target.plainName();

        var bundleArgs = args(
                "starter", player.coloredName(),
                "starterId", playerPid,
                "target", target.coloredName(),
                "targetId", targetPid,
                "reason", reason,
                "votes", votes(),
                "required", votesRequired());
        sessionService.broadcast("votekick-vote", bundleArgs);
        var message = systemLocal.format("votekick-vote", bundleArgs);
        Log.info(message);

        if (votes() == 1) {
            sessionService.getCachedAdminTools(
                    (v) -> VersionComparator.compareVersions(v, "0") >= 0,
                    data -> Call.clientPacketReliable(data.player.con, "adm_mod_votekick",
                            targetPid + "," + targetNickname)
            );
        }

        if (network != null) {
            network.post(new SocketEvents.ServerActionEvent(stripColors(message), config.server));
        }
    }

    @Override
    public void left(Player player) {
        if (voted.remove(player.id) != 0) {
            sessionService.broadcast("votekick-left", args(
                    "player", player.coloredName(),
                    "votes", votes(),
                    "required", votesRequired()));
        }

        if (target == player && votes() > 0) {
            success();
        }
    }

    @Override
    public void success() {
        stop();
        var bundleArgs = args(
                "target", target.coloredName(),
                "minutes", globalConfig.voteKickBanDurationMinutes);
        sessionService.broadcast("votekick-success", bundleArgs);
        target.kick(Packets.KickReason.vote, (long) globalConfig.voteKickBanDurationMinutes * 60 * 1000);

        if (network != null) {
            network.post(new SocketEvents.ServerActionEvent(
                    systemLocal.format("votekick-success", bundleArgs), config.server));
        }
        onKick.get(target);
    }

    @Override
    public void cancelByAdmin(Player admin) {
        stop();
        var bundleArgs = args(
                "target", target.coloredName(),
                "admin", admin.coloredName());
        sessionService.broadcast("votekick-cancelled", bundleArgs);
        Log.info(systemLocal.format("votekick-cancelled", bundleArgs));
    }

    @Override
    public void fail() {
        stop();
        sessionService.broadcast("votekick-fail", args("target", target.coloredName()));
    }

    @Override
    public void stop() {
        if (end != null) {
            end.cancel();
        }
        voteService.endVote();
    }

    @Override
    public int votesRequired() {
        return Groups.player.size() > 3 ? 3 : 2;
    }
}
