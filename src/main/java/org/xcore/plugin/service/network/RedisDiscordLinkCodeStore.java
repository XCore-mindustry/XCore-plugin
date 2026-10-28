package org.xcore.plugin.service.network;

import com.google.gson.Gson;
import io.lettuce.core.ScriptOutputType;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.TomlXcoreConfig;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

@Singleton
public class RedisDiscordLinkCodeStore {

    private static final long CODE_TTL_SECONDS = 10 * 60L;
    static final String RESERVE_SCRIPT = """
            if not redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[3]) then return 0 end
            local old = redis.call('GET', KEYS[2])
            if old and old ~= ARGV[2] then
              local key = 'xcore:discord-link:code:' .. old
              local value = redis.call('GET', key)
              if value then
                local p = cjson.decode(value)
                local n = cjson.decode(ARGV[1])
                if p.playerUuid == n.playerUuid and p.server == n.server then redis.call('DEL', key) end
              end
            end
            redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[3])
            return 1
            """;
    static final String CLAIM_SCRIPT = """
            local value = redis.call('GET', KEYS[1])
            if not value then return nil end
            local p = cjson.decode(value)
            local expected = cjson.decode(ARGV[1])
            if p.code ~= expected.code or p.playerNickname ~= expected.playerNickname
              or p.playerUuid ~= expected.playerUuid or p.playerPid ~= expected.playerPid
              or p.server ~= expected.server or p.createdAt ~= expected.createdAt
              or p.expiresAt ~= expected.expiresAt then return nil end
            redis.call('DEL', KEYS[1])
            local reverse = 'xcore:discord-link:player:' .. p.server .. ':' .. p.playerUuid
            if redis.call('GET', reverse) == ARGV[2] then redis.call('DEL', reverse) end
            return value
            """;
    static final String INVALIDATE_SCRIPT = """
            local code = redis.call('GET', KEYS[1])
            if not code then return 0 end
            local key = 'xcore:discord-link:code:' .. code
            local value = redis.call('GET', key)
            if value then
              local p = cjson.decode(value)
              if p.playerUuid == ARGV[1] and p.server == ARGV[2] then redis.call('DEL', key) end
            end
            redis.call('DEL', KEYS[1])
            return 1
            """;

    private final RedisNetworkBackend backend;
    private final Gson redisGson;
    private final TomlXcoreConfig config;

    @Inject
    public RedisDiscordLinkCodeStore(RedisNetworkBackend backend, @Named("redis") Gson redisGson, TomlXcoreConfig config) {
        this.backend = backend;
        this.redisGson = redisGson;
        this.config = config;
    }

    public boolean store(LinkCodePayload payload) {
        if (payload == null || payload.code() == null || payload.code().isBlank() || payload.playerUuid() == null || payload.playerUuid().isBlank()) {
            return false;
        }

        return storeAsync(payload).toCompletableFuture().join();
    }

    public CompletionStage<Boolean> storeAsync(LinkCodePayload payload) {
        if (payload == null || payload.code() == null || payload.code().isBlank() || payload.playerUuid() == null || payload.playerUuid().isBlank()) {
            return CompletableFuture.completedFuture(false);
        }

        if (payload.server() == null || !payload.server().equals(config.server.name)) {
            return CompletableFuture.completedFuture(false);
        }
        return backend.withAsyncCommands(commands -> commands.<Long>eval(RESERVE_SCRIPT, ScriptOutputType.INTEGER,
                        new String[]{codeKey(payload.code()), playerKey(payload.playerUuid())},
                        redisGson.toJson(payload), normalizeCode(payload.code()), Long.toString(CODE_TTL_SECONDS))
                .toCompletableFuture().orTimeout(500, TimeUnit.MILLISECONDS).thenApply(value -> value == 1L), false);
    }

    public LinkCodePayload findByCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }

        return findByCodeAsync(code).toCompletableFuture().join();
    }

    public CompletionStage<LinkCodePayload> findByCodeAsync(String code) {
        if (code == null || code.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        String codeKey = codeKey(code);
        return backend.withAsyncCommands(commands ->
                commands.get(codeKey)
                        .toCompletableFuture()
                        .orTimeout(500, TimeUnit.MILLISECONDS)
                        .thenApply(payloadJson -> {
                            if (payloadJson == null || payloadJson.isBlank()) {
                                return null;
                            }
                            return redisGson.fromJson(payloadJson, LinkCodePayload.class);
                        })
                        .exceptionally(err -> null),
                null
        );
    }

    public LinkCodePayload findPendingByPlayerUuid(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return null;
        }

        return findPendingByPlayerUuidAsync(playerUuid).toCompletableFuture().join();
    }

    public CompletionStage<LinkCodePayload> findPendingByPlayerUuidAsync(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        String playerKey = playerKey(playerUuid);
        return backend.withAsyncCommands(commands ->
                commands.get(playerKey)
                        .toCompletableFuture()
                        .orTimeout(500, TimeUnit.MILLISECONDS)
                        .thenCompose(code -> {
                            if (code == null || code.isBlank()) {
                                return CompletableFuture.completedFuture(null);
                            }
                            return commands.get(codeKey(code))
                                    .toCompletableFuture()
                                    .orTimeout(500, TimeUnit.MILLISECONDS)
                                    .thenCompose(payloadJson -> {
                                        if (payloadJson == null || payloadJson.isBlank()) {
                                            return CompletableFuture.completedFuture(null);
                                        }
                                        return CompletableFuture.completedFuture(redisGson.fromJson(payloadJson, LinkCodePayload.class));
                                    });
                        })
                        .exceptionally(err -> null),
                null
        );
    }

    public boolean invalidatePendingByPlayerUuid(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return false;
        }

        return invalidatePendingByPlayerUuidAsync(playerUuid).toCompletableFuture().join();
    }

    public CompletionStage<Boolean> invalidatePendingByPlayerUuidAsync(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }

        return backend.withAsyncCommands(commands -> commands.<Long>eval(INVALIDATE_SCRIPT, ScriptOutputType.INTEGER,
                new String[]{playerKey(playerUuid)}, playerUuid, config.server.name)
                .toCompletableFuture().orTimeout(500, TimeUnit.MILLISECONDS).thenApply(value -> value == 1L), false);
    }

    public LinkCodePayload claimCode(LinkCodePayload expected) {
        return claimCodeAsync(expected).toCompletableFuture().join();
    }

    public CompletionStage<LinkCodePayload> claimCodeAsync(LinkCodePayload expected) {
        if (expected == null) return CompletableFuture.completedFuture(null);
        return backend.withAsyncCommands(commands -> commands.<String>eval(CLAIM_SCRIPT, ScriptOutputType.VALUE,
                new String[]{codeKey(expected.code())}, redisGson.toJson(expected), normalizeCode(expected.code()))
                .toCompletableFuture().orTimeout(500, TimeUnit.MILLISECONDS)
                .thenApply(json -> json == null ? null : redisGson.fromJson(json, LinkCodePayload.class)), null);
    }

    public boolean consumeCode(String code) {
        return consumeCodeAsync(code).toCompletableFuture().join();
    }

    public CompletionStage<Boolean> consumeCodeAsync(String code) {
        return findByCodeAsync(code).thenCompose(payload -> claimCodeAsync(payload).thenApply(value -> value != null));
    }

    private String codeKey(String code) {
        return "xcore:discord-link:code:" + normalizeCode(code);
    }

    private String playerKey(String playerUuid) {
        return "xcore:discord-link:player:" + config.server.name + ":" + playerUuid;
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    public record LinkCodePayload(
            String code,
            String playerUuid,
            int playerPid,
            String playerNickname,
            String server,
            long createdAt,
            long expiresAt
    ) {}
}
