package org.xcore.plugin.command.controller.client;

import com.ospx.flubundle.Bundle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.gamemode.hexed.HexMember;
import org.xcore.plugin.gamemode.hexed.MiniHexedService;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.service.GameDataService;
import org.xcore.plugin.service.LeaderboardService;
import org.xcore.plugin.service.MapStatsService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.service.TopMenuCacheService;
import org.xcore.plugin.session.ObserverService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HexedControllerObserverTest {

    @Test
    @DisplayName("ai blocks observing players through observer service state")
    void ai_blocksObservingPlayersThroughObserverServiceState() {
        SessionService sessionService = mock(SessionService.class);
        ObserverService observerService = mock(ObserverService.class);
        MiniHexedService hexedService = new MiniHexedService(
                mock(Config.class),
                sessionService,
                mock(PlayerDataRepository.class),
                mock(NetworkService.class),
                mock(Bundle.class),
                mock(LeaderboardService.class),
                mock(PlayerDisplayService.class),
                mock(GameDataService.class),
                mock(MapStatsService.class),
                mock(TopMenuCacheService.class),
                observerService
        );
        HexedController controller = new HexedController(sessionService, hexedService, observerService);

        XCoreSender sender = mock(XCoreSender.class);
        var player = mock(mindustry.gen.Player.class);
        Session session = mock(Session.class);
        Localization localization = mock(Localization.class);
        HexMember member = mock(HexMember.class);

        session.player = player;
        session.data = new PlayerData("uuid-1", true);
        hexedService.members.put("uuid-1", member);

        when(sender.player()).thenReturn(player);
        when(player.uuid()).thenReturn("uuid-1");
        when(sessionService.get("uuid-1")).thenReturn(session);
        when(session.locale()).thenReturn(localization);
        when(observerService.isObserving(session)).thenReturn(true);

        controller.ai(sender, "attack");

        verify(localization).send("error-spectator", java.util.Map.of());
        verifyNoInteractions(member);
    }
}
