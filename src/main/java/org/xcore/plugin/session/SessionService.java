package org.xcore.plugin.session;

import arc.func.Boolf;
import arc.func.Cons;
import arc.struct.ObjectMap;
import org.xcore.plugin.common.PLog;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.GameThread;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.TopMenuCacheService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Singleton
public class SessionService {

    private final SessionFactory sessionFactory;
    private final PlayerDataRepository playerDataRepository;
    private final TopMenuCacheService topMenuCacheService;

    /**
     * In-memory cache of online players.
     * Key: player UUID, Value: player data
     */
    private final ObjectMap<String, Session> sessionCache = new ObjectMap<>();

    @Inject
    public SessionService(SessionFactory sessionFactory,
                          PlayerDataRepository playerDataRepository,
                          TopMenuCacheService topMenuCacheService) {
        this.sessionFactory = sessionFactory;
        this.playerDataRepository = playerDataRepository;
        this.topMenuCacheService = topMenuCacheService;
    }

    public SessionService(SessionFactory sessionFactory, PlayerDataRepository playerDataRepository) {
        this(sessionFactory, playerDataRepository, null);
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
     * Returns the session only when it is fully active (non-null session AND data).
     * Consolidates the ubiquitous {@code session == null || session.data == null} guard.
     */
    public Session getActive(String uuid) {
        Session session = sessionCache.get(uuid);
        return (session != null && session.data != null) ? session : null;
    }

    public Session getActive(Player player) {
        return player != null ? getActive(player.uuid()) : null;
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
     * Asynchronously gets player data by UUID with in-memory cache check.
     */
    public CompletionStage<PlayerData> getOrLoadFromDbAsync(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        var cached = sessionCache.get(uuid);
        if (cached != null && cached.data != null) {
            return CompletableFuture.completedFuture(cached.data);
        }
        return playerDataRepository.findByUuidAsync(uuid);
    }

    /**
     * Finds an active online session by player internal ID (pid).
     * Strictly in-memory; returns null if the player is not currently online.
     *
     * @param pid internal player ID
     * @return active Session, or null if not online
     */
    public Session findOnlineByPid(int pid) {
        if (pid < 0) return null;
        for (var session : getAllCachedSnapshot()) {
            if (session.data != null && session.data.pid == pid) {
                return session;
            }
        }
        return null;
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
        if (pid < 0) return null;
        Session online = findOnlineByPid(pid);
        if (online != null && online.data != null) {
            return online.data;
        }

        return playerDataRepository.findByPid(pid);
    }

    /**
     * Asynchronously gets player data by internal player ID with in-memory cache check.
     */
    public CompletionStage<PlayerData> getOrLoadFromDbAsync(int pid) {
        if (pid < 0) {
            return CompletableFuture.completedFuture(null);
        }
        Session online = findOnlineByPid(pid);
        if (online != null && online.data != null) {
            return CompletableFuture.completedFuture(online.data);
        }
        return playerDataRepository.findByPidAsync(pid);
    }

    /**
     * Registers player login - loads data from DB and caches it.
     * <p>
     * Should be called when player joins the server.
     *
     * @param player the player instance
     * @return loaded PlayerData (newly created if player is new)
     */
    /**
     * Registers a session around data that has already been loaded and settled.
     *
     * <p>There is deliberately no {@code registerLogin(Player)} convenience overload: it
     * would hide a Mongo read behind a call that looks game-thread safe, which is the
     * mistake this split exists to prevent. Callers load first, then register.
     *
     * <p>Game thread only, and deliberately the point at which the session becomes visible
     * to {@link #get(String)}: the caller has finished writing by then, so no other thread
     * can observe a half-populated {@link PlayerData}.
     */
    public Session registerLogin(Player player, PlayerData data) {
        Session session = createSession(player, data);

        sessionCache.put(player.uuid(), session);

        PLog.debug("Player session registered: @ (@)", session.data.nickname, player.uuid());
        return session;
    }

    /**
     * Reads the stored data for a uuid, or returns a fresh record for an unknown player.
     *
     * <p>Hits Mongo, so it belongs on the storage executor. The result is not published to
     * any other thread.
     */
    public PlayerData loadPlayerData(String uuid) {
        var data = playerDataRepository.findByUuid(uuid);
        return data != null ? data : new PlayerData(uuid, false);
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
            PLog.debug("Player session unregistered: @ (@)", data.data.nickname, player.uuid());
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
        if (data != null && data.data != null && data.data.uuid != null) {
            sessionCache.put(data.data.uuid, data);
        } else {
            PLog.warn("Attempted to update session with invalid data: @", data);
        }
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

        boolean persisted = playerDataRepository.save(session.data);
        if (persisted) {
            invalidateLeaderboardCache();
        }
        return persisted;
    }

    /**
     * Flags the cached player data as online and persists the flag.
     * <p>
     * Called when a player joins the server.
     *
     * @param data player data
     * @param serverName current server name
     */
    public void markOnline(PlayerData data, String serverName) {
        if (data == null || data.uuid == null || data.uuid.isBlank()) {
            return;
        }

        data.online = true;
        data.onlineSince = System.currentTimeMillis();
        data.onlineServer = serverName == null ? "" : serverName;

        playerDataRepository.updateOnlineAsync(data.uuid, true, serverName)
                .exceptionally(error -> {
                    PLog.warn("Failed to mark player @ online: @", data.uuid, error);
                    return false;
                });
    }

    /**
     * Flags the cached player data as offline and persists the flag.
     * <p>
     * Called when a player leaves the server.
     *
     * @param data player data
     */
    public void markOffline(PlayerData data) {
        if (data == null || data.uuid == null || data.uuid.isBlank()) {
            return;
        }

        data.online = false;
        data.onlineSince = 0L;
        data.onlineServer = "";

        playerDataRepository.updateOnlineAsync(data.uuid, false, null)
                .exceptionally(error -> {
                    PLog.warn("Failed to mark player @ offline: @", data.uuid, error);
                    return false;
                });
    }

    /**
     * Clears stale {@code online} flags left behind by a previous server process.
     * <p>
     * Must run before the first session is registered, otherwise players from a crashed
     * server would stay flagged as online forever.
     */
    public void clearStalePresenceFlags() {
        playerDataRepository.clearOnlineFlagsAsync()
                .thenAccept(cleared -> PLog.info("Cleared stale online flags: @", cleared))
                .exceptionally(error -> {
                    PLog.warn("Failed to clear stale online flags: @", error);
                    return null;
                });
    }

    /**
     * Reloads cache from currently online players.
     * <p>
     * Clears cache and rebuilds from Groups.player.
     * Useful for manual cache refresh.
     */
    /**
     * Rebuilds the session cache from the authoritative repository.
     *
     * <p>This replaces a reload that walked {@link mindustry.gen.Groups#player} from
     * the Redis subscriber thread while issuing one blocking Mongo query per player.
     * The list was mutated under iteration, and a reconnect during the reload left a
     * half-populated cache. The player snapshot is now taken on the game thread, the
     * queries run on the storage executor, and the rebuilt cache is installed back on
     * the game thread in a single swap.
     */
    public void reloadCacheAsync(Async async) {
        async.main(() -> {
            List<Player> online = new ArrayList<>();
            Groups.player.each(online::add);

            async.supply(() -> {
                    ObjectMap<String, PlayerData> reloaded = new ObjectMap<>();
                    for (Player player : online) {
                        PlayerData data = playerDataRepository.findByPlayer(player);
                        reloaded.put(player.uuid(), data != null ? data : new PlayerData(player.uuid(), false));
                    }
                    return reloaded;
                })
                .thenMain((reloaded, error) -> {
                    if (error != null) {
                        PLog.err("Failed to reload player data cache", error);
                        return;
                    }

                    // A player who disconnected while the queries ran must not be
                    // resurrected in the cache, and the cache is left untouched on
                    // failure so a bad reload cannot log everyone out.
                    ObjectMap<String, Session> rebuilt = new ObjectMap<>();
                    for (Player player : online) {
                        if (!Async.isPlayerOnline(player)) {
                            continue;
                        }
                        PlayerData data = reloaded.get(player.uuid());
                        if (data == null) {
                            continue;
                        }
                        rebuilt.put(player.uuid(), createSession(player, data));
                    }

                    sessionCache.clear();
                    sessionCache.putAll(rebuilt);
                    PLog.info("Player cache reloaded: @ players", sessionCache.size);
                });
        });
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
        for (var data : getAllCachedSnapshot()) {
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
     * Gets a snapshot of all cached player sessions.
     *
     * @return snapshot of all cached session entries
     */
    public Iterable<Session> getAllCached() {
        return getAllCachedSnapshot();
    }

    public List<Session> getAllCachedSnapshot() {
        var sessions = new ArrayList<Session>(sessionCache.size);
        for (var session : sessionCache.values()) {
            sessions.add(session);
        }
        return sessions;
    }

    public Stream<Session> streamCached() {
        return getAllCachedSnapshot().stream();
    }

    public List<Session> findByTeam(Team team) {
        if (team == null) {
            return List.of();
        }

        return streamCached()
                .filter(this::hasOnlinePlayer)
                .filter(session -> session.player.team() == team)
                .toList();
    }

    public void forEachOnline(Consumer<Session> consumer) {
        Objects.requireNonNull(consumer, "consumer");

        streamCached()
                .filter(this::hasOnlinePlayer)
                .forEach(consumer);
    }

    public boolean incrementPlayTime(Session session, int delta) {
        boolean updated = mutateSession(session,
                data -> data.totalPlayTime += delta,
                () -> playerDataRepository.incrementPlayTime(session.data.uuid, delta));
        if (updated) {
            invalidateLeaderboardCache();
        }
        return updated;
    }

    public CompletionStage<Boolean> incrementPlayTimeAsync(Session session, int delta) {
        if (!hasData(session)) {
            return CompletableFuture.completedFuture(false);
        }

        // Optimistic update is applied inside the async callback only on confirmed success,
        // so a racing callback or DB failure can never double-increment the counter.
        var stage = playerDataRepository.incrementPlayTimeAsync(session.data.uuid, delta);
        if (stage != null) {
            return stage.thenApply(updated -> {
                if (Boolean.TRUE.equals(updated)) {
                    org.xcore.plugin.concurrent.MainThreadDispatcher.mindustry().execute(() -> {
                        session.data.totalPlayTime += delta;
                        invalidateLeaderboardCache();
                    });
                }
                return updated;
            });
        }
        boolean updated = playerDataRepository.incrementPlayTime(session.data.uuid, delta);
        if (updated) {
            session.data.totalPlayTime += delta;
            invalidateLeaderboardCache();
        }
        return CompletableFuture.completedFuture(updated);
    }

    public boolean updateIp(Session session, String ip) {
        return mutateSession(session,
                data -> data.ip = ip,
                () -> playerDataRepository.updateIp(session.data.uuid, ip));
    }

    public boolean updateConnectionData(Session session, String ip, String nickname) {
        return mutateSession(session, data -> {
            data.ip = ip;
            data.nickname = nickname;
        }, () -> playerDataRepository.updateConnectionData(session.data.uuid, ip, nickname));
    }

    /**
     * Applies the join-time ip/nickname to data that is not published yet, then persists.
     *
     * <p>Storage phase only. Because the {@code PlayerData} is still private to the caller,
     * mutating it here cannot race a gameplay write; a session-scoped caller must go
     * through {@link #updateConnectionData(Session, String, String)} instead, because there
     * the cached record is shared with the game thread.
     */
    public boolean updateConnectionData(PlayerData data, String ip, String nickname) {
        if (data == null) {
            return false;
        }

        data.ip = ip;
        data.nickname = nickname;
        return playerDataRepository.updateConnectionData(data.uuid, ip, nickname);
    }

    /** Persists data that is not published yet. Storage phase only. */
    public boolean persistData(PlayerData data) {
        if (data == null) {
            return false;
        }

        boolean persisted = playerDataRepository.save(data);
        if (persisted) {
            invalidateLeaderboardCache();
        }
        return persisted;
    }

    public boolean updateAdminStatus(Session session, boolean admin, String adminSource) {
        return mutateSession(session, data -> {
            data.admin = admin;
            data.adminSource = adminSource == null || adminSource.isBlank() ? "NONE" : adminSource;
        }, () -> playerDataRepository.updateAdminStatus(session.data.uuid, admin, session.data.adminSource));
    }

    public boolean updateLeaderboard(Session session, boolean leaderboard) {
        return mutateSession(session,
                data -> data.leaderboard = leaderboard,
                () -> playerDataRepository.updateLeaderboard(session.data.uuid, leaderboard));
    }

    public boolean updateGlobalChatVisible(Session session, boolean visible) {
        return mutateSession(session,
                data -> data.globalChatVisible = visible,
                () -> playerDataRepository.updateGlobalChatVisible(session.data.uuid, visible));
    }

    public boolean updateDiscordRelayVisible(Session session, boolean visible) {
        return mutateSession(session,
                data -> data.discordRelayVisible = visible,
                () -> playerDataRepository.updateDiscordRelayVisible(session.data.uuid, visible));
    }

    public boolean updateCustomNickname(Session session, String customNickname) {
        return mutateSession(session,
                data -> data.customNickname = customNickname,
                () -> playerDataRepository.updateCustomNickname(session.data.uuid, customNickname));
    }

    public boolean updateDescription(Session session, String description) {
        return mutateSession(session,
                data -> data.description = description,
                () -> playerDataRepository.updateDescription(session.data.uuid, description));
    }

    public boolean setActiveBadge(Session session, String badgeId) {
        return mutateSession(session,
                data -> data.activeBadge = badgeId,
                () -> playerDataRepository.setActiveBadge(session.data.uuid, badgeId));
    }

    public boolean updateBadgeSymbolColorMode(Session session, String mode) {
        return mutateSession(session,
                data -> data.badgeSymbolColorMode = mode,
                () -> playerDataRepository.updateBadgeSymbolColorMode(session.data.uuid, mode));
    }

    public boolean addBlockedPrivateUuid(Session session, String blockedUuid) {
        return mutateSession(session,
                data -> data.blockedPrivateUuids.add(blockedUuid),
                () -> playerDataRepository.addBlockedPrivateUuid(session.data.uuid, blockedUuid));
    }

    public boolean removeBlockedPrivateUuid(Session session, String blockedUuid) {
        return mutateSession(session,
                data -> data.blockedPrivateUuids.remove(blockedUuid),
                () -> playerDataRepository.removeBlockedPrivateUuid(session.data.uuid, blockedUuid));
    }

    public boolean putMapVote(Session session, String mapId, boolean like) {
        return mutateSession(session,
                data -> data.mapVotes.put(mapId, like),
                () -> playerDataRepository.putMapVote(session.data.uuid, mapId, like));
    }

    public boolean putEventVote(Session session, String eventId, boolean like) {
        return mutateSession(session,
                data -> data.eventVotes.put(eventId, like),
                () -> playerDataRepository.putEventVote(session.data.uuid, eventId, like));
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

    private boolean mutateSession(Session session, Consumer<PlayerData> mutation, BooleanSupplier persistence) {
        if (!hasData(session)) {
            return false;
        }

        mutation.accept(session.data);
        return persistence.getAsBoolean();
    }

    public void broadcast(String key, Map<String, Object> args) {
        for (Session session : getAllCachedSnapshot()) {
            if (session.data == null) continue;
            session.locale().send(key, args);
        }
    }

    public void broadcastFiltered(String key, Map<String, Object> args, Predicate<Session> filter) {
        // Every chat relay funnels through here, so this is the cheapest place to notice a
        // caller that reached the game thread over a listener, an executor or a UDP reader.
        GameThread.report("SessionService.broadcastFiltered:" + key);
        for (Session session : getAllCachedSnapshot()) {
            if (session.data == null) continue;
            if (filter != null && !filter.test(session)) continue;
            session.locale().send(key, args);
        }
    }

    public void broadcastToTeam(Team team, String key, Map<String, Object> args) {
        if (team == null) {
            return;
        }

        findByTeam(team).forEach(session -> session.locale().send(key, args));
    }

    private boolean hasOnlinePlayer(Session session) {
        return hasData(session) && session.player != null;
    }

    private void invalidateLeaderboardCache() {
        if (topMenuCacheService != null) {
            topMenuCacheService.invalidateAllAsync();
        }
    }
}
