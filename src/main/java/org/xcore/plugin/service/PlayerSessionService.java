package org.xcore.plugin.service;

import arc.func.Boolf;
import arc.func.Cons;
import arc.struct.ObjectMap;
import arc.util.Log;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;

@Singleton
public class PlayerSessionService {

    private final PlayerDataRepository playerDataRepository;

    /**
     * In-memory cache of online players.
     * Key: player UUID, Value: player data
     */
    private final ObjectMap<String, PlayerData> sessionCache = new ObjectMap<>();

    @Inject
    public PlayerSessionService(PlayerDataRepository playerDataRepository) {
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
    public PlayerData get(Player player) {
        return sessionCache.get(player.uuid());
    }

    /**
     * Gets cached player data by UUID.
     *
     * @param uuid player UUID
     * @return cached PlayerData or null if not in cache
     */
    public PlayerData get(String uuid) {
        return sessionCache.get(uuid);
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
            return cached;
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
            if (data.pid == pid) {
                return data;
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
    public PlayerData registerLogin(Player player) {
        var data = playerDataRepository.findByPlayer(player);
        sessionCache.put(player.uuid(), data);

        Log.debug("Player session registered: @ (@)", data.nickname, player.uuid());
        return data;
    }

    /**
     * Registers player logout - removes from cache and saves to DB.
     * <p>
     * Should be called when player leaves the server.
     *
     * @param uuid player UUID
     * @return removed PlayerData or null if not in cache
     */
    public PlayerData registerLogout(String uuid) {
        var data = sessionCache.remove(uuid);

        if (data != null) {
            playerDataRepository.save(data);
            Log.debug("Player session unregistered: @ (@)", data.nickname, uuid);
        }

        return data;
    }

    /**
     * Updates cached player data.
     * <p>
     * If player is not in cache, adds them.
     *
     * @param data player data to cache
     */
    public void update(PlayerData data) {
        sessionCache.put(data.uuid, data);
    }

    /**
     * Saves all cached player data to database.
     * <p>
     * Useful for periodic persistence or server shutdown.
     */
    public void saveAll() {
        Log.info("Saving @ online players to database", sessionCache.size);

        for (var data : sessionCache.values()) {
            playerDataRepository.save(data);
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
            sessionCache.put(player.uuid(), data);
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
    public void getCachedAdminTools(Boolf<Integer> versionCompare, Cons<PlayerData> consumer) {
        for (var data : sessionCache.values()) {
            if (data.adminModVersion != null && versionCompare.get(Integer.parseInt(data.adminModVersion))) {
                consumer.get(data);
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
    public Iterable<PlayerData> getAllCached() {
        return sessionCache.values();
    }
}
