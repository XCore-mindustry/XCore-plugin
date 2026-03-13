package org.xcore.plugin.integration;

import arc.util.Log;
import arc.struct.ObjectSet;
import com.google.gson.Gson;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import mindustry.gen.Call;
import org.xcore.plugin.service.moderation.ModerationService;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.common.VersionComparator;
import org.xcore.plugin.model.BanRequestData;

import java.util.concurrent.TimeUnit;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.netServer;

@Singleton
public class AdminModIntegration {

    private final PlayerDataRepository playerDataRepository;
    private final SessionService sessionService;
    private final ModerationService moderationService;
    private final Gson rawGson;
    private final ObjectSet<String> pendingVanillaBanUuids = new ObjectSet<>();

    @Inject
    public AdminModIntegration(PlayerDataRepository playerDataRepository,
                               SessionService sessionService,
                               ModerationService moderationService,
                               @Named("raw") Gson rawGson) {
        this.playerDataRepository = playerDataRepository;
        this.sessionService = sessionService;
        this.moderationService = moderationService;
        this.rawGson = rawGson;
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

            var duration = moderationService.parsePeriod(req.duration, TimeUnit.DAYS);

            if (duration == null) {
                session.locale().send("error-wrong-period-format", args());
                Call.clientPacketReliable(player.con, "give_ban_data", content);
                return;
            }

            var result = moderationService.banById(req.pid, player.name, session.data.discordId, req.reason, duration, true);

            if (!result.isSuccess() || result.getData().isEmpty()) {
                session.locale().send("error-processing-request", args());
                Call.clientPacketReliable(player.con, "give_ban_data", content);
                return;
            }

            clearPendingVanillaBan(targetData.uuid);
            sessionService.broadcast("tempban-player-banned", args(
                    "adminName", player.coloredName(),
                    "playerName", req.name != null ? req.name : targetData.nickname
            ));
            Log.info("@ banned @ (@) for @", player.plainName(), targetData.nickname, targetData.uuid, req.duration);
            session.locale().send("commands-ban-success", args("nickname", targetData.nickname));
        });

        netServer.addPacketHandler("cancel_ban_data", (player, content) -> {
            if (!player.admin) return;
            var session = sessionService.get(player);
            BanRequestData req;
            try {
                req = rawGson.fromJson(content, BanRequestData.class);
            } catch (Exception e) {
                Log.err("Error processing ban cancellation from @: @", player.name, e.getMessage());
                if (session != null) {
                    session.locale().send("error-processing-request", args());
                }
                return;
            }

            var targetData = playerDataRepository.findByPid(req.pid);
            if (targetData != null) {
                clearPendingVanillaBan(targetData.uuid);
            }

            if (session != null && targetData != null) {
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

    public void holdVanillaBan(String uuid) {
        if (uuid == null) {
            return;
        }

        netServer.admins.banPlayerID(uuid);
        pendingVanillaBanUuids.add(uuid);
    }

    private void clearPendingVanillaBan(String uuid) {
        if (uuid == null || !pendingVanillaBanUuids.remove(uuid)) {
            return;
        }

        netServer.admins.unbanPlayerID(uuid);
    }
}
