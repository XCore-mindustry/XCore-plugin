package org.xcore.plugin.service.map.inspect;

import io.avaje.inject.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.startup.ShutdownOrder;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Blocking worker API; one deadline covers headers, redirects and the entire streaming body. */
@Singleton
public final class MapDownloader implements AutoCloseable {
    private final HttpClient client;
    private final Duration timeout;
    private final Path tempDirectory;
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("map-inspection-deadline").factory());

    @Inject
    public MapDownloader() { this(Duration.ofSeconds(30), null); }

    public MapDownloader(Duration timeout, Path tempDirectory) {
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("Positive timeout required");
        this.timeout = timeout;
        this.tempDirectory = tempDirectory;
        client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public Download download(String url, int maxBytes) throws IOException {
        if (maxBytes <= 0) throw new IllegalArgumentException("Positive download limit required");
        Path file = tempDirectory == null ? Files.createTempFile("xcore-inspect-", ".msav")
                : Files.createTempFile(tempDirectory, "xcore-inspect-", ".msav");
        boolean success = false;
        var timedOut = new AtomicBoolean();
        var body = new AtomicReference<InputStream>();
        var pending = new AtomicReference<CompletableFuture<HttpResponse<InputStream>>>();
        ScheduledFuture<?> alarm = null;
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            alarm = deadlines.schedule(() -> {
                timedOut.set(true);
                var request = pending.get();
                if (request != null) request.cancel(true);
                closeQuietly(body.get());
            }, timeout.toNanos(), TimeUnit.NANOSECONDS);
            URI uri = httpUri(url);
            for (int redirects = 0; redirects <= 5; redirects++) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0 || timedOut.get()) throw new IOException("Map download timed out");
                var request = HttpRequest.newBuilder(uri).timeout(Duration.ofNanos(remaining)).GET().build();
                var future = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
                pending.set(future);
                future.thenAccept(response -> {
                    if (timedOut.get()) closeQuietly(response.body());
                });
                HttpResponse<InputStream> response;
                try { response = future.get(remaining, TimeUnit.NANOSECONDS); }
                catch (TimeoutException | CancellationException ex) {
                    future.cancel(true);
                    throw new IOException("Map download timed out", ex);
                } catch (InterruptedException ex) {
                    future.cancel(true);
                    Thread.currentThread().interrupt();
                    throw new IOException("Map download interrupted", ex);
                } catch (ExecutionException ex) { throw new IOException("Map download failed", ex.getCause()); }
                Download ready;
                try (InputStream input = response.body()) {
                    body.set(input);
                    if (timedOut.get()) throw new IOException("Map download timed out");
                    int status = response.statusCode();
                    if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                        if (redirects == 5) throw new IOException("Too many map download redirects");
                        String location = response.headers().firstValue("Location").orElseThrow(() -> new IOException("Missing redirect location"));
                        uri = httpUri(uri.resolve(location).toString());
                        continue;
                    }
                    if (status < 200 || status >= 300) throw new IOException("Map download HTTP status " + status);
                    long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                    if (declared > maxBytes) throw new IOException("Map download exceeds maxFileBytes");
                    var digest = sha256();
                    int size = 0;
                    try (var output = Files.newOutputStream(file)) {
                        byte[] buffer = new byte[8192];
                        for (;;) {
                            if (timedOut.get() || System.nanoTime() >= deadline) throw new IOException("Map download timed out");
                            int read = input.read(buffer, 0, (int) Math.min(buffer.length, (long) maxBytes - size + 1));
                            if (read < 0) break;
                            if ((long) size + read > maxBytes) throw new IOException("Map download exceeds maxFileBytes");
                            output.write(buffer, 0, read);
                            digest.update(buffer, 0, read);
                            size += read;
                        }
                    }
                    if (timedOut.get() || System.nanoTime() >= deadline) throw new IOException("Map download timed out");
                    ready = new Download(file, HexFormat.of().formatHex(digest.digest()), size);
                } finally { body.set(null); }
                success = true;
                return ready;
            }
            throw new IOException("Too many map download redirects");
        } catch (IllegalArgumentException ex) { throw new IOException("Invalid map download URL or headers", ex); }
        finally {
            if (alarm != null) alarm.cancel(false);
            if (!success) Files.deleteIfExists(file);
        }
    }

    private static URI httpUri(String url) throws IOException {
        URI uri = URI.create(url);
        if ((!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null) throw new IOException("HTTP(S) map URL required");
        return uri;
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static void closeQuietly(InputStream input) {
        if (input != null) try { input.close(); } catch (IOException ignored) {}
    }

    @Override
    @PreDestroy(priority = ShutdownOrder.DRAIN_STORAGE)
    public void close() { deadlines.shutdownNow(); client.shutdownNow(); }

    public record Download(Path path, String sha256, int sizeBytes) implements AutoCloseable {
        @Override public void close() throws IOException { Files.deleteIfExists(path); }
    }
}
