package org.xcore.plugin.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.event.transport.ChatTransportHandler;
import org.xcore.plugin.event.transport.DiscordLinkTransportHandler;
import org.xcore.plugin.event.transport.MapTransportHandler;
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
import java.util.Deque;

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
    @DisplayName("resolve host address caches successful resolver response")
    void resolveHostAddress_cachesSuccessfulResolverResponse() {
        // Arrange
        TomlXcoreConfig config = new TomlXcoreConfig();
        TestTransportService service = new TestTransportService(config);
        service.enqueueConnection(new StubHttpURLConnection("198.51.100.24\n"));

        // Act
        String firstResolvedHost = service.resolveHostAddress();
        String secondResolvedHost = service.resolveHostAddress();

        // Assert
        assertThat(firstResolvedHost).isEqualTo("198.51.100.24");
        assertThat(secondResolvedHost).isEqualTo("198.51.100.24");
        assertThat(service.openConnectionCount()).isEqualTo(1);
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
        String firstResolvedHost = service.resolveHostAddress();
        service.setCurrentTimeMillis(12_000L);
        String backoffResolvedHost = service.resolveHostAddress();
        service.setCurrentTimeMillis(15_000L);
        String retriedResolvedHost = service.resolveHostAddress();

        // Assert
        assertThat(firstResolvedHost).isNull();
        assertThat(backoffResolvedHost).isNull();
        assertThat(retriedResolvedHost).isEqualTo("203.0.113.7");
        assertThat(service.openConnectionCount()).isEqualTo(2);
    }

    private static final class TestTransportService extends TransportService {

        private final Deque<Object> resolverOutcomes = new ArrayDeque<>();
        private long currentTimeMillis;
        private long failureBackoffMs = HOST_RESOLUTION_FAILURE_BACKOFF_MS;
        private int openConnectionCount;

        private TestTransportService(TomlXcoreConfig config) {
            super(
                    mock(ChatTransportHandler.class),
                    mock(DiscordLinkTransportHandler.class),
                    mock(ModerationTransportHandler.class),
                    mock(MapTransportHandler.class),
                    mock(NetworkService.class),
                    config
            );
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
