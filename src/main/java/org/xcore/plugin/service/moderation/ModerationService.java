package org.xcore.plugin.service.moderation;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.database.repository.BanDataRepository;
import org.xcore.plugin.database.repository.MuteDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.model.BanData;
import org.xcore.plugin.model.MuteData;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.FindService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.service.TimeService;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static mindustry.Vars.netServer;

/**
 * Centralized moderation service handling ban, unban, mute, unmute operations.
 * Eliminates code duplication across client, server, and Discord controllers.
 */
@Singleton
public class ModerationService {

    private final PlayerDataRepository playerDataRepository;
    private final BanDataRepository banDataRepository;
    private final MuteDataRepository muteDataRepository;
    private final SessionService sessionService;
    private final NetworkService network;
    private final FindService find;
    private final TimeService time;

    @Inject
    public ModerationService(PlayerDataRepository playerDataRepository,
                             BanDataRepository banDataRepository,
                             MuteDataRepository muteDataRepository,
                             SessionService sessionService,
                             NetworkService network,
                             FindService find,
                             TimeService timeService) {
        this.playerDataRepository = playerDataRepository;
        this.banDataRepository = banDataRepository;
        this.muteDataRepository = muteDataRepository;
        this.sessionService = sessionService;
        this.network = network;
        this.find = find;
        this.time = timeService;
    }

    /**
     * Ban a player by their ID.
     *
     * @param id          Player ID
     * @param adminName   Name of the admin performing the ban
     * @param reason      Ban reason
     * @param duration    Ban duration period
     * @param kickOnline  Whether to kick the player if online
     * @return Result containing BanData if successful
     */
    public ModerationResult<BanData> banById(int id, String adminName, String reason, Instant duration, boolean kickOnline) {
        var target = playerDataRepository.findByPid(id);
        if (target == null) {
            return ModerationResult.failure("Player not found");
        }

        Instant unbanDate = Instant.now().plusMillis(duration.toEpochMilli());
        var info = netServer.admins.getInfoOptional(target.uuid);
        String ip = (info != null) ? info.lastIP : null;

        if (kickOnline) {
            network.post(new SocketEvents.KickBannedPlayer(target.uuid, ip));
        }

        BanData ban = BanData.builder()
                .name(target.nickname)
                .uuid(target.uuid)
                .ip(ip)
                .adminName(adminName)
                .reason(reason != null ? reason : "Not Specified")
                .expireDate(unbanDate)
                .build();

        network.post(ban);
        banDataRepository.save(ban);

        return ModerationResult.success("Player '" + target.nickname + "' banned successfully", ban);
    }

    /**
     * Unban a player by their ID.
     *
     * @param id Player ID
     * @return Result containing PlayerData if successful
     */
    public ModerationResult<PlayerData> unbanById(int id) {
        var target = playerDataRepository.findByPid(id);
        if (target == null) {
            return ModerationResult.failure("Player not found");
        }

        banDataRepository.delete(target.uuid, null);

        return ModerationResult.success("Player '" + target.nickname + "' unbanned successfully", target);
    }

    /**
     * Mute a player by their ID.
     *
     * @param id        Player ID
     * @param adminName Name of the admin performing the mute
     * @param reason    Mute reason
     * @param duration  Mute duration period
     * @return Result containing MuteData if successful
     */
    public ModerationResult<MuteData> muteById(int id, String adminName, String reason, Instant duration) {
        var target = sessionService.getOrLoadFromDb(id);
        if (target == null) {
            return ModerationResult.failure("Player not found");
        }

        Instant expireDate = Instant.now().plusMillis(duration.toEpochMilli());

        MuteData mute = MuteData.builder()
                .uuid(target.uuid)
                .name(target.nickname)
                .adminName(adminName)
                .reason(reason != null ? reason : "Not Specified")
                .expireDate(expireDate)
                .build();

        muteDataRepository.save(mute);
        network.post(mute);

        return ModerationResult.success("Player '" + target.nickname + "' muted successfully", mute);
    }

    /**
     * Unmute a player by their ID.
     *
     * @param id Player ID
     * @return Result containing PlayerData if successful
     */
    public ModerationResult<PlayerData> unmuteById(int id) {
        var target = sessionService.getOrLoadFromDb(id);
        if (target == null) {
            return ModerationResult.failure("Player not found");
        }

        muteDataRepository.delete(target.uuid);

        return ModerationResult.success("Player '" + target.nickname + "' unmuted successfully", target);
    }

    /**
     * Temporary ban a player by UUID or IP.
     *
     * @param uuid      Player UUID (can be null)
     * @param ip        Player IP (can be null)
     * @param name      Player name
     * @param duration  Ban duration period
     * @param reason    Ban reason
     * @param adminName Name of the admin performing the ban
     * @return Result containing BanData if successful
     */
    public ModerationResult<BanData> tempBanByUuidOrIp(String uuid, String ip, String name, Instant duration, String reason, String adminName) {
        if (uuid == null && ip == null) {
            return ModerationResult.failure("Either UUID or IP must be provided");
        }

        Instant expire = Instant.now().plusMillis(duration.toEpochMilli());
        network.post(new SocketEvents.KickBannedPlayer(uuid, ip));

        BanData ban = BanData.builder()
                .name(name != null ? name : "Unknown")
                .uuid(uuid)
                .ip(ip)
                .adminName(adminName)
                .reason(reason != null ? reason : "Not Specified")
                .expireDate(expire)
                .build();

        network.post(ban);
        banDataRepository.save(ban);

        return ModerationResult.success("Player '" + ban.name + "' banned until " + expire, ban);
    }

    /**
     * Temporary unban by UUID or IP.
     *
     * @param uuid Player UUID (can be null)
     * @param ip   Player IP (can be null)
     * @return Result indicating success or failure
     */
    public ModerationResult<Void> tempUnban(String uuid, String ip) {
        if (uuid == null && ip == null) {
            return ModerationResult.failure("Either UUID or IP must be provided");
        }

        banDataRepository.delete(uuid, ip);

        return ModerationResult.success("Unbanned: UUID=" + uuid + " / IP=" + ip, null);
    }

    /**
     * Parse period string using TimeService.
     *
     * @param periodStr Period string (e.g., "1d", "2h")
     * @param unit      Time unit
     * @return Parsed Instant or null if invalid
     */
    public Instant parsePeriod(String periodStr, TimeUnit unit) {
        return time.parsePeriod(periodStr, unit);
    }

    /**
     * Find player data by UUID or PID.
     *
     * @param uuidOrPid UUID or PID string (PID starts with #)
     * @return PlayerData or null if not found
     */
    public PlayerData findPlayerData(String uuidOrPid) {
        return find.playerData(uuidOrPid);
    }
}
