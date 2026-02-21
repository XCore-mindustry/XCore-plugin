package org.xcore.plugin.integration;

import arc.util.Log;
import com.google.gson.Gson;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import mindustry.gen.Call;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.service.TimeService;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.database.repository.BanDataRepository;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.common.VersionComparator;
import org.xcore.plugin.model.BanData;
import org.xcore.plugin.model.BanRequestData;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.netServer;

@Singleton
public class AdminModIntegration {

    private final PlayerDataRepository playerDataRepository;
    private final BanDataRepository banDataRepository;
    private final SessionService sessionService;
    private final NetworkService network;
    private final Gson rawGson;
    private final TimeService time;

    @Inject
    public AdminModIntegration(PlayerDataRepository playerDataRepository,
                               BanDataRepository banDataRepository,
                               SessionService sessionService,
                               NetworkService network,
                               @Named("raw") Gson rawGson,
                               TimeService timeService) {
        this.playerDataRepository = playerDataRepository;
        this.banDataRepository = banDataRepository;
        this.sessionService = sessionService;
        this.network = network;
        this.rawGson = rawGson;
        this.time = timeService;
    }

    @PostConstruct
    public void init() {
        netServer.addPacketHandler("take_ban_data", (player, content) -> {
            if (!player.admin) return;
            var session = sessionService.get(player);
            if (session == null) return;

            BanRequestData req;
            try {
                req = rawGson.fromJson(content, BanRequestData.class);
            } catch (Exception e) {
                Log.err("Error processing ban request from @: @", player.name, e.getMessage());
                session.locale().send("error-processing-request", args());
                return;
            }

            var targetData = playerDataRepository.findByPid(req.pid);

            if (targetData == null) {
                session.locale().send("error-player-not-found", args());
                Call.clientPacketReliable(player.con, "give_ban_data", content);
                return;
            }

            Instant date = time.parsePeriod(req.duration, TimeUnit.DAYS);

            if (date == null) {
                session.locale().send("error-wrong-period-format", args());
                Call.clientPacketReliable(player.con, "give_ban_data", content);
                return;
            }

            netServer.admins.unbanPlayerID(targetData.uuid);

            var ban = BanData.builder()
                    .name(req.name)
                    .uuid(targetData.uuid)
                    .ip(targetData.ip)
                    .adminName(player.name)
                    .reason(req.reason)
                    .expireDate(Instant.now().plusMillis(date.toEpochMilli()))
                    .build();
            network.post(ban);
            banDataRepository.save(ban);
        });

        netServer.addPacketHandler("cancel_ban_data", (player, content) -> {
            if (!player.admin) return;
            var session = sessionService.get(player);
            BanRequestData req = rawGson.fromJson(content, BanRequestData.class);

            var targetData = playerDataRepository.findByPid(req.pid);
            netServer.admins.unbanPlayerID(targetData.uuid);

            if (session != null) {
                session.locale().send("ban-cancelled", args("nickname", targetData.nickname));
            }
        });

        netServer.addPacketHandler("adm_mod_end", (player, content) -> {
            var session = sessionService.get(player);
            if (session == null) return;
            var data = session.data;

            if (data == null || data.adminModVersion != null) return;
            Log.info("Player @ joined with the Admin mod version '@'", player.plainName(), content);

            var requiredVersion = "1.3";
            if (VersionComparator.compareVersions(content, "1.3") < 0) {
                String kickMsg = session.locale().format("kick-admintools-outdated", args(
                        "version", content,
                        "requiredVersion", requiredVersion));
                player.con.kick(kickMsg, 0);
                return;
            }
            data.adminModVersion = content;
        });
    }
}
