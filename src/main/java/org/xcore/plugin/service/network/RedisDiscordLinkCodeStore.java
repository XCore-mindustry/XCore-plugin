package org.xcore.plugin.service.network;

import com.google.gson.Gson;
import io.lettuce.core.SetArgs;
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

        String code = normalizeCode(payload.code());
        String playerKey = playerKey(payload.playerUuid());
        String codeKey = codeKey(code);
        String json = redisGson.toJson(payload);

        return backend.withAsyncCommands(commands ->
                deleteKeysAsync(commands, payload.playerUuid())
                        .thenCompose(ignored -> commands.set(codeKey, json, SetArgs.Builder.ex(CODE_TTL_SECONDS)).toCompletableFuture())
                        .thenCompose(ignored -> commands.set(playerKey, code, SetArgs.Builder.ex(CODE_TTL_SECONDS)).toCompletableFuture())
                        .thenApply("OK"::equals)
                        .toCompletableFuture()
                        .orTimeout(500, TimeUnit.MILLISECONDS)
                        .exceptionally(err -> false),
                false
        );
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
                                            return commands.del(playerKey).toCompletableFuture().thenApply(ignored -> null);
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

        return backend.withAsyncCommands(commands ->
                deleteKeysAsync(commands, playerUuid)
                        .thenApply(ignored -> true)
                        .toCompletableFuture()
                        .orTimeout(500, TimeUnit.MILLISECONDS)
                        .exceptionally(err -> false),
                false
        );
    }

    public boolean consumeCode(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }

        return consumeCodeAsync(code).toCompletableFuture().join();
    }

    public CompletionStage<Boolean> consumeCodeAsync(String code) {
        if (code == null || code.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }

        String codeKey = codeKey(code);
        return backend.withAsyncCommands(commands ->
                commands.get(codeKey)
                        .toCompletableFuture()
                        .orTimeout(500, TimeUnit.MILLISECONDS)
                        .thenCompose(payloadJson -> {
                            if (payloadJson == null || payloadJson.isBlank()) {
                                return CompletableFuture.completedFuture(false);
                            }
                            LinkCodePayload payload = redisGson.fromJson(payloadJson, LinkCodePayload.class);
                            var delCodeFuture = commands.del(codeKey).toCompletableFuture();
                            if (payload != null && payload.playerUuid() != null && !payload.playerUuid().isBlank()) {
                                return delCodeFuture.thenCompose(ignored ->
                                        commands.del(playerKey(payload.playerUuid())).toCompletableFuture().thenApply(res -> true)
                                );
                            }
                            return delCodeFuture.thenApply(res -> true);
                        })
                        .exceptionally(err -> false),
                false
        );
    }

    private CompletionStage<Void> deleteKeysAsync(io.lettuce.core.api.async.RedisAsyncCommands<String, String> commands, String playerUuid) {
        String playerKey = playerKey(playerUuid);
        return commands.get(playerKey)
                .toCompletableFuture()
                .thenCompose(existingCode -> {
                    if (existingCode != null && !existingCode.isBlank()) {
                        return commands.del(codeKey(existingCode))
                                .toCompletableFuture()
                                .thenCompose(ignored -> commands.del(playerKey).toCompletableFuture())
                                .thenApply(ignored -> null);
                    }
                    return commands.del(playerKey).toCompletableFuture().thenApply(ignored -> null);
                });
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
