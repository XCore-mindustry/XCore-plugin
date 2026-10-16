package org.xcore.plugin.service.network;

import org.junit.jupiter.api.Test;
import org.xcore.plugin.model.BanData;
import org.xcore.plugin.model.PlayerPids;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationBanCreatedV1;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ModerationProtocolMapperTest {
    @Test
    void banCreatedPayloadCarriesIdentityAndPid() {
        var ban = BanData.builder().uuid("target").name("Target").banId(1234).pid(567).build();
        var event = ModerationProtocolMapper.toBanCreated(ban, "survival", Instant.parse("2026-10-13T10:00:00Z"));

        assertThat(event.banId()).isEqualTo(1234);
        assertThat(event.target().playerPid()).isEqualTo(567);
        assertThat(event.toPayload()).containsEntry("banId", 1234);
        assertThat(((Map<?, ?>) event.toPayload().get("target")).get("playerPid")).isEqualTo(567);
        var gson = new com.google.gson.Gson();
        assertThat(gson.fromJson(gson.toJson(event), ModerationBanCreatedV1.class)).isEqualTo(event);
    }

    @Test
    void unknownPidIsOmittedButNegativePidIsPreserved() {
        var ban = BanData.builder().uuid("target").name("Target").banId(1234).pid(PlayerPids.NONE).build();
        assertThat(ModerationProtocolMapper.toBanCreated(ban, "survival", Instant.now()).target().playerPid()).isNull();
        ban.pid = -7;
        assertThat(ModerationProtocolMapper.toBanCreated(ban, "survival", Instant.now()).target().playerPid()).isEqualTo(-7);
    }
}
