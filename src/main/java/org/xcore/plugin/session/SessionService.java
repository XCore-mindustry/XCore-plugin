package org.xcore.plugin.session;

import arc.func.Boolf;
import arc.func.Cons;
import arc.struct.ObjectMap;
import arc.util.Log;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Singleton;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;

import java.util.Map;

@Singleton
public class SessionService {

    private final SessionFactory sessionFactory;
    private final PlayerDataRepository playerDataRepository;

    /**
     * In-memory cache of online players.
     * Key: player UUID, Value: player data
     */
    private final ObjectMap<String, Session> sessionCache = new ObjectMap<>();

    public SessionService(SessionFactory sessionFactory, PlayerDataRepository playerDataRepository) {
        this.sessionFactory = sessionFactory;
        this.playerDataRepository = playerDataRepository;
    }

    @PostConstruct
    void init() {
        Log.info("PlayerSessionService initialized");
    }

    /**
     * Gets cached player data by player instance.
     *
     * @param player the player instance
     * @return cached PlayerData or null if not in cache
     */
    public Session get(Player player) {
        return sessionCache.get(player.uuid());
    }

    /**
     * Gets cached player data by UUID.
     *
     * @param uuid player UUID
     * @return cached PlayerData or null if not in cache
     */
    public Session get(String uuid) {
        return sessionCache.get(uuid);
    }

    public Session get(PlayerData data) {
        return sessionCache.get(data.uuid);
    }

    /**
     * Gets cached player data by UUID, with database fallback.
     * <p>
     * If player is not in cache, attempts to load from database.
     *
     * @param uuid player UUID
     * @return PlayerData from cache or database, or null if not found
     */
    public PlayerData getOrLoadFromDb(String uuid) {
        var cached = sessionCache.get(uuid);
        if (cached != null) {
            return cached.data;
        }
        return playerDataRepository.findByUuid(uuid);
    }

    /**
     * Gets cached player data by internal player ID.
     * <p>
     * Note: This performs a linear search through the cache.
     * Use sparingly for performance reasons.
     *
     * @param pid internal player ID
     * @return PlayerData from cache or database, or null if not found
     */
    public PlayerData getOrLoadFromDb(int pid) {
        for (var data : sessionCache.values()) {
            if (data.data.pid == pid) {
                return data.data;
            }
        }

        return playerDataRepository.findByPid(pid);
    }

    /**
     * Registers player login - loads data from DB and caches it.
     * <p>
     * Should be called when player joins the server.
     *
     * @param player the player instance
     * @return loaded PlayerData (newly created if player is new)
     */
    public Session registerLogin(Player player) {
        Session session = createSession(player, loadOrCreatePlayerData(player));

        sessionCache.put(player.uuid(), session);

        Log.debug("Player session registered: @ (@)", session.data.nickname, player.uuid());
        return session;
    }

    /**
     * Registers player logout - removes from cache and saves to DB.
     * <p>
     * Should be called when player leaves the server.
     *
     * @param player player
     * @return removed PlayerData or null if not in cache
     */
    public Session registerLogout(Player player) {
        var data = sessionCache.remove(player.uuid());

        if (data != null) {
            Log.debug("Player session unregistered: @ (@)", data.data.nickname, player.uuid());
        }

        return data;
    }

    /**
     * Updates cached player data.
     * <p>
     * If player is not in cache, adds them.
     *
     * @param data session data to cache
     */
    public void update(Session data) {
        sessionCache.put(data.data.uuid, data);
    }

    /**
     * Updates cached player data.
     * <p>
     * If player is not in cache, adds them.
     *
     * @param data player data to cache
     */
    public boolean update(PlayerData data) {
        var session = sessionCache.get(data.uuid);
        if (session == null) return false;
        session.data = data;
        return true;
    }

    public boolean persistPlayer(Session session) {
        if (!hasData(session)) {
            return false;
        }

        playerDataRepository.save(session.data);
        return true;
    }

    /**
     * Reloads cache from currently online players.
     * <p>
     * Clears cache and rebuilds from Groups.player.
     * Useful for manual cache refresh.
     */
    public void reloadCache() {
        sessionCache.clear();

        Groups.player.each(player -> {
            Session session = createSession(player, loadOrCreatePlayerData(player));
            sessionCache.put(player.uuid(), session);
        });

        Log.info("Player cache reloaded: @ players", sessionCache.size);
    }

    /**
     * Gets all cached players matching admin mod version comparison.
     * <p>
     * Used for admin tools version checking.
     *
     * @param versionCompare version comparison predicate (returns true if match)
     * @param consumer consumer for matched players
     */
    public void getCachedAdminTools(Boolf<String> versionCompare, Cons<PlayerData> consumer) {
        for (var data : sessionCache.values()) {
            if (data.data.adminModVersion != null && versionCompare.get(data.data.adminModVersion)) {
                consumer.get(data.data);
            }
        }
    }

    /**
     * Gets the number of currently cached (online) players.
     *
     * @return count of cached players
     */
    public int getCachedCount() {
        return sessionCache.size;
    }

    /**
     * Gets all cached player data.
     * <p>
     * Use with caution - returns live collection.
     *
     * @return all cached PlayerData entries
     */
    public Iterable<Session> getAllCached() {
        return sessionCache.values();
    }

    public boolean incrementPlayTime(Session session, int delta) {
        if (!hasData(session)) return false;
        session.data.totalPlayTime += delta;
        return playerDataRepository.incrementPlayTime(session.data.uuid, delta);
    }

    public boolean updateIp(Session session, String ip) {
        if (!hasData(session)) return false;
        session.data.ip = ip;
        return playerDataRepository.updateIp(session.data.uuid, ip);
    }

    public boolean updateConnectionData(Session session, String ip, String nickname) {
        if (!hasData(session)) return false;
        session.data.ip = ip;
        session.data.nickname = nickname;
        return playerDataRepository.updateConnectionData(session.data.uuid, ip, nickname);
    }

    public boolean updateAdminStatus(Session session, boolean admin, String adminSource) {
        if (!hasData(session)) return false;
        session.data.admin = admin;
        session.data.adminSource = adminSource == null || adminSource.isBlank() ? "NONE" : adminSource;
        return playerDataRepository.updateAdminStatus(session.data.uuid, admin, session.data.adminSource);
    }

    public boolean updateLeaderboard(Session session, boolean leaderboard) {
        if (!hasData(session)) return false;
        session.data.leaderboard = leaderboard;
        return playerDataRepository.updateLeaderboard(session.data.uuid, leaderboard);
    }

    public boolean updateCustomNickname(Session session, String customNickname) {
        if (!hasData(session)) return false;
        session.data.customNickname = customNickname;
        return playerDataRepository.updateCustomNickname(session.data.uuid, customNickname);
    }

    public boolean updateDescription(Session session, String description) {
        if (!hasData(session)) return false;
        session.data.description = description;
        return playerDataRepository.updateDescription(session.data.uuid, description);
    }

    public boolean setActiveBadge(Session session, String badgeId) {
        if (!hasData(session)) return false;
        session.data.activeBadge = badgeId;
        return playerDataRepository.setActiveBadge(session.data.uuid, badgeId);
    }

    public boolean addBlockedPrivateUuid(Session session, String blockedUuid) {
        if (!hasData(session)) return false;
        session.data.blockedPrivateUuids.add(blockedUuid);
        return playerDataRepository.addBlockedPrivateUuid(session.data.uuid, blockedUuid);
    }

    public boolean removeBlockedPrivateUuid(Session session, String blockedUuid) {
        if (!hasData(session)) return false;
        session.data.blockedPrivateUuids.remove(blockedUuid);
        return playerDataRepository.removeBlockedPrivateUuid(session.data.uuid, blockedUuid);
    }

    public boolean putMapVote(Session session, String mapId, boolean like) {
        if (!hasData(session)) return false;
        session.data.mapVotes.put(mapId, like);
        return playerDataRepository.putMapVote(session.data.uuid, mapId, like);
    }

    public boolean putEventVote(Session session, String eventId, boolean like) {
        if (!hasData(session)) return false;
        session.data.eventVotes.put(eventId, like);
        return playerDataRepository.putEventVote(session.data.uuid, eventId, like);
    }

    private PlayerData loadOrCreatePlayerData(Player player) {
        var data = playerDataRepository.findByPlayer(player);
        return data != null ? data : new PlayerData(player.uuid(), false);
    }

    private Session createSession(Player player, PlayerData data) {
        return sessionFactory.create(player, data);
    }

    private boolean hasData(Session session) {
        return session != null && session.data != null;
    }

    public void broadcast(String key, Map<String, Object> args) {
        for (Session session : getAllCached()) {
            if (session == null || session.data == null) continue;
            session.locale().send(key, args);
        }
    }
}
