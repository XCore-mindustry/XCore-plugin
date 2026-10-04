package org.xcore.plugin.rating.prize;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.StorageExecutor;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.rating.season.PrizeKind;
import org.xcore.plugin.rating.season.SeasonException;
import org.xcore.plugin.rating.season.SeasonPrize;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.session.SessionService;
import org.xcore.protocol.generated.messages.identity.IdentityMessages.PlayerBadgeInventoryChangedCommandV1;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BadgePrizeHandlerTest {
    private final PlayerDataRepository players = mock(PlayerDataRepository.class);
    private final NetworkService network = mock(NetworkService.class);
    private final SessionService sessions = mock(SessionService.class);
    private BadgePrizeHandler handler;

    @BeforeEach
    void setUp() {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";
        handler = new BadgePrizeHandler(players, network, config, sessions, mock(PlayerDisplayService.class),
                new Async(new StorageExecutor(1), Runnable::run));
    }

    private static PrizeGrant grant(String badge) {
        return new PrizeGrant("minipvp:1:1:ace:0", "minipvp:1", 1, "ace", 0, PrizeKind.BADGE, badge, "",
                PrizeStatus.PENDING, "system", Instant.EPOCH, "");
    }

    @Test
    @DisplayName("a known, non-system badge is a valid prize; anything else is refused at setup")
    void validate() {
        handler.validate(new SeasonPrize(1, 1, PrizeKind.BADGE, "season-champion", ""));
        handler.validate(new SeasonPrize(1, 1, PrizeKind.BADGE, " Veteran ", ""));

        assertThatThrownBy(() -> handler.validate(new SeasonPrize(1, 1, PrizeKind.BADGE, "nope", "")))
                .isInstanceOf(SeasonException.class).hasMessageContaining("nope");
        assertThatThrownBy(() -> handler.validate(new SeasonPrize(1, 1, PrizeKind.BADGE, "admin", "")))
                .isInstanceOf(SeasonException.class).hasMessageContaining("system");
    }

    @Test
    @DisplayName("delivering unlocks the badge and tells every server the new inventory")
    void deliver_unlocksAndBroadcasts() {
        PlayerData ace = PlayerData.builder().uuid("ace").unlockedBadges(new HashSet<>(Set.of("veteran")))
                .activeBadge("veteran").build();
        when(players.findByUuid("ace")).thenReturn(ace);

        PrizeOutcome outcome = handler.deliver(grant("season-champion"));

        assertThat(outcome.status()).isEqualTo(PrizeStatus.GRANTED);
        verify(players).addUnlockedBadge("ace", "season-champion");
        ArgumentCaptor<Object> posted = ArgumentCaptor.forClass(Object.class);
        verify(network).post(posted.capture());
        var command = (PlayerBadgeInventoryChangedCommandV1) posted.getValue();
        assertThat(command.playerUuid()).isEqualTo("ace");
        assertThat(command.activeBadge()).isEqualTo("veteran");
        assertThat(command.unlockedBadges()).containsExactlyInAnyOrder("veteran", "season-champion");
        assertThat(command.server()).isEqualTo("mini-pvp");
    }

    @Test
    @DisplayName("a player who is gone, or a badge that no longer exists, fails the grant for good")
    void deliver_permanentFailures() {
        when(players.findByUuid("ace")).thenReturn(null);
        assertThat(handler.deliver(grant("season-champion")).status()).isEqualTo(PrizeStatus.FAILED);

        assertThat(handler.deliver(grant("nope")).status()).isEqualTo(PrizeStatus.FAILED);
        assertThat(handler.deliver(grant("admin")).status()).isEqualTo(PrizeStatus.FAILED);

        verify(players, never()).addUnlockedBadge(any(), any());
        verify(network, never()).post(any());
    }

    @Test
    @DisplayName("a storage error is thrown so the grant is tried again")
    void deliver_transientFailureIsThrown() {
        when(players.findByUuid("ace")).thenReturn(PlayerData.builder().uuid("ace").build());
        when(players.addUnlockedBadge("ace", "season-champion")).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> handler.deliver(grant("season-champion")))
                .isInstanceOf(IllegalStateException.class);
    }
}
