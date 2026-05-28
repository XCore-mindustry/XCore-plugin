package org.xcore.plugin.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.player.Badge;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.protocol.generated.messages.chat.ChatMessages.PlayerCustomNicknameChangedCommandV1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerProfileSettingsServiceTest {

    @Test
    @DisplayName("validateCustomNickname accepts blank input as reset")
    void validateCustomNickname_acceptsBlankAsReset() {
        var service = service();

        assertThat(service.validateCustomNickname("").valid()).isTrue();
        assertThat(service.validateCustomNickname("   ").valid()).isTrue();
        assertThat(service.validateCustomNickname(null)).satisfies(r -> {
            assertThat(r.valid()).isTrue();
            assertThat(r.errorKey()).isNull();
        });
    }

    @Test
    @DisplayName("validateCustomNickname accepts valid plain nickname")
    void validateCustomNickname_acceptsValidPlainNickname() {
        var service = service();

        assertThat(service.validateCustomNickname("Hello").valid()).isTrue();
        assertThat(service.validateCustomNickname("[green]Colored[]").valid()).isTrue();
    }

    @Test
    @DisplayName("validateCustomNickname rejects too-long plain UTF-8 nickname")
    void validateCustomNickname_rejectsTooLongUtf8() {
        var service = service();
        String tooLong = "a".repeat(41);

        var result = service.validateCustomNickname(tooLong);

        assertThat(result.valid()).isFalse();
        assertThat(result.errorKey()).isEqualTo("error-nickname-too-long");
        assertThat(result.maxBytes()).isEqualTo(40);
    }

    @Test
    @DisplayName("validateCustomNickname rejects actual reserved badge glyphs")
    void validateCustomNickname_rejectsActualReservedBadgeGlyphs() {
        var service = service();
        String withGlyph = "Player" + Badge.DEVELOPER.glyph();

        var result = service.validateCustomNickname(withGlyph);

        assertThat(result.valid()).isFalse();
        assertThat(result.errorKey()).isEqualTo("error-nickname-badge-glyph");
    }

    @Test
    @DisplayName("validateCustomNickname rejects system badge glyphs too")
    void validateCustomNickname_rejectsSystemBadgeGlyphsToo() {
        var service = service();
        String withGlyph = "Player" + Badge.ADMIN.glyph();

        var result = service.validateCustomNickname(withGlyph);

        assertThat(result.valid()).isFalse();
        assertThat(result.errorKey()).isEqualTo("error-nickname-badge-glyph");
    }

    @Test
    @DisplayName("validateCustomNickname accepts private-use glyphs that are not reserved badges")
    void validateCustomNickname_acceptsPrivateUseGlyphsThatAreNotReservedBadges() {
        var service = service();
        int nonReservedGlyph = Badge.values()[0].glyph() + 1;
        String withGlyph = "Player" + Character.toString(nonReservedGlyph);

        assertThat(service.validateCustomNickname(withGlyph).valid()).isTrue();
    }

    @Test
    @DisplayName("validateCustomNickname accepts crossed swords symbol")
    void validateCustomNickname_acceptsCrossedSwordsSymbol() {
        var service = service();

        assertThat(service.validateCustomNickname("Player⚔").valid()).isTrue();
    }

    @Test
    @DisplayName("updateCustomNickname for online session mutates session data and refreshes display and posts sync")
    void updateCustomNickname_onlineSession_mutatesAndRefreshesAndSyncs() {
        SessionService sessionService = mock(SessionService.class);
        PlayerDataRepository repository = mock(PlayerDataRepository.class);
        PlayerDisplayService displayService = mock(PlayerDisplayService.class);
        NetworkService network = mock(NetworkService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";

        PlayerProfileSettingsService service = new PlayerProfileSettingsService(
                sessionService, repository, displayService, network, config);

        PlayerData targetData = new PlayerData("uuid-1", true);
        targetData.customNickname = "old";

        Session onlineSession = mock(Session.class);
        onlineSession.data = targetData;
        when(sessionService.get("uuid-1")).thenReturn(onlineSession);
        when(repository.updateCustomNickname("uuid-1", "new")).thenReturn(true);

        service.updateCustomNickname(targetData, "new", true, true);

        assertThat(onlineSession.data.customNickname).isEqualTo("new");
        verify(repository).updateCustomNickname("uuid-1", "new");
        verify(displayService).refresh(onlineSession);
        verify(network).post(any(PlayerCustomNicknameChangedCommandV1.class));
    }

    @Test
    @DisplayName("updateCustomNickname for offline target mutates data and posts sync without display refresh")
    void updateCustomNickname_offlineTarget_mutatesAndSyncsWithoutRefresh() {
        SessionService sessionService = mock(SessionService.class);
        PlayerDataRepository repository = mock(PlayerDataRepository.class);
        PlayerDisplayService displayService = mock(PlayerDisplayService.class);
        NetworkService network = mock(NetworkService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";

        PlayerProfileSettingsService service = new PlayerProfileSettingsService(
                sessionService, repository, displayService, network, config);

        PlayerData targetData = new PlayerData("uuid-1", true);
        targetData.customNickname = "old";
        when(sessionService.get("uuid-1")).thenReturn(null);
        when(repository.updateCustomNickname("uuid-1", "new")).thenReturn(true);

        service.updateCustomNickname(targetData, "new", true, true);

        assertThat(targetData.customNickname).isEqualTo("new");
        verify(repository).updateCustomNickname("uuid-1", "new");
        verify(displayService, never()).refresh(any());
        verify(network).post(any(PlayerCustomNicknameChangedCommandV1.class));
    }

    @Test
    @DisplayName("updateCustomNickname for online session with sync=false does not post network event")
    void updateCustomNickname_onlineSession_noSync_noNetworkPost() {
        SessionService sessionService = mock(SessionService.class);
        PlayerDataRepository repository = mock(PlayerDataRepository.class);
        PlayerDisplayService displayService = mock(PlayerDisplayService.class);
        NetworkService network = mock(NetworkService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";

        PlayerProfileSettingsService service = new PlayerProfileSettingsService(
                sessionService, repository, displayService, network, config);

        PlayerData targetData = new PlayerData("uuid-1", true);
        Session onlineSession = mock(Session.class);
        onlineSession.data = targetData;
        when(sessionService.get("uuid-1")).thenReturn(onlineSession);

        service.updateCustomNickname(targetData, "new", false, false);

        verify(displayService, never()).refresh(any());
        verify(network, never()).post(any());
    }

    @Test
    @DisplayName("updateLanguage for online session delegates to session helper")
    void updateLanguage_onlineSession_delegatesToSessionHelper() {
        SessionService sessionService = mock(SessionService.class);
        PlayerDataRepository repository = mock(PlayerDataRepository.class);
        PlayerDisplayService displayService = mock(PlayerDisplayService.class);
        NetworkService network = mock(NetworkService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();

        PlayerProfileSettingsService service = new PlayerProfileSettingsService(
                sessionService, repository, displayService, network, config);

        PlayerData targetData = new PlayerData("uuid-1", true);
        Session onlineSession = mock(Session.class);
        onlineSession.data = targetData;
        when(sessionService.get("uuid-1")).thenReturn(onlineSession);
        when(onlineSession.updateLanguage("uk_UA")).thenReturn(true);

        service.updateLanguage(targetData, "uk_UA");

        verify(onlineSession).updateLanguage("uk_UA");
        verify(repository, never()).updateLanguage(any(), any());
    }

    @Test
    @DisplayName("updateLanguage for offline target mutates data and calls repository")
    void updateLanguage_offlineTarget_mutatesAndCallsRepository() {
        SessionService sessionService = mock(SessionService.class);
        PlayerDataRepository repository = mock(PlayerDataRepository.class);
        PlayerDisplayService displayService = mock(PlayerDisplayService.class);
        NetworkService network = mock(NetworkService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();

        PlayerProfileSettingsService service = new PlayerProfileSettingsService(
                sessionService, repository, displayService, network, config);

        PlayerData targetData = new PlayerData("uuid-1", true);
        targetData.language = "auto";
        when(sessionService.get("uuid-1")).thenReturn(null);
        when(repository.updateLanguage("uuid-1", "uk_UA")).thenReturn(true);

        service.updateLanguage(targetData, "uk_UA");

        assertThat(targetData.language).isEqualTo("uk_UA");
        verify(repository).updateLanguage("uuid-1", "uk_UA");
    }

    @Test
    @DisplayName("updateDescription for online session mutates session data and calls repository")
    void updateDescription_onlineSession_mutatesAndCallsRepository() {
        SessionService sessionService = mock(SessionService.class);
        PlayerDataRepository repository = mock(PlayerDataRepository.class);
        PlayerDisplayService displayService = mock(PlayerDisplayService.class);
        NetworkService network = mock(NetworkService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();

        PlayerProfileSettingsService service = new PlayerProfileSettingsService(
                sessionService, repository, displayService, network, config);

        PlayerData targetData = new PlayerData("uuid-1", true);
        Session onlineSession = mock(Session.class);
        onlineSession.data = targetData;
        when(sessionService.get("uuid-1")).thenReturn(onlineSession);

        service.updateDescription(targetData, "new desc");

        assertThat(onlineSession.data.description).isEqualTo("new desc");
        verify(repository).updateDescription("uuid-1", "new desc");
    }

    @Test
    @DisplayName("updateDescription for offline target mutates provided data and calls repository")
    void updateDescription_offlineTarget_mutatesAndCallsRepository() {
        SessionService sessionService = mock(SessionService.class);
        PlayerDataRepository repository = mock(PlayerDataRepository.class);
        PlayerDisplayService displayService = mock(PlayerDisplayService.class);
        NetworkService network = mock(NetworkService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();

        PlayerProfileSettingsService service = new PlayerProfileSettingsService(
                sessionService, repository, displayService, network, config);

        PlayerData targetData = new PlayerData("uuid-1", true);
        when(sessionService.get("uuid-1")).thenReturn(null);

        service.updateDescription(targetData, "new desc");

        assertThat(targetData.description).isEqualTo("new desc");
        verify(repository).updateDescription("uuid-1", "new desc");
    }

    @Test
    @DisplayName("updateLeaderboard for online session mutates session data and calls repository")
    void updateLeaderboard_onlineSession_mutatesAndCallsRepository() {
        SessionService sessionService = mock(SessionService.class);
        PlayerDataRepository repository = mock(PlayerDataRepository.class);
        PlayerDisplayService displayService = mock(PlayerDisplayService.class);
        NetworkService network = mock(NetworkService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();

        PlayerProfileSettingsService service = new PlayerProfileSettingsService(
                sessionService, repository, displayService, network, config);

        PlayerData targetData = new PlayerData("uuid-1", true);
        targetData.leaderboard = false;
        Session onlineSession = mock(Session.class);
        onlineSession.data = targetData;
        when(sessionService.get("uuid-1")).thenReturn(onlineSession);

        service.updateLeaderboard(targetData, true);

        assertThat(onlineSession.data.leaderboard).isTrue();
        verify(repository).updateLeaderboard("uuid-1", true);
    }

    @Test
    @DisplayName("updateActiveBadge for online session mutates session data and refreshes display and posts sync")
    void updateActiveBadge_onlineSession_mutatesAndRefreshesAndSyncs() {
        SessionService sessionService = mock(SessionService.class);
        PlayerDataRepository repository = mock(PlayerDataRepository.class);
        PlayerDisplayService displayService = mock(PlayerDisplayService.class);
        NetworkService network = mock(NetworkService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";

        PlayerProfileSettingsService service = new PlayerProfileSettingsService(
                sessionService, repository, displayService, network, config);

        PlayerData targetData = new PlayerData("uuid-1", true);
        Session onlineSession = mock(Session.class);
        onlineSession.data = targetData;
        when(sessionService.get("uuid-1")).thenReturn(onlineSession);

        service.updateActiveBadge(targetData, "developer", true, true);

        assertThat(onlineSession.data.activeBadge).isEqualTo("developer");
        verify(repository).setActiveBadge("uuid-1", "developer");
        verify(displayService).refresh(onlineSession);
        verify(network).post(any(org.xcore.protocol.generated.messages.chat.ChatMessages.PlayerActiveBadgeChangedCommandV1.class));
    }

    @Test
    @DisplayName("updateBadgeSymbolColorMode for offline target mutates data and calls repository")
    void updateBadgeSymbolColorMode_offlineTarget_mutatesAndCallsRepository() {
        SessionService sessionService = mock(SessionService.class);
        PlayerDataRepository repository = mock(PlayerDataRepository.class);
        PlayerDisplayService displayService = mock(PlayerDisplayService.class);
        NetworkService network = mock(NetworkService.class);
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";

        PlayerProfileSettingsService service = new PlayerProfileSettingsService(
                sessionService, repository, displayService, network, config);

        PlayerData targetData = new PlayerData("uuid-1", true);
        when(sessionService.get("uuid-1")).thenReturn(null);

        service.updateBadgeSymbolColorMode(targetData, "player-color", false, false);

        assertThat(targetData.badgeSymbolColorMode).isEqualTo("player-color");
        verify(repository).updateBadgeSymbolColorMode("uuid-1", "player-color");
        verify(displayService, never()).refresh(any());
        verify(network, never()).post(any());
    }

    private PlayerProfileSettingsService service() {
        return new PlayerProfileSettingsService(
                mock(SessionService.class),
                mock(PlayerDataRepository.class),
                mock(PlayerDisplayService.class),
                mock(NetworkService.class),
                new TomlXcoreConfig());
    }
}
