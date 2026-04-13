package org.xcore.plugin.command.controller.server;

import arc.struct.Seq;
import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.cloud.annotation.DefaultUnit;
import org.xcore.plugin.command.controller.CloudServerController;
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

@Singleton
public class ServerModerationController implements CloudServerController {

    private final ModerationService moderationService;
    private final BanDataRepository banDataRepository;

    @Inject
    public ServerModerationController(ModerationService moderationService,
                                      BanDataRepository banDataRepository) {
        this.moderationService = moderationService;
        this.banDataRepository = banDataRepository;
    }

    @Command("tempban <target> <period> [reason]")
    @CommandDescription("Temporarily bans a player by Name, UUID, IP, or #ID.")
    public void tempBan(XCoreSender sender,
                        @Argument(value = "target", description = "Player Name/#ID/UUID/IP") String arg,
                        @Argument(value = "period", description = "Duration (e.g. 1d, 30m)") @DefaultUnit(TimeUnit.DAYS) Duration period,
                        @Argument(value = "reason", description = "Reason for the ban") @Greedy String reason) {

        String uuid = null;
        String ip = null;
        String name = "Unknown";

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
            var info = netServer.admins.getInfoOptional(arg);
            if (info != null) {
                uuid = arg;
                ip = info.lastIP;
                name = info.lastName;
            } else {
                ip = arg;
            }
        }

        String effectiveReason = reason == null || reason.isBlank() ? null : reason;
        var result = moderationService.tempBanByUuidOrIp(uuid, ip, name, period, effectiveReason, "console", null);

        if (result.isSuccess()) {
            var ban = result.getData().get();
            Log.info("Banned @ until @", ban.name, ban.expireDate);
        } else {
            Log.err(result.getMessage().orElse("Ban failed"));
        }
    }

    @Command("tempunban <type> <value>")
    @CommandDescription("Unbans a temporary ban.")
    public void tempUnban(XCoreSender sender,
                          @Argument(value = "type", description = "Identifier type: uuid/uid, ip, id") String type,
                          @Argument(value = "value", description = "The identifier value") String value) {

        String uuid = null, ip = null;
        switch (type.toLowerCase()) {
            case "uuid", "uid" -> uuid = value;
            case "ip" -> ip = value;
            case "id" -> {
                var d = moderationService.findPlayerData("#" + value);
                if (d != null) uuid = d.uuid;
            }
        }

        var result = moderationService.tempUnban(uuid, ip, "console", null);
        if (result.isSuccess()) {
            Log.info("Unbanned: UUID=@ / IP=@", uuid, ip);
        } else {
            Log.err(result.getMessage().orElse("Unban failed"));
        }
    }

    @Command("tempbans [search]")
    @CommandDescription("Lists active temporary bans.")
    public void tempBans(XCoreSender sender,
                         @Argument(value = "search", description = "Filter by Name/UUID/IP") String q) {

        Seq<BanData> bans = Seq.with(banDataRepository.findAll());
        if (q != null && !q.isBlank()) {
            bans.select(b -> deepEquals(b.name, q) || equalsNonEmpty(b.ip, q) || equalsNonEmpty(b.uuid, q));
        }
        bans.each(b -> Log.info("Ban: @ (@) until @. Reason: @", b.name, b.uuid,
                b.expireDate.atZone(ZoneId.systemDefault()).toLocalDateTime(), b.reason));
    }

    @Command("mute <target> <period> [reason]")
    @CommandDescription("Mutes a player by #ID or UUID.")
    public void mute(XCoreSender sender,
                     @Argument(value = "target", description = "Player #ID/UUID") String target,
                     @Argument(value = "period", description = "Duration (e.g. 1h, 30m)") @DefaultUnit(TimeUnit.HOURS) Duration period,
                     @Argument(value = "reason", description = "Reason for the mute") @Greedy String reason) {

        PlayerData data = moderationService.findPlayerData(target);
        if (data == null) {
            Log.err("Player not found");
            return;
        }

        String effectiveReason = reason == null || reason.isBlank() ? null : reason;
        var result = moderationService.muteById(data.pid, "console", null, effectiveReason, period);

        if (result.isSuccess()) {
            Log.info("Muted @ for @ minutes.", data.nickname, Duration.ofMillis(period.toMillis()).toMinutes());
        } else {
            Log.err(result.getMessage().orElse("Mute failed"));
        }
    }

    @Command("unmute <target>")
    @CommandDescription("Unmutes a player by #ID or UUID.")
    public void unmute(XCoreSender sender,
                       @Argument(value = "target", description = "Player #ID/UUID") String target) {

        PlayerData data = moderationService.findPlayerData(target);
        if (data == null) {
            Log.err("Player not found");
            return;
        }

        var result = moderationService.unmuteById(data.pid, "console", null);
        if (result.isSuccess()) {
            Log.info("Unmuted @", data.nickname);
        } else {
            Log.err(result.getMessage().orElse("Unmute failed"));
        }
    }
}
