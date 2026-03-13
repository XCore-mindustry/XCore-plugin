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

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static mindustry.Vars.netServer;

/**
 * Centralized moderation service handling ban, unban, mute, unmute operations.
 * Eliminates code duplication across client, server, and Discord controllers.
 */
@Singleton
public class ModerationService {
    private static final String DEFAULT_REASON = "Not Specified";
    private static final String UNKNOWN_PLAYER_NAME = "Unknown";
    private static final String EMPTY_DISCORD_ID = "";
    private static final String PLAYER_NOT_FOUND_MESSAGE = "Player not found";
    private static final String MISSING_IDENTIFIER_MESSAGE = "Either UUID or IP must be provided";
    private static final String BAN_SAVE_FAILED_MESSAGE = "Failed to save ban";
    private static final String BAN_DELETE_FAILED_MESSAGE = "Failed to delete ban";
    private static final String MUTE_SAVE_FAILED_MESSAGE = "Failed to save mute";
    private static final String MUTE_DELETE_FAILED_MESSAGE = "Failed to delete mute";

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
    public ModerationResult<BanData> banById(int id, String adminName, String adminDiscordId, String reason, Duration duration, boolean kickOnline) {
        var target = playerDataRepository.findByPid(id);
        if (target == null) {
            return ModerationResult.failure(PLAYER_NOT_FOUND_MESSAGE);
        }

        Instant unbanDate = toExpireDate(duration);
        var info = netServer.admins.getInfoOptional(target.uuid);
        String ip = (info != null) ? info.lastIP : null;

        BanData ban = BanData.builder()
                .name(target.nickname)
                .uuid(target.uuid)
                .ip(ip)
                .adminName(adminName)
                .adminDiscordId(resolveAdminDiscordId(adminDiscordId))
                .reason(resolveReason(reason))
                .expireDate(unbanDate)
                .build();

        if (!banDataRepository.save(ban)) {
            return ModerationResult.failure(BAN_SAVE_FAILED_MESSAGE);
        }

        network.post(ban);

        if (kickOnline) {
            network.post(new SocketEvents.KickBannedPlayer(target.uuid, ip));
        }

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
            return ModerationResult.failure(PLAYER_NOT_FOUND_MESSAGE);
        }

        if (!banDataRepository.delete(target.uuid, null)) {
            return ModerationResult.failure(BAN_DELETE_FAILED_MESSAGE);
        }

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
    public ModerationResult<MuteData> muteById(int id, String adminName, String adminDiscordId, String reason, Duration duration) {
        var target = sessionService.getOrLoadFromDb(id);
        if (target == null) {
            return ModerationResult.failure(PLAYER_NOT_FOUND_MESSAGE);
        }

        Instant expireDate = toExpireDate(duration);

        MuteData mute = MuteData.builder()
                .uuid(target.uuid)
                .name(target.nickname)
                .adminName(adminName)
                .adminDiscordId(resolveAdminDiscordId(adminDiscordId))
                .reason(resolveReason(reason))
                .expireDate(expireDate)
                .build();

        if (!muteDataRepository.save(mute)) {
            return ModerationResult.failure(MUTE_SAVE_FAILED_MESSAGE);
        }

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
            return ModerationResult.failure(PLAYER_NOT_FOUND_MESSAGE);
        }

        if (!muteDataRepository.delete(target.uuid)) {
            return ModerationResult.failure(MUTE_DELETE_FAILED_MESSAGE);
        }

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
    public ModerationResult<BanData> tempBanByUuidOrIp(String uuid, String ip, String name, Duration duration, String reason, String adminName, String adminDiscordId) {
        if (hasNoIdentifier(uuid, ip)) {
            return ModerationResult.failure(MISSING_IDENTIFIER_MESSAGE);
        }

        Instant expire = toExpireDate(duration);

        BanData ban = BanData.builder()
                .name(resolvePlayerName(name))
                .uuid(uuid)
                .ip(ip)
                .adminName(adminName)
                .adminDiscordId(resolveAdminDiscordId(adminDiscordId))
                .reason(resolveReason(reason))
                .expireDate(expire)
                .build();

        if (!banDataRepository.save(ban)) {
            return ModerationResult.failure(BAN_SAVE_FAILED_MESSAGE);
        }

        network.post(ban);
        network.post(new SocketEvents.KickBannedPlayer(uuid, ip));

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
        if (hasNoIdentifier(uuid, ip)) {
            return ModerationResult.failure(MISSING_IDENTIFIER_MESSAGE);
        }

        if (!banDataRepository.delete(uuid, ip)) {
            return ModerationResult.failure(BAN_DELETE_FAILED_MESSAGE);
        }

        return ModerationResult.success("Unbanned: UUID=" + uuid + " / IP=" + ip, null);
    }

    /**
     * Parse period string using TimeService.
     *
     * @param periodStr Period string (e.g., "1d", "2h")
     * @param unit      Time unit
     * @return Parsed Duration or null if invalid
     */
    public Duration parsePeriod(String periodStr, TimeUnit unit) {
        Instant parsed = time.parsePeriod(periodStr, unit);
        if (parsed == null) {
            return null;
        }
        return Duration.ofMillis(parsed.toEpochMilli());
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

    private static String resolveReason(String reason) {
        return reason != null ? reason : DEFAULT_REASON;
    }

    private static String resolvePlayerName(String name) {
        return name != null ? name : UNKNOWN_PLAYER_NAME;
    }

    private static String resolveAdminDiscordId(String adminDiscordId) {
        return adminDiscordId != null ? adminDiscordId : EMPTY_DISCORD_ID;
    }

    private static boolean hasNoIdentifier(String uuid, String ip) {
        return uuid == null && ip == null;
    }

    private static Instant toExpireDate(Duration duration) {
        return Instant.now().plus(duration);
    }
}
