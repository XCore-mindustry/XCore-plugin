package org.xcore.plugin.service.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.DiscordUnlinkCommandV1;
import org.xcore.protocol.generated.shared.ActorRefV1;
import org.xcore.protocol.generated.shared.ActorRefV1ActorType;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordProtocolMapperTest {

    @Test
    @DisplayName("requester actor falls back to system when only name is available")
    void testRequesterActorSystemFallback() {
        ActorRefV1 actor = invokeMapper("toRequesterActor", new Class[]{String.class}, "plugin/unlink");

        assertThat(actor.actorType()).isEqualTo(ActorRefV1ActorType.SYSTEM);
        assertThat(actor.actorType().toString()).isEqualTo("system");
        assertThat(actor.actorDiscordId()).isNull();
        assertThat(actor.actorName()).isEqualTo("plugin/unlink");
    }

    @Test
    @DisplayName("requester actor uses discord type when discord id is available")
    void testRequesterActorWithDiscordId() {
        ActorRefV1 actor = invokeMapper("toRequesterActor", new Class[]{String.class, String.class}, "boss", "12345");

        assertThat(actor.actorType()).isEqualTo(ActorRefV1ActorType.DISCORD);
        assertThat(actor.actorType().toString()).isEqualTo("discord");
        assertThat(actor.actorDiscordId()).isEqualTo("12345");
        assertThat(actor.actorName()).isEqualTo("boss");
    }

    @Test
    @DisplayName("unlink command payload uses canonical actor and target field names")
    void testUnlinkCommandCanonicalFields() {
        DiscordUnlinkCommandV1 command = DiscordProtocolMapper.toUnlinkCommand(
                "uuid-7",
                7,
                "Target",
                "12345",
                "discord-user",
                "requestor",
                "survival",
                1_714_102_400_000L
        );

        Map<String, Object> payload = command.toPayload();

        // Top-level keys: nested objects, no legacy flat keys
        assertThat(payload)
                .containsKeys("player", "discord", "actor", "server", "requestedAt")
                .doesNotContainKeys("uuid", "name", "requestedBy", "requestedByDiscordId", "requestedByType");

        @SuppressWarnings("unchecked")
        var player = (Map<String, Object>) payload.get("player");
        assertThat(player).containsEntry("playerUuid", "uuid-7");
        assertThat(player).containsEntry("playerName", "Target");

        @SuppressWarnings("unchecked")
        var discord = (Map<String, Object>) payload.get("discord");
        assertThat(discord).containsEntry("discordId", "12345");
        assertThat(discord).containsEntry("discordUsername", "discord-user");

        @SuppressWarnings("unchecked")
        var actorPayload = (Map<String, Object>) payload.get("actor");
        assertThat(actorPayload).containsEntry("actorName", "requestor");

        assertThat(payload).containsEntry("server", "survival");
        assertThat(payload.get("requestedAt")).isNotNull();
    }

    @Test
    @DisplayName("unlink command actor overload preserves canonical actor fields")
    void testUnlinkCommandActorOverload() {
        ActorRefV1 actor = new ActorRefV1("DisplayName", "555", ActorRefV1ActorType.DISCORD);

        DiscordUnlinkCommandV1 command = DiscordProtocolMapper.toUnlinkCommand(
                "uuid-7",
                7,
                "Target",
                "12345",
                "discord-user",
                actor,
                "survival",
                1_714_102_400_000L
        );

        assertThat(command.actor()).isEqualTo(actor);
        assertThat(command.actor().actorName()).isEqualTo("DisplayName");
        assertThat(command.actor().actorDiscordId()).isEqualTo("555");
        assertThat(command.actor().actorType()).isEqualTo(ActorRefV1ActorType.DISCORD);
    }

    @SuppressWarnings("unchecked")
    private static <T> T invokeMapper(String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = DiscordProtocolMapper.class.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return (T) method.invoke(null, args);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to invoke mapper method: " + methodName, exception);
        }
    }
}
