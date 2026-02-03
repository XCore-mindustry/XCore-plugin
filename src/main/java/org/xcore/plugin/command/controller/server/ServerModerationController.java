package org.xcore.plugin.command.controller.server;

import arc.struct.Seq;
import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.command.core.annotation.Command;
import org.xcore.plugin.command.core.context.ServerContext;
import org.xcore.plugin.database.repository.BanDataRepository;
import org.xcore.plugin.model.BanData;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.moderation.ModerationService;

import java.time.Duration;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

import static mindustry.Vars.netServer;
import static org.xcore.plugin.common.TextUtils.deepEquals;
import static org.xcore.plugin.common.TextUtils.equalsNonEmpty;

import org.xcore.plugin.command.core.ServerController;
@Singleton
public class ServerModerationController implements ServerController {

    private final ModerationService moderationService;
    private final BanDataRepository banDataRepository;

    @Inject
    public ServerModerationController(ModerationService moderationService,
                                      BanDataRepository banDataRepository) {
        this.moderationService = moderationService;
        this.banDataRepository = banDataRepository;
    }

    @Command(name = "tempban", params = "<uuid/ip/#id> <period> [reason...]", description = "Temp-ban a player.")
    public void tempBan(ServerContext ctx) {
        String arg = ctx.arg(0);
        String uuid = null;
        String ip = null;
        String name = "Unknown";

        // If it starts with #, treat it as player ID
        if (arg.startsWith("#")) {
            var target = moderationService.findPlayerData(arg);
            if (target == null) {
                Log.err("Player not found");
                return;
            }
            uuid = target.uuid;
            name = target.nickname;
            var info = netServer.admins.getInfoOptional(uuid);
            if (info != null) ip = info.lastIP;
        } else {
            // Try to find by UUID first
            var info = netServer.admins.getInfoOptional(arg);
            if (info != null) {
                uuid = arg;
                ip = info.lastIP;
                name = info.lastName;
            } else {
                // If not found by UUID, treat as IP
                ip = arg;
            }
        }

        var period = moderationService.parsePeriod(ctx.arg(1), TimeUnit.DAYS);
        if (period == null) {
            Log.err("Invalid period format.");
            return;
        }

        String reason = ctx.args().length > 2 ? ctx.arg(2) : null;
        var result = moderationService.tempBanByUuidOrIp(uuid, ip, name, period, reason, "console");

        if (result.isSuccess()) {
            var ban = result.getData().get();
            Log.info("Banned @ until @", ban.name, ban.expireDate);
        } else {
            Log.err(result.getMessage().orElse("Ban failed"));
        }
    }

    @Command(name = "tempunban", params = "<type:uuid/ip/id> <value>", description = "Unban player.")
    public void tempUnban(ServerContext ctx) {
        String uuid = null, ip = null;
        switch (ctx.arg(0).toLowerCase()) {
            case "uuid", "uid" -> uuid = ctx.arg(1);
            case "ip" -> ip = ctx.arg(1);
            case "id" -> {
                var d = moderationService.findPlayerData("#" + ctx.arg(1));
                if (d != null) uuid = d.uuid;
            }
        }

        var result = moderationService.tempUnban(uuid, ip);
        if (result.isSuccess()) {
            Log.info("Unbanned: UUID=@ / IP=@", uuid, ip);
        } else {
            Log.err(result.getMessage().orElse("Unban failed"));
        }
    }

    @Command(name = "tempbans", params = "[search...]", description = "List current temp bans.")
    public void tempBans(ServerContext ctx) {
        // todo: paged
        Seq<BanData> bans = Seq.with(banDataRepository.findAll());
        if (ctx.args().length > 0) {
            String q = ctx.arg(0);
            bans.select(b -> deepEquals(b.name, q) || equalsNonEmpty(b.ip, q) || equalsNonEmpty(b.uuid, q));
        }
        bans.each(b -> Log.info("Ban: @ (@) until @. Reason: @", b.name, b.uuid,
                b.expireDate.atZone(ZoneId.systemDefault()).toLocalDateTime(), b.reason));
    }

    @Command(name = "mute", params = "<uuid/#id> <period> [reason...]", description = "Mute player.")
    public void mute(ServerContext ctx) {
        PlayerData data = moderationService.findPlayerData(ctx.arg(0));
        if (data == null) {
            Log.err("Player not found");
            return;
        }

        var period = moderationService.parsePeriod(ctx.arg(1), TimeUnit.HOURS);
        if (period == null) {
            Log.err("Invalid period format");
            return;
        }

        String reason = ctx.args().length > 2 ? ctx.arg(2) : null;
        var result = moderationService.muteById(data.pid, "console", reason, period);

        if (result.isSuccess()) {
            Log.info("Muted @ for @ minutes.", data.nickname, Duration.ofMillis(period.toEpochMilli()).toMinutes());
        } else {
            Log.err(result.getMessage().orElse("Mute failed"));
        }
    }

    @Command(name = "unmute", params = "<uuid/#id>", description = "Unmute player.")
    public void unmute(ServerContext ctx) {
        PlayerData data = moderationService.findPlayerData(ctx.arg(0));
        if (data == null) {
            Log.err("Player not found");
            return;
        }

        var result = moderationService.unmuteById(data.pid);
        if (result.isSuccess()) {
            Log.info("Unmuted @", data.nickname);
        } else {
            Log.err(result.getMessage().orElse("Unmute failed"));
        }
    }
}
