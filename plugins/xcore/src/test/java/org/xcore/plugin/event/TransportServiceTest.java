package org.xcore.plugin.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.concurrent.StorageExecutor;
import org.xcore.plugin.event.transport.ChatTransportHandler;
import org.xcore.plugin.event.transport.DiscordLinkTransportHandler;
import org.xcore.plugin.event.transport.MapTransportHandler;
import org.xcore.plugin.event.transport.RatingTransportHandler;
import org.xcore.plugin.event.transport.ModerationTransportHandler;
import org.xcore.plugin.service.NetworkService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TransportServiceTest {

    @Test
    @DisplayName("resolve host address returns configured override without contacting resolver")
    void resolveHostAddress_returnsConfiguredOverrideWithoutContactingResolver() {
        // Arrange
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.publicHostOverride = "  play.xcore.example  ";
        TestTransportService service = new TestTransportService(config);

        // Act
        String resolvedHost = service.resolveHostAddress();

        // Assert
        assertThat(resolvedHost).isEqualTo("play.xcore.example");
        assertThat(service.openConnectionCount()).isZero();
    }

    @Test
    @DisplayName("resolve host address never blocks the calling thread and caches a background resolution")
    void resolveHostAddress_neverBlocksAndCachesBackgroundResolution() {
        // Arrange
        TomlXcoreConfig config = new TomlXcoreConfig();
        TestTransportService service = new TestTransportService(config);
        service.enqueueConnection(new StubHttpURLConnection("198.51.100.24\n"));

        // Act
        String firstResolvedHost = service.resolveHostAddress();
        String secondResolvedHost = service.resolveHostAddress();

        // Assert: the first call cannot have the answer yet, because the lookup was moved
        // off the calling thread. The second reads the cache the background task filled.
        assertThat(firstResolvedHost).isNull();
        assertThat(secondResolvedHost).isEqualTo("198.51.100.24");
        assertThat(service.openConnectionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("resolve host address schedules at most one lookup while one is in flight")
    void resolveHostAddress_schedulesAtMostOneLookupWhileInFlight() {
        TomlXcoreConfig config = new TomlXcoreConfig();
        TestTransportService service = new TestTransportService(config);
        service.holdScheduledTasks();
        service.enqueueConnection(new StubHttpURLConnection("198.51.100.24\n"));

        service.resolveHostAddress();
        service.resolveHostAddress();
        service.resolveHostAddress();

        assertThat(service.scheduledTaskCount()).isEqualTo(1);
        assertThat(service.openConnectionCount()).isZero();
    }

    @Test
    @DisplayName("resolve host address backs off after resolver failure until retry window expires")
    void resolveHostAddress_backsOffAfterResolverFailureUntilRetryWindowExpires() {
        // Arrange
        TomlXcoreConfig config = new TomlXcoreConfig();
        TestTransportService service = new TestTransportService(config);
        service.setCurrentTimeMillis(10_000L);
        service.setFailureBackoffMs(5_000L);
        service.enqueueFailure(new IOException("ipify unavailable"));
        service.enqueueConnection(new StubHttpURLConnection("203.0.113.7"));

        // Act
        assertThat(service.resolveHostAddress()).isNull();
        service.setCurrentTimeMillis(12_000L);
        assertThat(service.resolveHostAddress()).isNull();
        service.setCurrentTimeMillis(15_000L);
        assertThat(service.resolveHostAddress()).isNull();
        // The retry ran in the background; the next call reads the cache it filled.
        assertThat(service.resolveHostAddress()).isEqualTo("203.0.113.7");

        // Assert: the failure suppressed the retry inside the backoff window only.
        assertThat(service.openConnectionCount()).isEqualTo(2);
    }

    private static final class TestTransportService extends TransportService {

        private final Deque<Object> resolverOutcomes = new ArrayDeque<>();
        private long currentTimeMillis;
        private long failureBackoffMs = HOST_RESOLUTION_FAILURE_BACKOFF_MS;
        private int openConnectionCount;
        private boolean holdScheduledTasks;
        private final List<Runnable> heldTasks = new ArrayList<>();
        private int scheduledTaskCount;
        private TestTransportService(TomlXcoreConfig config) {
            super(
                    mock(ChatTransportHandler.class),
                    mock(DiscordLinkTransportHandler.class),
                    mock(ModerationTransportHandler.class),
                    mock(MapTransportHandler.class),
                    mock(RatingTransportHandler.class),
                    mock(org.xcore.plugin.event.transport.SecurityTransportHandler.class),
                    mock(NetworkService.class),
                    config,
                    // Unused: this subclass overrides the scheduling hook and resolves inline.
                    mock(StorageExecutor.class)
            );
        }

        @Override
        protected void dispatchPublicHostResolution() {
            scheduledTaskCount++;
            if (holdScheduledTasks) {
                heldTasks.add(this::runPublicHostResolution);
                return;
            }
            runPublicHostResolution();
        }

        private void holdScheduledTasks() {
            this.holdScheduledTasks = true;
        }

        private int scheduledTaskCount() {
            return scheduledTaskCount;
        }

        private void runHeldTasks() {
            List<Runnable> pending = new ArrayList<>(heldTasks);
            heldTasks.clear();
            pending.forEach(Runnable::run);
        }

        private void enqueueConnection(HttpURLConnection connection) {
            resolverOutcomes.addLast(connection);
        }

        private void enqueueFailure(Exception failure) {
            resolverOutcomes.addLast(failure);
        }

        private void setCurrentTimeMillis(long currentTimeMillis) {
            this.currentTimeMillis = currentTimeMillis;
        }

        private void setFailureBackoffMs(long failureBackoffMs) {
            this.failureBackoffMs = failureBackoffMs;
        }

        private int openConnectionCount() {
            return openConnectionCount;
        }

        @Override
        protected HttpURLConnection openPublicHostConnection() throws Exception {
            openConnectionCount++;
            Object outcome = resolverOutcomes.removeFirst();
            if (outcome instanceof Exception failure) {
                throw failure;
            }
            return (HttpURLConnection) outcome;
        }

        @Override
        protected long currentTimeMillis() {
            return currentTimeMillis;
        }

        @Override
        protected long hostResolutionFailureBackoffMs() {
            return failureBackoffMs;
        }
    }

    private static final class StubHttpURLConnection extends HttpURLConnection {

        private final byte[] payload;

        private StubHttpURLConnection(String payload) {
            super(createUrl());
            this.payload = payload.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void disconnect() {
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(payload);
        }

        @Override
        public void setRequestMethod(String method) throws ProtocolException {
            this.method = method;
        }

        private static URL createUrl() {
            try {
                return URI.create("https://example.invalid").toURL();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
