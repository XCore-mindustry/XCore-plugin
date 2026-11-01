package org.xcore.plugin.service.map.inspect;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

class MapDownloaderTest {
    @TempDir Path directory;
    private HttpServer server;
    private ExecutorService workers;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        workers = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(workers); server.start();
    }

    @AfterEach
    void stop() { server.stop(0); workers.shutdownNow(); }

    @Test
    void followsRelativeRedirectHashesExactBytesAndDeletesSuccessfulTemp() throws Exception {
        byte[] bytes = new MsavFixture().compressed();
        server.createContext("/map", exchange -> send(exchange, 200, bytes.length, bytes));
        server.createContext("/redirect", exchange -> { exchange.getResponseHeaders().set("Location", "/map"); send(exchange, 302, -1, new byte[0]); });
        try (var downloader = new MapDownloader(Duration.ofSeconds(3), directory)) {
            Path path;
            try (var download = downloader.download(url("/redirect"), bytes.length)) {
                path = download.path();
                assertThat(Files.readAllBytes(path)).isEqualTo(bytes);
                assertThat(download.sizeBytes()).isEqualTo(bytes.length);
                assertThat(download.sha256()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            }
            assertThat(path).doesNotExist();
            assertClean();
        }
    }

    @Test
    void declaredAndChunkedLimitsAlsoApplyAfterRedirects() throws Exception {
        server.createContext("/declared", exchange -> send(exchange, 200, 128, new byte[128]));
        server.createContext("/chunked", exchange -> send(exchange, 200, 0, new byte[128]));
        server.createContext("/redirect", exchange -> { exchange.getResponseHeaders().set("Location", "/chunked"); send(exchange, 307, -1, new byte[0]); });
        try (var downloader = new MapDownloader(Duration.ofSeconds(3), directory)) {
            for (String path : new String[]{"/declared", "/chunked", "/redirect"}) {
                assertThatThrownBy(() -> downloader.download(url(path), 32)).isInstanceOf(IOException.class).hasMessageContaining("maxFileBytes");
                assertClean();
            }
        }
    }

    @Test
    void timeoutCoversStalledBodyRatherThanOnlyResponseHeaders() throws Exception {
        var release = new CountDownLatch(1);
        var headers = new CountDownLatch(1);
        server.createContext("/stall", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, 0); exchange.getResponseBody().write(1); exchange.getResponseBody().flush();
                headers.countDown();
                try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
        });
        try (var downloader = new MapDownloader(Duration.ofMillis(300), directory)) {
            var future = workers.submit(() -> downloader.download(url("/stall"), 1024));
            assertThat(headers.await(2, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> future.get(2, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).cause().isInstanceOf(IOException.class);
            assertClean();
        } finally { release.countDown(); }
    }

    @Test
    void timeoutAlsoCoversHeadersAndRedirectChain() throws Exception {
        var release = new CountDownLatch(1);
        server.createContext("/no-headers", exchange -> {
            try (exchange) { try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); } }
        });
        server.createContext("/redirect", exchange -> { exchange.getResponseHeaders().set("Location", "/no-headers"); send(exchange, 308, -1, new byte[0]); });
        try (var downloader = new MapDownloader(Duration.ofMillis(200), directory)) {
            assertThatThrownBy(() -> downloader.download(url("/redirect"), 1024)).isInstanceOf(IOException.class);
            assertClean();
        } finally { release.countDown(); }
    }

    @Test
    void rejectsRedirectLoopUnsupportedSchemesHttpErrorsAndTruncatedBodyWithCleanup() throws Exception {
        server.createContext("/loop", exchange -> { exchange.getResponseHeaders().set("Location", "/loop"); send(exchange, 301, -1, new byte[0]); });
        server.createContext("/bad-redirect", exchange -> { exchange.getResponseHeaders().set("Location", "file:///tmp/map.msav"); send(exchange, 302, -1, new byte[0]); });
        server.createContext("/error", exchange -> send(exchange, 404, -1, new byte[0]));
        server.createContext("/truncated", exchange -> send(exchange, 200, 100, new byte[2]));
        try (var downloader = new MapDownloader(Duration.ofSeconds(2), directory)) {
            for (String path : new String[]{url("/loop"), url("/bad-redirect"), url("/error"), url("/truncated"), "file:///tmp/map.msav", "http://user:password@127.0.0.1/map", "bad url"}) {
                assertThatThrownBy(() -> downloader.download(path, 1024)).isInstanceOf(IOException.class);
                assertClean();
            }
        }
    }

    private String url(String path) { return "http://127.0.0.1:" + server.getAddress().getPort() + path; }
    private void assertClean() throws IOException { try (var files = Files.list(directory)) { assertThat(files).isEmpty(); } }
    private static void send(HttpExchange exchange, int status, long length, byte[] body) throws IOException {
        try (exchange) { exchange.sendResponseHeaders(status, length); if (length >= 0) exchange.getResponseBody().write(body); }
    }
}
