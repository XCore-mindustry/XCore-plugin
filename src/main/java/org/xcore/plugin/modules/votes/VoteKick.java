package org.xcore.plugin.modules.votes;

import arc.func.Cons;
import arc.util.Log;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Packets;
import org.xcore.plugin.listeners.SocketEvents;
import org.xcore.plugin.modules.Config;
import org.xcore.plugin.modules.GlobalConfig;
import org.xcore.plugin.modules.bundles.BundleService;
import org.xcore.plugin.modules.database.DatabaseService;
import org.xcore.plugin.modules.network.NetworkService;
import org.xcore.plugin.utils.models.PlayerData;

import static arc.util.Strings.stripColors;
import static com.ospx.flubundle.Bundle.args;

public class VoteKick extends VoteSession {

    public static Cons<Player> onKick = (player) -> {};

    public final Player starter;
    public final Player target;
    public final String reason;

    private final DatabaseService database;
    private final NetworkService network;
    private final BundleService bundle;
    private final VoteService voteService;
    private final Config config;
    private final GlobalConfig globalConfig;

    public VoteKick(Player starter, Player target, String reason, DatabaseService database,
                    NetworkService network, BundleService bundleService, VoteService voteService,
                    Config config, GlobalConfig globalConfig) {
        super(globalConfig);
        this.starter = starter;
        this.target = target;
        this.reason = reason;
        this.database = database;
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
        PlayerData playerData = database.getCached(player.uuid());
        PlayerData targetData = database.getCached(target.uuid());
        var bundleArgs = args(
                "nickname", player.coloredName(),
                "nicknameId", playerData.pid,
                "targetNickname", target.coloredName(),
                "targetNicknameId", targetData.pid,
                "reason", reason,
                "votes", votes(),
                "votesRequired", votesRequired());
        bundle.send("votekick-vote", bundleArgs);
        var message = bundle.format(bundle.getDefaultLocale(), "votekick-vote", bundleArgs);
        Log.info(message);

        if (votes() == 1) {
            database.getCachedAdminTools("1.3", (v) -> v >= 0, data ->
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
                    "nickname", player.coloredName(),
                    "votes", votes(),
                    "votesRequired", votesRequired()));
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
                "minutes", globalConfig.voteKickBanDurationMinutes / 60000);
        bundle.send("votekick-success", bundleArgs);
        target.kick(Packets.KickReason.vote, globalConfig.voteKickBanDurationMinutes);

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
