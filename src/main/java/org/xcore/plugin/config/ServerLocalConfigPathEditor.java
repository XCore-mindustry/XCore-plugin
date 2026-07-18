package org.xcore.plugin.config;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Updates server-local config values using either legacy flat field names or
 * TOML-oriented dotted paths.
 */
public final class ServerLocalConfigPathEditor {
    private static final Map<String, PathBinding> PATH_BINDINGS = createPathBindings();

    private final Gson prettyGson;

    public ServerLocalConfigPathEditor(Gson prettyGson) {
        this.prettyGson = Objects.requireNonNull(prettyGson, "prettyGson must not be null");
    }

    public TomlXcoreConfig update(TomlXcoreConfig config, String fieldPath, String value) {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(fieldPath, "fieldPath must not be null");
        Objects.requireNonNull(value, "value must not be null");

        PathBinding binding = PATH_BINDINGS.get(fieldPath.trim());
        if (binding == null) {
            return null;
        }

        TomlXcoreConfig workingCopy = prettyGson.fromJson(prettyGson.toJson(config), TomlXcoreConfig.class);
        workingCopy.normalize();

        JsonObject root = JsonParser.parseString(prettyGson.toJson(workingCopy)).getAsJsonObject();
        PathLocation target = resolvePath(root, binding.canonicalPath());
        if (target == null) {
            return null;
        }

        binding.valueType().apply(target.parent(), target.key(), value, prettyGson);

        TomlXcoreConfig updatedToml = prettyGson.fromJson(root, TomlXcoreConfig.class);
        updatedToml.normalize();
        return updatedToml;
    }

    public static IllegalArgumentException invalidValue(String fieldPath, String expected, String value, Throwable cause) {
        return new IllegalArgumentException(
                "Invalid value '" + value + "' for '" + fieldPath + "' (expected " + expected + ").",
                cause
        );
    }

    public static IllegalArgumentException invalidValue(String fieldPath, String expected, String value) {
        return new IllegalArgumentException(
                "Invalid value '" + value + "' for '" + fieldPath + "' (expected " + expected + ")."
        );
    }

    private static PathLocation resolvePath(JsonObject root, String path) {
        JsonObject current = root;
        String[] segments = path.split("\\.");
        for (int i = 0; i < segments.length - 1; i++) {
            String segment = segments[i];
            if (!current.has(segment) || !current.get(segment).isJsonObject()) {
                return null;
            }
            current = current.getAsJsonObject(segment);
        }

        String key = segments[segments.length - 1];
        if (!current.has(key)) {
            return null;
        }
        return new PathLocation(current, key);
    }

    private static Map<String, PathBinding> createPathBindings() {
        Map<String, PathBinding> bindings = new LinkedHashMap<>();

        bind(bindings, "server.name", ValueType.STRING, "server");
        bind(bindings, "server.public_host_override", ValueType.STRING, "public_host_override", "publicHostOverride");
        bind(bindings, "server.player_limit", ValueType.INT, "player_limit", "playerLimit");
        bind(bindings, "server.game_started_timer", ValueType.BOOLEAN, "game_started_timer", "gameStartedTimer");

        bind(bindings, "paths.global_config_directory", ValueType.STRING, "global_config_directory", "globalConfigDirectory");
        bind(bindings, "discord.channel_id", ValueType.DISCORD_SNOWFLAKE, "discord_channel_id", "discordChannelId");

        bind(bindings, "transport.redis.url", ValueType.STRING, "redis_url", "redisUrl");
        bind(bindings, "transport.redis.group_prefix", ValueType.STRING, "redis_group_prefix", "redisGroupPrefix");
        bind(bindings, "transport.redis.consumer_name", ValueType.STRING, "redis_consumer_name", "redisConsumerName");
        bind(bindings, "transport.redis.reclaim.enabled", ValueType.BOOLEAN, "redis_reclaim_enabled", "redisReclaimEnabled");
        bind(bindings, "transport.redis.reclaim.min_idle_ms", ValueType.LONG, "redis_reclaim_min_idle_ms", "redisReclaimMinIdleMs");
        bind(bindings, "transport.redis.reclaim.batch", ValueType.INT, "redis_reclaim_batch", "redisReclaimBatch");
        bind(bindings, "transport.redis.dlq.enabled", ValueType.BOOLEAN, "redis_dlq_enabled", "redisDlqEnabled");
        bind(bindings, "transport.redis.dlq.max_delivery_attempts", ValueType.INT, "redis_max_delivery_attempts", "redisMaxDeliveryAttempts");
        bind(bindings, "transport.redis.dlq.prefix", ValueType.STRING, "redis_dlq_prefix", "redisDlqPrefix");

        bind(bindings, "event_hub.enabled", ValueType.BOOLEAN, "is_event_hub_map", "isEventHubMap");
        bind(bindings, "event_hub.map_id", ValueType.STRING, "event_hub_map_id", "eventHubMapID");

        bind(bindings, "translation.enabled", ValueType.BOOLEAN);
        bind(bindings, "translation.pipeline", ValueType.STRING_LIST);
        bind(bindings, "translation.preserve_original_message_on_failure", ValueType.BOOLEAN);
        bind(bindings, "translation.cache.enabled", ValueType.BOOLEAN);
        bind(bindings, "translation.cache.ttl_seconds", ValueType.INT);
        bind(bindings, "translation.cache.max_text_length", ValueType.INT);
        bind(bindings, "translation.metrics.enabled", ValueType.BOOLEAN);
        bind(bindings, "translation.metrics.minute_buckets_enabled", ValueType.BOOLEAN);
        bind(bindings, "translation.metrics.minute_bucket_ttl_seconds", ValueType.INT);
        bind(bindings, "translation.llm.preserve_formatting_tokens", ValueType.BOOLEAN);
        bind(bindings, "translation.llm.structured_output_required", ValueType.BOOLEAN);
        bind(bindings, "translation.llm.max_input_chars", ValueType.INT);
        bind(bindings, "translation.llm.max_output_chars", ValueType.INT);
        bind(bindings, "translation.llm.strip_control_characters", ValueType.BOOLEAN);

        bind(bindings, "ip_reputation.enabled", ValueType.BOOLEAN);
        bind(bindings, "ip_reputation.block_proxy", ValueType.BOOLEAN);
        bind(bindings, "ip_reputation.block_vpn", ValueType.BOOLEAN);
        bind(bindings, "ip_reputation.block_tor", ValueType.BOOLEAN);
        bind(bindings, "ip_reputation.block_hosting", ValueType.BOOLEAN);
        bind(bindings, "ip_reputation.cache_ttl_seconds", ValueType.INT);

        return bindings;
    }

    private static void bind(Map<String, PathBinding> bindings, String canonicalPath, ValueType type, String... aliases) {
        PathBinding binding = new PathBinding(canonicalPath, type);
        bindings.put(canonicalPath, binding);
        for (String alias : aliases) {
            bindings.put(alias, binding);
        }
    }

    private record PathBinding(String canonicalPath, ValueType valueType) {
    }

    private record PathLocation(JsonObject parent, String key) {
    }

    private enum ValueType {
        STRING {
            @Override
            void apply(JsonObject parent, String key, String value, Gson gson) {
                parent.addProperty(key, value);
            }
        },
        BOOLEAN {
            @Override
            void apply(JsonObject parent, String key, String value, Gson gson) {
                String trimmed = value.trim();
                if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
                    parent.addProperty(key, Boolean.parseBoolean(trimmed));
                    return;
                }

                throw invalidValue(key, "true or false", value);
            }
        },
        LONG {
            @Override
            void apply(JsonObject parent, String key, String value, Gson gson) {
                try {
                    parent.addProperty(key, Long.parseLong(value.trim()));
                } catch (NumberFormatException e) {
                    throw invalidValue(key, "integer number", value, e);
                }
            }
        },
        DISCORD_SNOWFLAKE {
            @Override
            void apply(JsonObject parent, String key, String value, Gson gson) {
                String trimmed = value.trim();
                if (trimmed.isEmpty()) {
                    parent.addProperty(key, "0");
                    return;
                }
                for (int i = 0; i < trimmed.length(); i++) {
                    char ch = trimmed.charAt(i);
                    if (ch < '0' || ch > '9') {
                        throw invalidValue(key, "decimal digits", value);
                    }
                }
                try {
                    Long.parseLong(trimmed);
                } catch (NumberFormatException e) {
                    throw invalidValue(key, "signed 64-bit decimal digits", value, e);
                }
                parent.addProperty(key, trimmed);
            }
        },
        INT {
            @Override
            void apply(JsonObject parent, String key, String value, Gson gson) {
                try {
                    parent.addProperty(key, Integer.parseInt(value.trim()));
                } catch (NumberFormatException e) {
                    throw invalidValue(key, "integer number", value, e);
                }
            }
        },
        STRING_LIST {
            @Override
            void apply(JsonObject parent, String key, String value, Gson gson) {
                parent.add(key, parseStringList(value, gson));
            }
        };

        abstract void apply(JsonObject parent, String key, String value, Gson gson);

        private static JsonArray parseStringList(String value, Gson gson) {
            String trimmed = value.trim();
            if (trimmed.startsWith("[")) {
                try {
                    String[] values = gson.fromJson(trimmed, String[].class);
                    JsonArray array = new JsonArray();
                    if (values != null) {
                        Arrays.stream(values)
                                .filter(Objects::nonNull)
                                .map(String::trim)
                                .filter(entry -> !entry.isEmpty())
                                .forEach(array::add);
                    }
                    return array;
                } catch (RuntimeException e) {
                    throw invalidValue("translation.pipeline", "a comma-separated list or JSON string array", value, e);
                }
            }

            JsonArray array = new JsonArray();
            Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(entry -> !entry.isEmpty())
                    .forEach(array::add);
            return array;
        }
    }
}
