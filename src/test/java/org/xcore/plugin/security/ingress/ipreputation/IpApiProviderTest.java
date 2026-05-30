package org.xcore.plugin.security.ingress.ipreputation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

        TomlSecretsConfig secretsConfig = new TomlSecretsConfig();
        secretsConfig.ipReputation.provider.maxRetries = 2;
        secretsConfig.ipReputation.provider.timeoutSeconds = 1;

        IpApiProvider provider = new IpApiProvider(secretsConfig, client);

        assertThat(provider.lookup("1.2.3.4")).isNull();
        verify(client, times(secretsConfig.ipReputation.provider.maxRetries + 1))
                .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @DisplayName("lookup returns null when local provider rate limit is exceeded")
    void lookup_rateLimitExceeded_returnsNullWithoutSecondRequest() throws Exception {
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
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        TomlSecretsConfig secretsConfig = new TomlSecretsConfig();
        secretsConfig.ipReputation.provider.maxRetries = 0;
        secretsConfig.ipReputation.provider.timeoutSeconds = 1;
        secretsConfig.ipReputation.provider.rateLimitPerMinute = 1;

        IpApiProvider provider = new IpApiProvider(secretsConfig, client);

        assertThat(provider.lookup("1.2.3.4")).isNotNull();
        assertThat(provider.lookup("5.6.7.8")).isNull();
        verify(client, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
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

        TomlSecretsConfig secretsConfig = new TomlSecretsConfig();
        secretsConfig.ipReputation.provider.maxRetries = 2;
        secretsConfig.ipReputation.provider.timeoutSeconds = 1;

        IpApiProvider provider = new IpApiProvider(secretsConfig, client);
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
        TomlSecretsConfig secretsConfig = new TomlSecretsConfig();
        secretsConfig.ipReputation.provider.maxRetries = 0;
        secretsConfig.ipReputation.provider.timeoutSeconds = 1;
        return new IpApiProvider(secretsConfig, client);
    }
}
