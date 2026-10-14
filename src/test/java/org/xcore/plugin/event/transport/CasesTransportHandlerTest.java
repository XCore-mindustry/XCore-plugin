package org.xcore.plugin.event.transport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.cases.*;
import org.xcore.plugin.concurrent.GameThread;
import org.xcore.protocol.generated.messages.cases.CasesMessages.*;
import org.xcore.protocol.generated.shared.CaseChatLineV1Role;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CasesTransportHandlerTest {
    private final CasesWorld world = new CasesWorld();
    private final CaseNoticeService notices = mock(CaseNoticeService.class);
    private final CasesTransportHandler handler = new CasesTransportHandler(world.network, world.config, world.secrets,
            world.feature, notices, world.evidence, world.loop.async);

    @BeforeEach
    void thread() {
        GameThread.setGameThreadOverride(Thread.currentThread());
    }

    @AfterEach
    void reset() {
        GameThread.setGameThreadOverride(null);
        mindustry.gen.Groups.clear();
    }

    @Test
    void wakeUpFiltersServerAndHopsToGameThread() {
        handler.notice(new CasesNoticeCommandV1("elsewhere", "n", "player", null));
        verifyNoInteractions(notices);
        handler.notice(new CasesNoticeCommandV1("survival", "n", "player", null));
        verifyNoInteractions(notices);
        world.loop.main.runTurn();
        verify(notices).wakeUp("player", "n");
    }

    @Test
    void evidenceAnswersOnlineOfflineAndUnknownWithWorkerPublication() throws Exception {
        var player = world.session("player", 1, "private-address");
        world.chat.record(player.player, "evidence", true);
        doAnswer(invocation -> {
            assertThat(GameThread.isGameThread()).isFalse();
            return null;
        }).when(world.network).respond(any(), any());
        var online = new CasesEvidenceRequestV1("survival", "player", 30, 600);
        handler.evidence(online);
        world.loop.drain();
        var messages = org.mockito.ArgumentCaptor.forClass(CasesEvidenceResponseV1.class);
        verify(world.network).respond(eq(online), messages.capture());
        assertThat(messages.getValue().targetOnline()).isTrue();
        assertThat(messages.getValue().chat()).hasSize(1);
        assertThat(messages.getValue().chat().getFirst().role()).isEqualTo(CaseChatLineV1Role.TARGET);
        world.recent.left(player, false);
        mindustry.gen.Groups.player.remove(player.player);
        handler.evidence(new CasesEvidenceRequestV1("survival", "player", null, null));
        world.loop.drain();
        handler.evidence(new CasesEvidenceRequestV1("survival", "unknown", null, null));
        world.loop.drain();
        verify(world.network, times(3)).respond(any(), messages.capture());
        List<CasesEvidenceResponseV1> replies = messages.getAllValues();
        assertThat(replies.get(replies.size() - 2).targetOnline()).isFalse();
        assertThat(replies.get(replies.size() - 2).chat()).hasSize(1);
        assertThat(replies.getLast().chat()).isEmpty();
        assertThat(replies.getLast().blockSummary()).isNull();
        assertThat(replies.getLast().toPayload().toString()).doesNotContain("private-address", "playerUuid");
    }

    @Test
    void excessiveRequestValuesAreClampedBeforeNativeRead() throws Exception {
        var player = world.session("player", 1, "private");
        world.history.register((uuid, window) -> {
            assertThat(GameThread.isGameThread()).isTrue();
            assertThat(window).isEqualTo(1800);
            return java.util.Optional.empty();
        });
        handler.evidence(new CasesEvidenceRequestV1("survival", "player", 1000, Integer.MAX_VALUE));
        world.loop.drain();
        verify(world.network).respond(any(), any());
    }

    @Test
    void disabledFeatureDoesNotSubscribeAndQueuedWakeUpHasNoEffect() {
        world.secrets.cases.reports.enabled = false;
        handler.registerListeners();
        handler.notice(new CasesNoticeCommandV1("survival", "n", "player", null));
        verifyNoInteractions(world.network, notices);
        world.secrets.cases.reports.enabled = true;
        handler.notice(new CasesNoticeCommandV1("survival", "n", "player", null));
        world.config.runtime.disabledFeatures.add("reports");
        world.loop.main.runTurn();
        verifyNoInteractions(notices);
    }

    @Test
    void disabledRequestRejectsAndOtherServerRequestsAreIgnored() {
        var request = new CasesEvidenceRequestV1("survival", "player", null, null);
        world.secrets.cases.reports.enabled = false;
        handler.evidence(new CasesEvidenceRequestV1("elsewhere", "player", null, null));
        verifyNoInteractions(world.network);
        handler.evidence(request);
        verify(world.network).respondError(request, "REJECTED", "Reports are disabled");
    }

    @Test
    void captureFailureAnswersFailedOffGameThread() throws Exception {
        var broken = mock(CaseEvidenceService.class);
        when(broken.capture(anyString(), isNull(), anyInt(), anyInt(), anyInt())).thenThrow(new IllegalStateException("broken"));
        var testHandler = new CasesTransportHandler(world.network, world.config, world.secrets, world.feature,
                notices, broken, world.loop.async);
        var request = new CasesEvidenceRequestV1("survival", "player", null, null);
        doAnswer(invocation -> {
            assertThat(GameThread.isGameThread()).isFalse();
            return null;
        }).when(world.network).respondError(any(), anyString(), anyString());
        testHandler.evidence(request);
        world.loop.drain();
        verify(world.network).respondError(request, "FAILED", "Evidence could not be captured");
    }

    @Test
    void requestQueueIsBoundedAndCapacityReturnsAfterCapture() throws Exception {
        var request = new CasesEvidenceRequestV1("survival", "unknown", null, null);
        for (int i = 0; i < 65; i++) handler.evidence(request);
        verify(world.network).respondError(request, "REJECTED", "Evidence capture is busy");
        world.loop.drain();
        verify(world.network, times(64)).respond(eq(request), any());
        handler.evidence(request);
        world.loop.drain();
        verify(world.network, times(65)).respond(eq(request), any());
    }

    @Test
    void reconnectReplacesSubscriptionsAndShutdownDisablesQueuedCallback() {
        var first = mock(org.xcore.plugin.service.network.RedisNetworkBackend.Subscription.class);
        var second = mock(org.xcore.plugin.service.network.RedisNetworkBackend.Subscription.class);
        when(world.network.subscribe(eq(CasesNoticeCommandV1.class), any())).thenReturn(first);
        when(world.network.subscribe(eq(CasesEvidenceRequestV1.class), any())).thenReturn(second);
        handler.registerListeners();
        handler.registerListeners();
        verify(first).unsubscribe();
        verify(second).unsubscribe();
        handler.notice(new CasesNoticeCommandV1("survival", "n", "player", null));
        handler.stop();
        world.loop.main.runTurn();
        verifyNoInteractions(notices);
        verify(first, times(2)).unsubscribe();
        verify(second, times(2)).unsubscribe();
    }
}
