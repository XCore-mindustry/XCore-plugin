package org.xcore.plugin.service.network;

import com.google.gson.Gson;
import io.lettuce.core.SetArgs;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.TomlXcoreConfig;

import java.util.Locale;

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

        return backend.withCommands(commands -> {
            deleteKeys(commands, payload.playerUuid());
            String code = normalizeCode(payload.code());
            commands.set(codeKey(code), redisGson.toJson(payload), SetArgs.Builder.ex(CODE_TTL_SECONDS));
            commands.set(playerKey(payload.playerUuid()), code, SetArgs.Builder.ex(CODE_TTL_SECONDS));
            return true;
        }, false);
    }

    public LinkCodePayload findByCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }

        return backend.withCommands(commands -> {
            String payloadJson = commands.get(codeKey(code));
            if (payloadJson == null || payloadJson.isBlank()) {
                return null;
            }
            return redisGson.fromJson(payloadJson, LinkCodePayload.class);
        }, null);
    }

    public LinkCodePayload findPendingByPlayerUuid(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return null;
        }

        return backend.withCommands(commands -> {
            String code = commands.get(playerKey(playerUuid));
            if (code == null || code.isBlank()) {
                return null;
            }
            String payloadJson = commands.get(codeKey(code));
            if (payloadJson == null || payloadJson.isBlank()) {
                commands.del(playerKey(playerUuid));
                return null;
            }
            return redisGson.fromJson(payloadJson, LinkCodePayload.class);
        }, null);
    }

    public boolean invalidatePendingByPlayerUuid(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return false;
        }

        return backend.withCommands(commands -> {
            deleteKeys(commands, playerUuid);
            return true;
        }, false);
    }

    public boolean consumeCode(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }

        return backend.withCommands(commands -> {
            String payloadJson = commands.get(codeKey(code));
            if (payloadJson == null || payloadJson.isBlank()) {
                return false;
            }
            LinkCodePayload payload = redisGson.fromJson(payloadJson, LinkCodePayload.class);
            commands.del(codeKey(code));
            if (payload != null && payload.playerUuid() != null && !payload.playerUuid().isBlank()) {
                commands.del(playerKey(payload.playerUuid()));
            }
            return true;
        }, false);
    }

    private void deleteKeys(io.lettuce.core.api.sync.RedisCommands<String, String> commands, String playerUuid) {
        String playerKey = playerKey(playerUuid);
        String existingCode = commands.get(playerKey);
        if (existingCode != null && !existingCode.isBlank()) {
            commands.del(codeKey(existingCode));
        }
        commands.del(playerKey);
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
