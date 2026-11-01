package org.xcore.plugin.service.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedisConnectionManagerTest {

    @Test
    @DisplayName("sanitizeRedisUrl removes embedded credentials")
    void sanitizeRedisUrl_removesEmbeddedCredentials() {
        String sanitized = RedisConnectionManager.sanitizeRedisUrl("redis://user:secret@example.com:6379/0");

        assertThat(sanitized).isEqualTo("redis://example.com:6379/0");
        assertThat(sanitized).doesNotContain("secret");
        assertThat(sanitized).doesNotContain("user@");
    }

    @Test
    @DisplayName("sanitizeRedisUrl keeps host details for plain URLs")
    void sanitizeRedisUrl_keepsHostDetailsForPlainUrls() {
        String sanitized = RedisConnectionManager.sanitizeRedisUrl("redis://127.0.0.1:6379/1");

        assertThat(sanitized).isEqualTo("redis://127.0.0.1:6379/1");
    }
}
