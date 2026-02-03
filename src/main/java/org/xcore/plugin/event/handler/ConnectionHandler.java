package org.xcore.plugin.event.handler;

import arc.util.Log;
import arc.util.Time;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.game.EventType.PlayerJoin;
import mindustry.game.EventType.PlayerLeave;
import mindustry.gen.Call;
import mindustry.gen.Player;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.AdminDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.gamemode.hexed.HexedRanks;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.BundleService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.PlayerSessionService;
import org.xcore.plugin.vote.VoteService;
import org.xcore.plugin.event.SocketEvents;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.netServer;

@Singleton
public class ConnectionHandler {

    private final PlayerSessionService playerSessionService;
    private final PlayerDataRepository playerDataRepository;
    private final AdminDataRepository adminDataRepository;
    private final NetworkService network;
    private final Config config;
    private final GlobalConfig globalConfig;
    private final BundleService bundleService;
    private final VoteService voteService;

    @Inject
    public ConnectionHandler(PlayerSessionService playerSessionService,
                             PlayerDataRepository playerDataRepository,
                             AdminDataRepository adminDataRepository,
                             NetworkService network,
                             Config config,
                             GlobalConfig globalConfig,
                             BundleService bundleService,
                             VoteService voteService) {
        this.playerSessionService = playerSessionService;
        this.playerDataRepository = playerDataRepository;
        this.adminDataRepository = adminDataRepository;
        this.network = network;
        this.config = config;
        this.globalConfig = globalConfig;
        this.bundleService = bundleService;
        this.voteService = voteService;
    }

    public void onPlayerJoin(PlayerJoin event) {
        var player = event.player;

        Time.runTask(120, () -> {
            if (player != null && player.con != null && player.con.isConnected()) {
                if (player.con.lastReceivedClientSnapshot == -1) {
                    String kickMsg = bundleService.format(bundleService.locale(player), "kick-bot-protection", args());
                    player.kick(kickMsg);
                }
            }
        });

        bundleService.send(player, "welcome", args("serverName", mindustry.net.Administration.Config.serverName.string()));
        PlayerData data = playerDataRepository.findByPlayer(player);

        if (data == null) data = new PlayerData(player.uuid(), false);

        data.setNickname(player.coloredName()).setPlayer(player);

        Call.clientPacketReliable(player.con, "adm_mod_begin", "");

        if (data.exists && !data.ip.equals(player.ip())) {
            if (player.admin) {
                var adminData = adminDataRepository.findByUuid(data.uuid);
                adminData.adminConfirmed = false;
                adminDataRepository.save(adminData);

                player.admin = false;
                netServer.admins.unAdminPlayer(player.uuid());
                bundleService.send(player, "error-ip-changed", args());
            }

            data.setIp(player.ip());
            playerDataRepository.save(data);
        }

        if (!data.exists) {
            // data.generatePid(); now, automatically generated
            data.setIp(player.ip());
            playerDataRepository.save(data);
        }

        HexedRanks.updateRank(player, data, config);
        playerSessionService.update(data);

        if (player.getInfo().timesJoined < 5) {
            Call.openURI(player.con, globalConfig.discordUrl);
        }

        Log.info("@ #@ @ joined", player.plainName(), data.pid, player.uuid());
        bundleService.send("player-joined", args(
                "nickname", player.coloredName(),
                "pid", data.pid));
        network.post(new SocketEvents.PlayerJoinLeaveEvent(
                player.plainName() + " #" + data.pid,
                config.server,
                true)
        );
    }

    public void onPlayerLeave(PlayerLeave event) {
        Player player = event.player;

        var data = playerSessionService.registerLogout(event.player.uuid());

        voteService.handleLeave(event.player);

        if (data != null) {
            Log.info("@ #@ @ left", player.plainName(), data.pid, player.uuid());
            bundleService.send("player-left", args(
                    "nickname", player.coloredName(),
                    "pid", data.pid)
            );

            network.post(new SocketEvents.PlayerJoinLeaveEvent(
                    player.plainName() + " #" + data.pid,
                    config.server,
                    false)
            );
        }
    }
}
