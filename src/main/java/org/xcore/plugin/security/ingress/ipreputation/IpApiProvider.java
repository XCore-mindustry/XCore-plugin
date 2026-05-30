package org.xcore.plugin.security.ingress.ipreputation;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import jakarta.inject.Singleton;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.config.TomlSecretsConfig;

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

    private final TomlSecretsConfig.IpReputationSection.ProviderConfig providerConfig;
    private final HttpClient client;
    private final Deque<Long> requestTimestamps = new ArrayDeque<>();

    /**
     * Creates a new IpApiProvider using values from the provided global configuration.
     *
     * The provider will use an HttpClient configured with a connect timeout derived from
     * secretsConfig.ipReputation.provider.timeoutSeconds.
     *
     * @param secretsConfig structured secrets configuration containing ip reputation provider settings
     */
    public IpApiProvider(TomlSecretsConfig secretsConfig) {
        this(secretsConfig, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(secretsConfig.ipReputation.provider.timeoutSeconds))
                .build());
    }

    /**
     * Package-private constructor used for testing; initializes the provider with the given configuration and HTTP client.
     *
     * @param secretsConfig supplies the structured ip reputation provider settings (base URL, timeouts, retries, rate limits)
     * @param client       the {@link HttpClient} to use for HTTP requests (injected, typically a test client)
     */
    IpApiProvider(TomlSecretsConfig secretsConfig, HttpClient client) {
        this.providerConfig = secretsConfig.ipReputation.provider;
        this.client = client;
    }

    /**
     * Performs an IP reputation lookup for the given IP and returns the parsed result or `null` when unavailable.
     *
     * The method trims the input, enforces a local per-minute rate limit, retries transient failures with
     * exponential backoff up to the configured retry count, and returns `null` on invalid input, interruption,
     * rate limiting, non-successful responses, parsing errors, or when all attempts fail.
     *
     * @param ip the IP address to lookup; leading and trailing whitespace will be ignored
     * @return the resolved {@code IpReputationResult} for the IP, or {@code null} if the lookup cannot be completed
     */
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

    /**
     * Performs an HTTP GET to the given URL and parses the JSON response into an IpReputationResult.
     *
     * @param url the full request URL (including the target IP) to query
     * @return the parsed IpReputationResult from the response body, or `null` if the response JSON
     *         does not indicate a successful lookup or cannot be parsed
     * @throws IOException if the HTTP response status is not in the 2xx range or an I/O error occurs
     * @throws InterruptedException if the thread is interrupted while sending the HTTP request
     */
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

    /**
     * Parses the JSON response from ip-api.com and produces an IpReputationResult when the response indicates success.
     *
     * @param body the raw JSON response body from ip-api.com
     * @return an IpReputationResult populated from the response when `status` equals "success", or `null` if the response is missing/invalid, the status is not "success", or parsing fails
     */
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

    /**
     * Builds the full request URL for the configured provider by appending the given IP.
     *
     * Normalizes the configured base URL by removing a trailing '/' if present, then appends
     * '/' followed by the provided IP.
     *
     * @param ip the IP address to append to the provider base URL
     * @return the normalized base URL with the IP appended (e.g. "https://api.example.com/1.2.3.4")
     */
    private String buildUrl(String ip) {
        String base = providerConfig.baseUrl;
        // Normalize: strip trailing slash, then append /{ip}
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + ip;
    }

    /**
     * Sleep for an exponential backoff delay based on the attempt number, capped at 2000 ms.
     *
     * If the thread is interrupted while sleeping, the interrupt status is restored before returning.
     *
     * @param attempt 1-based attempt number used to compute the delay (delay = 200ms * 2^(attempt-1), capped at 2000ms)
     */
    private void backoff(int attempt) {
        try {
            // Simple exponential backoff: 200ms, 400ms, 800ms...
            long delayMs = 200L * (1L << (attempt - 1));
            Thread.sleep(Math.min(delayMs, 2000L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Acquires a local rate-limit slot if the number of requests in the recent window is below the configured per-minute limit.
     *
     * Evicts timestamps older than RATE_LIMIT_WINDOW_MILLIS from the internal timestamp deque, compares the remaining count
     * against providerConfig.rateLimitPerMinute, and if allowed records the current time.
     *
     * @return `true` if a request slot was acquired and the current timestamp was recorded, `false` if the per-minute limit has been reached.
     */
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

    /**
     * Produce a short SHA-256 fingerprint token for an IP address suitable for logging.
     *
     * @param ip the IP address string to hash
     * @return `sha256:` followed by the first 8 hex characters of the SHA-256 digest of {@code ip}
     * @throws IllegalStateException if the SHA-256 MessageDigest is unavailable
     */
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
