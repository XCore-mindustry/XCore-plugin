package org.xcore.plugin.integration;

import org.xcore.plugin.common.PLog;
import arc.struct.ObjectSet;
import com.google.gson.Gson;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import mindustry.gen.Call;
import org.xcore.plugin.model.AuthLoginPacket;
import org.xcore.plugin.model.AuthLogoutPacket;
import org.xcore.plugin.model.AuthResultPacket;
import org.xcore.plugin.model.AuthTokenLoginPacket;
import org.xcore.plugin.model.DiscordLinkInfoPacket;
import org.xcore.plugin.service.AdminAuthService;
import org.xcore.plugin.service.DiscordLinkService;
import org.xcore.plugin.service.moderation.ModerationService;
import org.xcore.plugin.ui.menu.DiscordMenu;
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
    private final AdminAuthService adminAuthService;
    private final DiscordLinkService discordLinkService;
    private final DiscordMenu discordMenu;
    private final Gson rawGson;
    private final ObjectSet<String> pendingVanillaBanUuids = new ObjectSet<>();

    @Inject
    public AdminModIntegration(PlayerDataRepository playerDataRepository,
                               SessionService sessionService,
                               ModerationService moderationService,
                               AdminAuthService adminAuthService,
                               DiscordLinkService discordLinkService,
                               DiscordMenu discordMenu,
                               @Named("raw") Gson rawGson) {
        this.playerDataRepository = playerDataRepository;
        this.sessionService = sessionService;
        this.moderationService = moderationService;
        this.adminAuthService = adminAuthService;
        this.discordLinkService = discordLinkService;
        this.discordMenu = discordMenu;
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
                PLog.err("Failed to process ban request from '@': @", player.name, e.getMessage());
                session.locale().send("error-processing-request", args());
                return;
            }

            if (req == null) {
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
            PLog.info("@ banned @ (@) for @", player.plainName(), targetData.nickname, targetData.uuid, req.duration);
            session.locale().send("commands-ban-success", args("nickname", targetData.nickname));
        });

        netServer.addPacketHandler("cancel_ban_data", (player, content) -> {
            if (!player.admin) return;
            var session = sessionService.get(player);
            BanRequestData req;
            try {
                req = rawGson.fromJson(content, BanRequestData.class);
            } catch (Exception e) {
                PLog.err("Failed to process ban cancellation from '@': @", player.name, e.getMessage());
                if (session != null) {
                    session.locale().send("error-processing-request", args());
                }
                return;
            }

            if (req == null) {
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
            PLog.info("Player @ joined with the Admin mod version '@'", player.plainName(), content);

            var requiredVersion = "1.3";
            if (VersionComparator.compareVersions(content, "1.3") < 0) {
                String kickMsg = session.locale().format("kick-admintools-outdated", args(
                        "version", content,
                        "requiredVersion", requiredVersion));
                player.con.kick(kickMsg, 0);
                return;
            }
            data.adminModVersion = content;

            // Push updated auth & discord link status to AdminTools client
            adminAuthService.pushStatus(player);
        });

        netServer.addPacketHandler("adm_auth_status_req", (player, content) -> {
            adminAuthService.pushStatus(player);
        });

        netServer.addPacketHandler("adm_discord_link_req", (player, content) -> {
            if (player == null) return;
            var session = sessionService.get(player);
            boolean hasMod = session != null && session.data != null && session.data.adminModVersion != null;
            if (session != null && player.con != null) {
                var res = discordLinkService.getOrCreateActiveCode(session);
                if (res.success()) {
                    var packet = new DiscordLinkInfoPacket(true, res.code(), res.expiresAt(), null);
                    Call.clientPacketReliable(player.con, "adm_discord_link_info", rawGson.toJson(packet));
                } else {
                    var packet = new DiscordLinkInfoPacket(false, "", 0L, res.errorKey());
                    Call.clientPacketReliable(player.con, "adm_discord_link_info", rawGson.toJson(packet));
                }
            }
            // Only show intrusive server dialog for vanilla/mobile players without the modern mod UI
            if (!hasMod) {
                discordMenu.linking(player.uuid(), false);
            }
        });

        netServer.addPacketHandler("adm_auth_login", (player, content) -> {
            if (player == null || player.con == null) return;
            AuthLoginPacket req;
            try {
                req = rawGson.fromJson(content, AuthLoginPacket.class);
            } catch (Exception e) {
                PLog.err("Failed to process auth request from '@': @", player.name, e.getMessage());
                if (player.con != null) {
                    var resp = new AuthResultPacket(-1, "SESSION_NOT_FOUND", "error-processing-request");
                    Call.clientPacketReliable(player.con, "adm_auth_result", rawGson.toJson(resp));
                }
                return;
            }

            if (req == null) {
                if (player.con != null) {
                    var resp = new AuthResultPacket(-1, "SESSION_NOT_FOUND", "error-processing-request");
                    Call.clientPacketReliable(player.con, "adm_auth_result", rawGson.toJson(resp));
                }
                return;
            }

            try {
                var result = adminAuthService.authenticate(player, req.password, req.rememberDevice);
                if (player.con != null) {
                    var resp = new AuthResultPacket(req.requestId, result.status().name(), result.messageKey(), result.token());
                    Call.clientPacketReliable(player.con, "adm_auth_result", rawGson.toJson(resp));

                    adminAuthService.pushStatus(player);
                }
            } catch (Exception e) {
                PLog.err("Error during authentication for '@': @", player.name, e.getMessage());
                if (player.con != null) {
                    var resp = new AuthResultPacket(req.requestId, "SESSION_NOT_FOUND", "error-processing-request");
                    Call.clientPacketReliable(player.con, "adm_auth_result", rawGson.toJson(resp));
                }
            }
        });

        netServer.addPacketHandler("adm_auth_token_login", (player, content) -> {
            if (player == null || player.con == null) return;
            AuthTokenLoginPacket req;
            try {
                req = rawGson.fromJson(content, AuthTokenLoginPacket.class);
            } catch (Exception e) {
                PLog.err("Failed to process token auth request from '@': @", player.name, e.getMessage());
                if (player.con != null) {
                    var resp = new AuthResultPacket(-1, "TOKEN_INVALID", "error-token-invalid");
                    Call.clientPacketReliable(player.con, "adm_auth_result", rawGson.toJson(resp));
                }
                return;
            }

            if (req == null) {
                if (player.con != null) {
                    var resp = new AuthResultPacket(-1, "TOKEN_INVALID", "error-token-invalid");
                    Call.clientPacketReliable(player.con, "adm_auth_result", rawGson.toJson(resp));
                }
                return;
            }

            try {
                var result = adminAuthService.authenticateToken(player, req.token);
                if (player.con != null) {
                    var resp = new AuthResultPacket(req.requestId, result.status().name(), result.messageKey(), result.token());
                    Call.clientPacketReliable(player.con, "adm_auth_result", rawGson.toJson(resp));

                    adminAuthService.pushStatus(player);
                }
            } catch (Exception e) {
                PLog.err("Error during token authentication for '@': @", player.name, e.getMessage());
                if (player.con != null) {
                    var resp = new AuthResultPacket(req.requestId, "TOKEN_INVALID", "error-token-invalid");
                    Call.clientPacketReliable(player.con, "adm_auth_result", rawGson.toJson(resp));
                }
            }
        });

        netServer.addPacketHandler("adm_auth_logout", (player, content) -> {
            if (player == null) return;
            AuthLogoutPacket req = null;
            if (content != null && !content.isBlank()) {
                try {
                    req = rawGson.fromJson(content, AuthLogoutPacket.class);
                } catch (Exception ignored) {}
            }
            adminAuthService.logout(player, req != null ? req.token : null);
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
