package org.xcore.plugin.vote;

import arc.Application;
import arc.Core;
import arc.files.Fi;
import arc.func.Cons;
import com.ospx.flubundle.Bundle;
import mindustry.gen.Player;
import mindustry.net.Packets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.session.SessionService;
import org.xcore.protocol.generated.messages.server.ServerMessages.ServerActionV1;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VoteKickTest {
    private Application originalApp;
    private Cons<Player> originalHook;
    private VoteService service;

    @BeforeEach
    void setUp() {
        originalApp = Core.app;
        originalHook = VoteKick.onKick;
        Core.app = mock(Application.class);
        service = new VoteService();
    }

    @AfterEach
    void tearDown() {
        service.stop();
        VoteKick.setOnKick(originalHook);
        Core.app = originalApp;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void successExplainsDisconnectionOnlyWhenTargetDisconnects(boolean disconnected) {
        var bundle = new Bundle(Locale.ENGLISH);
        bundle.addSource(new Fi("src/main/resources/bundles"));
        var sessions = mock(SessionService.class);
        var network = mock(NetworkService.class);
        var starter = mock(Player.class);
        starter.id = 1;
        when(starter.uuid()).thenReturn("starter-uuid");
        when(starter.plainName()).thenReturn("Starter");
        var target = mock(Player.class);
        target.id = 2;
        when(target.uuid()).thenReturn("target-uuid");
        when(target.coloredName()).thenReturn("Target");
        when(target.plainName()).thenReturn("Target");
        var config = new TomlSecretsConfig();
        config.moderation.votekick.voteDurationSeconds = 10_000f;
        var vote = new VoteKick(starter, target, "test", bundle, sessions, network,
                service, new TomlXcoreConfig(), config);
        var rolledBack = new java.util.ArrayList<Player>();
        VoteKick.setOnKick(rolledBack::add);
        service.startVote(vote);
        vote.voted.put(starter.id, 1);

        if (disconnected) {
            service.handleLeave(target);
        } else {
            vote.success();
        }

        String key = disconnected ? "votekick-success-disconnected" : "votekick-success";
        var args = Bundle.args("target", "Target", "minutes", config.moderation.votekick.banDurationMinutes);
        verify(sessions).broadcast(eq(key), eq(args));
        verify(sessions, never()).broadcast(eq(disconnected ? "votekick-success" : "votekick-success-disconnected"), anyMap());
        verify(network).post(new ServerActionV1(bundle.format(Locale.ENGLISH, key, args), new TomlXcoreConfig().server.name));
        verify(target).kick(Packets.KickReason.vote, (long) config.moderation.votekick.banDurationMinutes * 60_000);
        assertThat(rolledBack).containsExactly(target);
        assertThat(service.isVoting()).isFalse();
        assertThat(vote.end.isActive()).isFalse();

        for (Locale locale : bundle.getAvailableLocales()) {
            assertThat(bundle.keys(locale)).contains(key);
            assertThat(bundle.format(locale, key, args)).contains("Target", "30");
        }
        assertThat(bundle.format(Locale.of("ru"), key, args).contains("игрок отключился во время голосования"))
                .isEqualTo(disconnected);
    }
}
