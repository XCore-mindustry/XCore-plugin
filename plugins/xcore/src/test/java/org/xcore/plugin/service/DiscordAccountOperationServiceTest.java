package org.xcore.plugin.service;

import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.AccountOperationRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.network.RedisNetworkBackend;
import org.xcore.plugin.service.network.RedisTransportContracts;
import org.xcore.protocol.generated.messages.discord.DiscordMessages.*;
import org.xcore.protocol.generated.shared.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.xcore.protocol.generated.messages.discord.DiscordAccountOperationRequestV1Action.*;
import static org.xcore.protocol.generated.messages.discord.DiscordAccountOperationResponseV1Status.*;

class DiscordAccountOperationServiceTest {
    final AccountOperationRepository receipts = mock(AccountOperationRepository.class);
    final DiscordLinkService links = mock(DiscordLinkService.class);
    final PlayerDataRepository players = mock(PlayerDataRepository.class);
    final TomlXcoreConfig config = new TomlXcoreConfig();
    final com.google.gson.Gson gson = RedisNetworkBackend.createRedisGson();
    final DiscordAccountOperationService service;
    DiscordAccountOperationServiceTest() {
        config.server.name = "issuer";
        service = new DiscordAccountOperationService(receipts, links, players, config, gson);
        when(receipts.claim(anyString(), anyString())).thenReturn(new AccountOperationRepository.Claim(true, false, null));
        when(receipts.complete(anyString(), anyString(), anyString())).thenReturn(true);
    }
    DiscordAccountOperationRequestV1 request(long issuedAt, long expiresAt) {
        return new DiscordAccountOperationRequestV1("issuer", UUID.randomUUID().toString(), LINK,
                new PlayerRefV1("one", 7, "Player", null), new DiscordIdentityRefV1("123", "Discord"),
                issuedAt, expiresAt, "ABC123", null);
    }
    DiscordAccountOperationRequestV1 request() { long now = System.currentTimeMillis(); return request(now, now + 60_000L); }
    @Test void knownReceiptReplaysWithoutExecutingEvenAfterExpiry() {
        var request = request(1, 2);
        var response = new DiscordAccountOperationResponseV1("issuer", request.operationId(), SUCCESS, null, 50L);
        when(receipts.claim(anyString(), anyString())).thenReturn(new AccountOperationRepository.Claim(false, false, gson.toJson(response.toPayload())));
        assertThat(service.execute(request, "web")).isEqualTo(response);
        verifyNoInteractions(links);
    }
    @Test void duplicateInProgressReturnsUnknownWithoutReexecution() {
        when(receipts.claim(anyString(), anyString())).thenReturn(new AccountOperationRepository.Claim(false, false, null));
        assertThat(service.execute(request(), "web").status()).isEqualTo(UNKNOWN);
        verifyNoInteractions(links);
    }
    @Test void changedIntentCannotReuseId() {
        when(receipts.claim(anyString(), anyString())).thenReturn(new AccountOperationRepository.Claim(false, true, null));
        assertThat(service.execute(request(), "web").reason()).isEqualTo("operation-id-conflict");
        verifyNoInteractions(links);
    }
    @Test void staleRequestNeverExecutes() {
        assertThat(service.execute(request(1, 2), "web").status()).isEqualTo(REJECTED);
        verifyNoInteractions(links);
    }
    @Test void timeoutAfterClaimReturnsUnknownAndDoesNotInventFailure() {
        when(links.confirmLink(anyString(), anyString(), anyInt(), anyString(), anyString(), anyLong()))
                .thenThrow(new java.util.concurrent.CompletionException(new java.util.concurrent.TimeoutException()));
        assertThat(service.execute(request(), "web").status()).isEqualTo(UNKNOWN);
        verify(receipts, never()).complete(anyString(), anyString(), anyString());
    }
    @Test void confirmedMongoWriteReturnsLinkedGenerationAndRecordsResult() {
        when(links.confirmLink(anyString(), anyString(), anyInt(), anyString(), anyString(), anyLong()))
                .thenReturn(DiscordLinkService.ConfirmResult.success(PlayerData.builder().discordLinkedAt(50L).build()));
        var result = service.execute(request(), "web");
        assertThat(result.status()).isEqualTo(SUCCESS);
        assertThat(result.linkedAt()).isEqualTo(50L);
        verify(receipts).complete(anyString(), anyString(), contains("success"));
    }
    @Test void lostReceiptCompletionMakesOutcomeUnknown() {
        when(links.confirmLink(anyString(), anyString(), anyInt(), anyString(), anyString(), anyLong()))
                .thenReturn(DiscordLinkService.ConfirmResult.success(PlayerData.builder().discordLinkedAt(50L).build()));
        when(receipts.complete(anyString(), anyString(), anyString())).thenReturn(false);
        assertThat(service.execute(request(), "web").status()).isEqualTo(UNKNOWN);
    }
    @Test void conditionalFieldsAreValidatedBeforeMutation() {
        var valid = request();
        var missingCode = new DiscordAccountOperationRequestV1(valid.server(), valid.operationId(), LINK,
                valid.player(), valid.discord(), valid.issuedAt(), valid.expiresAt(), null, null);
        var missingGeneration = new DiscordAccountOperationRequestV1(valid.server(), valid.operationId(), UNLINK,
                valid.player(), valid.discord(), valid.issuedAt(), valid.expiresAt(), null, null);
        assertThat(service.execute(missingCode, "web").status()).isEqualTo(REJECTED);
        assertThat(service.execute(missingGeneration, "web").status()).isEqualTo(REJECTED);
        verifyNoInteractions(receipts, links);
    }
    @Test void readOnlyDoesNotClaimBusinessIntent() {
        when(players.isReadOnly()).thenReturn(true);
        assertThat(service.execute(request(), "web").reason()).isEqualTo("read-only");
        verify(receipts, never()).claim(anyString(), anyString());
        verifyNoInteractions(links);
    }
    @Test void readOnlyReplaysKnownReceiptAndUuidCasingCannotCreateSecondIntent() {
        when(players.isReadOnly()).thenReturn(true);
        var original = request();
        var upper = new DiscordAccountOperationRequestV1(original.server(), original.operationId().toUpperCase(java.util.Locale.ROOT),
                original.action(), original.player(), original.discord(), original.issuedAt(), original.expiresAt(), original.code(), null);
        var result = new DiscordAccountOperationResponseV1("issuer", original.operationId(), SUCCESS, null, 50L);
        when(receipts.find(eq(original.operationId()), anyString()))
                .thenReturn(new AccountOperationRepository.Claim(false, false, gson.toJson(result.toPayload())));
        assertThat(service.execute(upper, "web").status()).isEqualTo(SUCCESS);
        verify(receipts, never()).claim(anyString(), anyString());
        verifyNoInteractions(links);
    }

    @Test void replyAllowlistRequiresExactRequesterPair() {
        assertThat(RedisTransportContracts.isAllowedAccountReply("web", "xcore:rpc:resp:web")).isTrue();
        assertThat(RedisTransportContracts.isAllowedAccountReply("discord-bot", "xcore:rpc:resp:discord")).isTrue();
        assertThat(RedisTransportContracts.isAllowedAccountReply("web", "xcore:rpc:resp:discord")).isFalse();
        assertThat(RedisTransportContracts.isAllowedAccountReply("other", "xcore:rpc:resp:other")).isFalse();
    }
    @Test void generatedRouteUsesTargetAndTenSecondTimeout() {
        var route = new org.xcore.plugin.service.network.RedisStreamRouter();
        assertThat(route.route(request(), "another").streamKey()).isEqualTo("xcore:rpc:req:issuer");
        assertThat(route.route(request(), "another").ttlMillis()).isEqualTo(10_000L);
        assertThat(route.responseTypeForRequest(DiscordAccountOperationRequestV1.class)).isEqualTo(DiscordAccountOperationResponseV1.class);
    }
}
