package org.xcore.plugin.vote;

import arc.func.Cons;
import arc.util.Log;
import io.avaje.inject.AssistFactory;
import io.avaje.inject.Assisted;
import jakarta.inject.Inject;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Packets;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.service.BundleService;
import org.xcore.plugin.service.PlayerSessionService;
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

    private final PlayerSessionService playerSessionService;
    private final NetworkService network;
    private final BundleService bundle;
    private final VoteService voteService;
    private final Config config;
    private final GlobalConfig globalConfig;

    @Inject
    public VoteKick(
            @Assisted Player starter,
            @Assisted Player target,
            @Assisted String reason,

            PlayerSessionService playerSessionService,
            NetworkService network,
            BundleService bundleService,
            VoteService voteService,
            Config config,
            GlobalConfig globalConfig) {
        super(globalConfig);
        this.starter = starter;
        this.target = target;
        this.reason = reason;
        this.playerSessionService = playerSessionService;
        this.network = network;
        this.bundle = bundleService;
        this.voteService = voteService;
        this.config = config;
        this.globalConfig = globalConfig;
    }

    public static void setOnKick(Cons<Player> onKick) {
        if (onKick == null) return;
        VoteKick.onKick = onKick;
    }

    @Override
    public void vote(Player player, int sign) {
        super.vote(player, sign);
        PlayerData playerData = playerSessionService.get(player.uuid());
        PlayerData targetData = playerSessionService.get(target.uuid());
        var bundleArgs = args(
                "starter", player.coloredName(),
                "starterId", playerData.pid,
                "target", target.coloredName(),
                "targetId", targetData.pid,
                "reason", reason,
                "votes", votes(),
                "required", votesRequired());
        bundle.send("votekick-vote", bundleArgs);
        var message = bundle.format(bundle.getDefaultLocale(), "votekick-vote", bundleArgs);
        Log.info(message);

        if (votes() == 1) {
            playerSessionService.getCachedAdminTools((v) -> v >= 0, data ->
                    Call.clientPacketReliable(data.player.con, "adm_mod_votekick",
                            targetData.pid + "," + targetData.nickname));
        }

        if (network != null) {
            network.post(new SocketEvents.ServerActionEvent(stripColors(message), config.server));
        }
    }

    @Override
    public void left(Player player) {
        if (voted.remove(player.id) != 0) {
            bundle.send("votekick-left", args(
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
                "nickname", target.coloredName(),
                "minutes", globalConfig.voteKickBanDurationMinutes);
        bundle.send("votekick-success", bundleArgs);
        target.kick(Packets.KickReason.vote, (long) globalConfig.voteKickBanDurationMinutes * 60 * 1000);

        if (network != null) {
            network.post(new SocketEvents.ServerActionEvent(
                    bundle.format(bundle.getDefaultLocale(), "votekick-success", bundleArgs), config.server));
        }
        onKick.get(target);
    }

    public void cancelByAdmin(Player admin) {
        stop();
        var bundleArgs = args(
                "nickname", target.coloredName(),
                "admin", admin.coloredName());
        bundle.send("votekick-cancelled", bundleArgs);
        Log.info(bundle.format(bundle.getDefaultLocale(), "votekick-cancelled", bundleArgs));
    }

    @Override
    public void fail() {
        stop();
        bundle.send("votekick-fail", args("nickname", target.coloredName()));
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
