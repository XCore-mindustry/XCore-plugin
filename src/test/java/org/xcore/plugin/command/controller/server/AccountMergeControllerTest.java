package org.xcore.plugin.command.controller.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.model.AuditActorType;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.AccountMergeService;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountMergeControllerTest {

    @Test
    @DisplayName("mergePlayer dispatches async merge request with console actor")
    void mergePlayer_dispatchesAsyncMergeRequest() {
        AccountMergeService service = mock(AccountMergeService.class);
        PlayerData source = new PlayerData("uuid-1", true);
        source.pid = 1;
        source.nickname = "Source";
        PlayerData target = new PlayerData("uuid-2", true);
        target.pid = 2;
        target.nickname = "Target";

        var dummyResult = new AccountMergeService.MergeResult(
                true, "Success", source, target, target, 3, false, false
        );
        when(service.mergeAsync(any())).thenReturn(CompletableFuture.completedFuture(dummyResult));

        AccountMergeController controller = new AccountMergeController(service);
        XCoreSender sender = mock(XCoreSender.class);

        controller.mergePlayer(sender, "1", "2", "Admin request");

        ArgumentCaptor<AccountMergeService.MergeRequest> captor = ArgumentCaptor.forClass(AccountMergeService.MergeRequest.class);
        verify(service).mergeAsync(captor.capture());

        var req = captor.getValue();
        assertThat(req.sourceIdentifier()).isEqualTo("1");
        assertThat(req.targetIdentifier()).isEqualTo("2");
        assertThat(req.reason()).isEqualTo("Admin request");
        assertThat(req.actor().type).isEqualTo(AuditActorType.SERVER_CONSOLE);
    }

    @Test
    @DisplayName("mergeAccount alias delegates to same merge logic")
    void mergeAccount_delegatesToMergeLogic() {
        AccountMergeService service = mock(AccountMergeService.class);
        when(service.mergeAsync(any())).thenReturn(CompletableFuture.completedFuture(AccountMergeService.MergeResult.failure("Failed")));

        AccountMergeController controller = new AccountMergeController(service);
        XCoreSender sender = mock(XCoreSender.class);

        controller.mergeAccount(sender, "old-player", "new-player", "Lost access");

        ArgumentCaptor<AccountMergeService.MergeRequest> captor = ArgumentCaptor.forClass(AccountMergeService.MergeRequest.class);
        verify(service).mergeAsync(captor.capture());

        var req = captor.getValue();
        assertThat(req.sourceIdentifier()).isEqualTo("old-player");
        assertThat(req.targetIdentifier()).isEqualTo("new-player");
        assertThat(req.reason()).isEqualTo("Lost access");
    }
}
