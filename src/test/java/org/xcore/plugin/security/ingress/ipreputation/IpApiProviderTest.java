package org.xcore.plugin.security.ingress.ipreputation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.GlobalConfig;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IpApiProviderTest {

    @Test
    @DisplayName("lookup returns parsed result on success")
    void lookup_success_returnsParsedResult() throws Exception {
        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);

        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {
                  "status": "success",
                  "query": "1.2.3.4",
                  "proxy": true,
                  "hosting": false,
                  "mobile": false
                }
                """);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        IpApiProvider provider = newProvider(client);
        IpReputationResult result = provider.lookup("1.2.3.4");

        assertThat(result).isNotNull();
        assertThat(result.ip()).isEqualTo("1.2.3.4");
        assertThat(result.proxy()).isTrue();
        assertThat(result.hosting()).isFalse();
        assertThat(result.mobile()).isFalse();
    }

    @Test
    @DisplayName("lookup returns null when status is fail")
    void lookup_statusFail_returnsNull() throws Exception {
        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);

        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"status":"fail","message":"invalid query"}
                """);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        IpApiProvider provider = newProvider(client);

        assertThat(provider.lookup("1.2.3.4")).isNull();
    }

    @Test
    @DisplayName("lookup returns null on non-2xx response")
    void lookup_non2xx_returnsNull() throws Exception {
        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);

        when(response.statusCode()).thenReturn(429);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        IpApiProvider provider = newProvider(client);

        assertThat(provider.lookup("1.2.3.4")).isNull();
    }

    @Test
    @DisplayName("lookup returns null on IOException")
    void lookup_ioException_returnsNull() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("connection refused"));

        IpApiProvider provider = newProvider(client);

        assertThat(provider.lookup("1.2.3.4")).isNull();
    }

    @Test
    @DisplayName("lookup returns null on RuntimeException")
    void lookup_runtimeException_returnsNull() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new RuntimeException("unexpected"));

        IpApiProvider provider = newProvider(client);

        assertThat(provider.lookup("1.2.3.4")).isNull();
    }

    @Test
    @DisplayName("lookup returns null for blank ip")
    void lookup_blankIp_returnsNull() {
        HttpClient client = mock(HttpClient.class);
        IpApiProvider provider = newProvider(client);

        assertThat(provider.lookup("")).isNull();
        assertThat(provider.lookup(null)).isNull();
    }

    @Test
    @DisplayName("lookup returns null on invalid JSON")
    void lookup_invalidJson_returnsNull() throws Exception {
        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);

        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("not json");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        IpApiProvider provider = newProvider(client);

        assertThat(provider.lookup("1.2.3.4")).isNull();
    }

    @Test
    @DisplayName("lookup retries on failure and returns null after exhausting retries")
    void lookup_retriesThenReturnsNull() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("connection refused"));

        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.ipReputationProvider.maxRetries = 2;
        globalConfig.ipReputationProvider.timeoutSeconds = 1;

        IpApiProvider provider = new IpApiProvider(globalConfig, client);

        assertThat(provider.lookup("1.2.3.4")).isNull();
    }

    @Test
    @DisplayName("lookup succeeds on retry")
    void lookup_succeedsOnRetry() throws Exception {
        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);

        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {
                  "status": "success",
                  "query": "1.2.3.4",
                  "proxy": false,
                  "hosting": false,
                  "mobile": false
                }
                """);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("first attempt fails"))
                .thenReturn(response);

        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.ipReputationProvider.maxRetries = 2;
        globalConfig.ipReputationProvider.timeoutSeconds = 1;

        IpApiProvider provider = new IpApiProvider(globalConfig, client);
        IpReputationResult result = provider.lookup("1.2.3.4");

        assertThat(result).isNotNull();
        assertThat(result.proxy()).isFalse();
    }

    @Test
    @DisplayName("lookup returns null when interrupted")
    void lookup_interrupted_returnsNull() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("interrupted"));

        IpApiProvider provider = newProvider(client);

        assertThat(provider.lookup("1.2.3.4")).isNull();
    }

    private static IpApiProvider newProvider(HttpClient client) {
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.ipReputationProvider.maxRetries = 0;
        globalConfig.ipReputationProvider.timeoutSeconds = 1;
        return new IpApiProvider(globalConfig, client);
    }
}
