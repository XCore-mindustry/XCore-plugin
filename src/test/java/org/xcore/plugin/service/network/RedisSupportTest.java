package org.xcore.plugin.service.network;

import com.google.gson.Gson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.Config;

import static org.assertj.core.api.Assertions.assertThat;

class RedisSupportTest {

    @Test
    @DisplayName("stream support resolves max lengths and dlq streams by prefix")
    void streamSupport_resolvesMaxLengthsAndDlqTargets() {
        Config config = new Config();
        config.redisDlqPrefix = "xcore:dead";
        RedisStreamSupport support = new RedisStreamSupport(config);

        assertThat(support.streamMaxLen("xcore:evt:chat:message")).isEqualTo(50_000L);
        assertThat(support.streamMaxLen("xcore:cmd:maps-load:event")).isEqualTo(10_000L);
        assertThat(support.streamMaxLen("xcore:rpc:req:mini-pvp")).isEqualTo(5_000L);
        assertThat(support.streamMaxLen("xcore:rpc:resp:mini-pvp")).isEqualTo(20_000L);
        assertThat(support.streamMaxLen("xcore:dead:evt")).isEqualTo(100_000L);
        assertThat(support.dlqStreamFor("xcore:rpc:req:mini-pvp")).isEqualTo("xcore:dead:rpc");
        assertThat(support.dlqStreamFor("xcore:cmd:maps-load:event")).isEqualTo("xcore:dead:cmd");
        assertThat(support.dlqStreamFor("xcore:evt:chat:message")).isEqualTo("xcore:dead:evt");
    }

    @Test
    @DisplayName("envelope factory builds deterministic idempotent event metadata")
    void envelopeFactory_buildsEventMetadata() {
        Config config = new Config();
        config.server = "mini-pvp";
        RedisEnvelopeFactory factory = new RedisEnvelopeFactory(config, new Gson());
        RedisStreamRouter.Route route = new RedisStreamRouter.Route("xcore:evt:chat:message", "chat.message", 60_000L);

        var first = factory.eventFields(route, "{\"message\":\"hello\"}", 1_000L);
        var second = factory.eventFields(route, "{\"message\":\"hello\"}", 1_000L);

        assertThat(first.get("event_type")).isEqualTo("chat.message");
        assertThat(first.get("producer")).isEqualTo("server:mini-pvp");
        assertThat(first.get("server")).isEqualTo("mini-pvp");
        assertThat(first.get("payload_json")).isEqualTo("{\"message\":\"hello\"}");
        assertThat(first.get("idempotency_key")).isEqualTo(second.get("idempotency_key"));
        assertThat(first.get("event_id")).isNotBlank();
    }
}
