package org.xcore.plugin.security.ingress.ipreputation;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import jakarta.inject.Singleton;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.config.GlobalConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;

/**
 * IP reputation provider backed by ip-api.com.
 * <p>
 * Transport concerns (HTTP, JSON parsing, retries) are internal to this class.
 * The public surface ({@link IpReputationProvider}) is transport-agnostic.
 * <p>
 * Fail-open: any lookup failure returns {@code null} so the caller does not
 * block ingress on provider errors.
 */
@Singleton
public class IpApiProvider implements IpReputationProvider {

    private static final Gson GSON = new Gson();
    private static final long RATE_LIMIT_WINDOW_MILLIS = 60_000L;

    private final GlobalConfig.IpReputationProviderConfig providerConfig;
    private final HttpClient client;
    private final Deque<Long> requestTimestamps = new ArrayDeque<>();

    public IpApiProvider(GlobalConfig globalConfig) {
        this(globalConfig, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(globalConfig.ipReputationProvider.timeoutSeconds))
                .build());
    }

    // Package-private for testing
    IpApiProvider(GlobalConfig globalConfig, HttpClient client) {
        this.providerConfig = globalConfig.ipReputationProvider;
        this.client = client;
    }

    @Override
    public IpReputationResult lookup(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }

        String normalized = ip.trim();
        String ipToken = hashIp(normalized);
        String url = buildUrl(normalized);

        int maxAttempts = Math.max(1, providerConfig.maxRetries + 1);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (!tryAcquireRequestSlot()) {
                PLog.warnTag("IpApiProvider", "Skipping lookup for @ because local rate limit @/min was reached",
                        ipToken, providerConfig.rateLimitPerMinute);
                return null;
            }

            try {
                return executeLookup(url);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                PLog.warnTag("IpApiProvider", "Lookup interrupted for @", ipToken);
                return null;
            } catch (IOException | RuntimeException e) {
                if (attempt < maxAttempts) {
                    PLog.warnTag("IpApiProvider", "Lookup failed for @ (attempt @/@), retrying: @",
                            ipToken, attempt, maxAttempts, e.getMessage());
                    backoff(attempt);
                } else {
                    PLog.warnTag("IpApiProvider", "Lookup failed for @ after @ attempts: @",
                            ipToken, maxAttempts, e.getMessage());
                }
            }
        }

        return null;
    }

    private IpReputationResult executeLookup(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(providerConfig.timeoutSeconds))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("HTTP " + statusCode);
        }

        return parseResponse(response.body());
    }

    private IpReputationResult parseResponse(String body) {
        try {
            JsonObject json = GSON.fromJson(body, JsonObject.class);
            if (json == null) {
                return null;
            }

            // ip-api returns "status" field; "fail" means the query could not be resolved
            String status = json.has("status") ? json.get("status").getAsString() : null;
            if (!"success".equalsIgnoreCase(status)) {
                return null;
            }

            String ip = json.has("query") ? json.get("query").getAsString() : null;
            boolean proxy = json.has("proxy") && json.get("proxy").getAsBoolean();
            boolean hosting = json.has("hosting") && json.get("hosting").getAsBoolean();
            boolean mobile = json.has("mobile") && json.get("mobile").getAsBoolean();

            return new IpReputationResult(ip, proxy, hosting, mobile);
        } catch (JsonParseException | IllegalStateException | ClassCastException e) {
            PLog.warnTag("IpApiProvider", "Failed to parse response: @", e.getMessage());
            return null;
        }
    }

    private String buildUrl(String ip) {
        String base = providerConfig.baseUrl;
        // Normalize: strip trailing slash, then append /{ip}
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + ip;
    }

    private void backoff(int attempt) {
        try {
            // Simple exponential backoff: 200ms, 400ms, 800ms...
            long delayMs = 200L * (1L << (attempt - 1));
            Thread.sleep(Math.min(delayMs, 2000L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean tryAcquireRequestSlot() {
        synchronized (requestTimestamps) {
            long now = System.currentTimeMillis();
            long cutoff = now - RATE_LIMIT_WINDOW_MILLIS;

            while (!requestTimestamps.isEmpty() && requestTimestamps.peekFirst() <= cutoff) {
                requestTimestamps.removeFirst();
            }

            if (requestTimestamps.size() >= providerConfig.rateLimitPerMinute) {
                return false;
            }

            requestTimestamps.addLast(now);
            return true;
        }
    }

    private String hashIp(String ip) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(ip.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
