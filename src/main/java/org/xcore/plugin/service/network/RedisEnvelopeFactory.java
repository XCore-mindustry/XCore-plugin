package org.xcore.plugin.service.network;

import com.google.gson.Gson;
import io.lettuce.core.StreamMessage;
import org.xcore.plugin.config.Config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class RedisEnvelopeFactory {
    private final Config config;
    private final Gson gson;

    RedisEnvelopeFactory(Config config, Gson gson) {
        this.config = config;
        this.gson = gson;
    }

    Map<String, String> eventFields(RedisStreamRouter.Route route, String payloadJson, long now) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("schema_version", "1");
        fields.put("event_type", route.eventType());
        fields.put("event_id", UUID.randomUUID().toString());
        fields.put(
                "idempotency_key",
                deterministicIdempotencyKey(
                        route.eventType(),
                        config.server,
                        payloadJson,
                        now,
                        Math.max(60_000L, route.ttlMillis())
                )
        );
        fields.put("producer", "server:" + config.server);
        fields.put("created_at", String.valueOf(now));
        fields.put("expires_at", String.valueOf(now + route.ttlMillis()));
        fields.put("server", config.server);
        fields.put("payload_json", payloadJson);
        return fields;
    }

    Map<String, String> rpcRequestFields(RedisStreamRouter.Route route,
                                         String requestJson,
                                         String replyTo,
                                         String correlationId,
                                         long now,
                                         long timeoutMs) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("schema_version", "1");
        fields.put("rpc_type", route.eventType());
        fields.put("correlation_id", correlationId);
        fields.put("request_id", UUID.randomUUID().toString());
        fields.put(
                "idempotency_key",
                deterministicIdempotencyKey(
                        "rpc." + route.eventType(),
                        config.server,
                        requestJson,
                        now,
                        Math.max(60_000L, timeoutMs)
                )
        );
        fields.put("reply_to", replyTo);
        fields.put("requested_by", "server:" + config.server);
        fields.put("server", config.server);
        fields.put("timeout_ms", String.valueOf(timeoutMs));
        fields.put("created_at", String.valueOf(now));
        fields.put("expires_at", String.valueOf(now + timeoutMs));
        fields.put("payload_json", requestJson);
        return fields;
    }

    Map<String, String> rpcResponseFields(RedisRpcTracker.RpcInboundContext context, String responseJson, long respondedAt) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("schema_version", "1");
        fields.put("rpc_type", context.rpcType());
        fields.put("correlation_id", context.correlationId());
        fields.put("server", config.server);
        fields.put("status", "ok");
        fields.put("error_code", "");
        fields.put("error_message", "");
        fields.put("responded_at", String.valueOf(respondedAt));
        fields.put("payload_json", responseJson);
        return fields;
    }

    Map<String, String> dlqFields(String sourceStream,
                                  String sourceGroup,
                                  StreamMessage<String, String> message,
                                  int attempts,
                                  String reason,
                                  long failedAt) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("source_stream", sourceStream);
        fields.put("source_group", sourceGroup);
        fields.put("source_id", message.getId());
        fields.put("failed_at", String.valueOf(failedAt));
        fields.put("attempts", String.valueOf(attempts));
        fields.put("failure_reason", reason);
        fields.put("event_type", message.getBody().getOrDefault("event_type", ""));
        fields.put("rpc_type", message.getBody().getOrDefault("rpc_type", ""));
        fields.put("message_json", gson.toJson(message.getBody()));
        return fields;
    }

    long resolveIdempotencyTtlSeconds(long expiresAtMillis) {
        if (expiresAtMillis <= 0L) {
            return 600L;
        }
        long ttlMillis = expiresAtMillis - System.currentTimeMillis();
        long ttlSeconds = Math.max(1L, ttlMillis / 1000L);
        return Math.min(ttlSeconds, 86_400L);
    }

    private String deterministicIdempotencyKey(String prefix,
                                               String server,
                                               String payloadJson,
                                               long nowMs,
                                               long ttlMs) {
        long windowMs = Math.max(60_000L, Math.min(ttlMs, 600_000L));
        long window = nowMs / windowMs;
        String material = prefix + "|" + server + "|" + payloadJson + "|" + window;
        return prefix + ":" + sha256Hex(material).substring(0, 24);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", e);
        }
    }
}
