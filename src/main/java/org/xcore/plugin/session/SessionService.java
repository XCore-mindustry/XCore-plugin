package org.xcore.plugin.session;

import arc.func.Boolf;
import arc.func.Cons;
import arc.struct.ObjectMap;
import arc.util.Log;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.localization.BundleService;
import org.xcore.plugin.ui.MenuService;

import java.util.Map;

@Singleton
public class SessionService {

    private final BundleService bundle;
    private final GlobalConfig globalConfig;
    private final Provider<MenuService> menuService;
    private final PlayerDataRepository playerDataRepository;

    /**
     * In-memory cache of online players.
     * Key: player UUID, Value: player data
     */
    private final ObjectMap<String, Session> sessionCache = new ObjectMap<>();

    @Inject
    public SessionService(BundleService bundle, GlobalConfig globalConfig, Provider<MenuService> menuService, PlayerDataRepository playerDataRepository) {
        this.bundle = bundle;
        this.globalConfig = globalConfig;
        this.menuService = menuService;
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
        var data = playerDataRepository.findByPlayer(player);

        if (data == null) data = new PlayerData(player.uuid(), false);

        Session session = new Session(globalConfig, bundle, menuService.get(), playerDataRepository, player, data);

        sessionCache.put(player.uuid(), session);

        Log.debug("Player session registered: @ (@)", data.nickname, player.uuid());
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
            playerDataRepository.save(data.data);
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

    /**
     * Saves all cached player data to database.
     * <p>
     * Useful for periodic persistence or server shutdown.
     */
    public void saveAll() {
        Log.info("Saving @ online players to database", sessionCache.size);

        for (var session : sessionCache.values()) {
            session.save();
        }
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
            var data = playerDataRepository.findByPlayer(player);
            if (data == null) data = new PlayerData(player.uuid(), false);

            Session session = new Session(globalConfig, bundle, menuService.get(), playerDataRepository, player, data);
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

    public void broadcast(String key, Map<String, Object> args) {
        for (Session session : getAllCached()) {
            session.locale().send(key, args);
        }
    }
}
