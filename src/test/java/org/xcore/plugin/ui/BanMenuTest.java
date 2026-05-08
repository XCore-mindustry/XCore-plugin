package org.xcore.plugin.ui;

import com.ospx.flubundle.Bundle;
import jakarta.inject.Provider;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.BanData;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.TimeService;
import org.xcore.plugin.service.moderation.ModerationResult;
import org.xcore.plugin.service.moderation.ModerationService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.flow.MenuMode;
import org.xcore.plugin.ui.menu.BanMenu;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BanMenuTest {

    private SessionService sessionService;
    private ModerationService moderationService;
    private TimeService timeService;
    private MindustryMenuGateway gateway;
    private MenuService menuService;
    private BanMenu banMenu;
    private Session session;
    private Player target;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        sessionService = mock(SessionService.class);
        moderationService = mock(ModerationService.class);
        timeService = mock(TimeService.class);
        gateway = mock(MindustryMenuGateway.class);

        Provider<SessionService> sessionProvider = mock(Provider.class);
        when(sessionProvider.get()).thenReturn(sessionService);
        menuService = new MenuService(sessionProvider, gateway);
        banMenu = new BanMenu(new Config(), new GlobalConfig(), sessionService, moderationService, timeService);

        session = session("admin-uuid", 1);
        target = target("target-uuid");

        PlayerData targetData = new PlayerData("target-uuid", false);
        targetData.uuid = "target-uuid";
        targetData.pid = 42;

        when(sessionService.get(session.player)).thenReturn(session);
        when(sessionService.getOrLoadFromDb("target-uuid")).thenReturn(targetData);
        when(timeService.parsePeriod("2d", java.util.concurrent.TimeUnit.DAYS)).thenReturn(Instant.ofEpochMilli(Duration.ofDays(2).toMillis()));
    }

    @Test
    @DisplayName("open starts duration prompt through MenuService and does not set textHandler")
    void open_startsDurationPromptThroughMenuService() {
        banMenu.open(session.player, target);

        assertThat(session.activePrompt()).isNotNull();
        assertThat(session.textHandler).isNull();
        verify(gateway).textInput(eq(session.player), eq(0), eq("ban-menu-duration-title"), eq("ban-menu-duration-message"), eq(64), eq("1d"), eq(false));
    }

    @Test
    @DisplayName("duration submit opens reason prompt and clears stale handler")
    void durationSubmit_opensReasonPrompt() {
        banMenu.open(session.player, target);

        menuService.onTextInput(session, "2d");

        assertThat(session.activePrompt()).isNotNull();
        assertThat(session.textHandler).isNull();
        verify(gateway).textInput(eq(session.player), eq(0), eq("ban-menu-reason-title"), eq("ban-menu-reason-message"), eq(256), eq(""), eq(false));
    }

    @Test
    @DisplayName("invalid duration reopens duration prompt without stale textHandler")
    void durationSubmit_invalidDurationReopensDurationPrompt() {
        banMenu.open(session.player, target);

        menuService.onTextInput(session, "bad");

        verify(session.localization).send(eq("error-wrong-period-format"), anyMap());
        assertThat(session.activePrompt()).isNotNull();
        assertThat(session.textHandler).isNull();
        verify(gateway, times(2)).textInput(eq(session.player), eq(0), eq("ban-menu-duration-title"), eq("ban-menu-duration-message"), eq(64), eq("1d"), eq(false));
    }

    @Test
    @DisplayName("duration cancel clears draft and does not call moderation service")
    void durationCancel_clearsDraft() {
        banMenu.open(session.player, target);

        menuService.onTextInput(session, null);

        assertThat(session.activePrompt()).isNull();
        assertThat(session.textHandler).isNull();
        assertThat(session.hasDraft(draftClass())).isFalse();
        verify(moderationService, never()).banById(eq(42), anyString(), anyString(), anyString(), eq(Duration.ofDays(2)), eq(true));
        verify(session.localization).send(eq("ban-cancelled"), anyMap());
    }

    @Test
    @DisplayName("reason submit opens confirmation follow-up screen with no active prompt")
    void reasonSubmit_opensConfirmationScreen() {
        banMenu.open(session.player, target);
        menuService.onTextInput(session, "2d");

        menuService.onTextInput(session, "griefing");

        assertThat(session.activePrompt()).isNull();
        assertThat(session.textHandler).isNull();
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().mode()).isEqualTo(MenuMode.FOLLOW_UP);
        verify(gateway).followUpMenu(eq(session.player), eq(0), eq("ban-menu-confirm-title"), anyString(), any());
    }

    @Test
    @DisplayName("reason cancel returns to duration prompt without stale textHandler")
    void reasonCancel_returnsToDurationPrompt() {
        banMenu.open(session.player, target);
        menuService.onTextInput(session, "2d");

        menuService.onTextInput(session, null);

        assertThat(session.activePrompt()).isNotNull();
        assertThat(session.textHandler).isNull();
        verify(gateway).textInput(eq(session.player), eq(0), eq("ban-menu-duration-title"), eq("ban-menu-duration-message"), eq(64), eq("2d"), eq(false));
    }

    @Test
    @DisplayName("confirm action applies ban through moderation service and clears draft")
    void confirmAction_appliesBan() {
        BanData ban = new BanData();
        ban.name = "Target";
        when(moderationService.banById(eq(42), eq(session.player.name), eq(session.data.discordId), eq("griefing"), eq(Duration.ofDays(2)), eq(true)))
                .thenReturn(ModerationResult.success(ban));

        banMenu.open(session.player, target);
        menuService.onTextInput(session, "2d");
        menuService.onTextInput(session, "griefing");

        menuService.onMenuOption(session, 0);

        verify(moderationService).banById(42, session.player.name, session.data.discordId, "griefing", Duration.ofDays(2), true);
        assertThat(session.hasDraft(draftClass())).isFalse();
        verify(sessionService).broadcast(eq("tempban-player-banned"), anyMap());
        verify(session.localization).send(eq("commands-ban-success"), anyMap());
        assertThat(session.activeScreen()).isNull();
        verify(gateway).hideFollowUpMenu(eq(session.player), eq(0));
    }

    @Test
    @DisplayName("confirm cancel clears draft, hides follow-up, and clears UI state")
    void confirmCancel_clearsDraftAndHidesFollowUp() {
        banMenu.open(session.player, target);
        menuService.onTextInput(session, "2d");
        menuService.onTextInput(session, "griefing");

        menuService.onMenuOption(session, 1);

        assertThat(session.hasDraft(draftClass())).isFalse();
        assertThat(session.activeScreen()).isNull();
        assertThat(session.activePrompt()).isNull();
        assertThat(session.textHandler).isNull();
        assertThat(session.actions).isEmpty();
        verify(gateway).hideFollowUpMenu(eq(session.player), eq(0));
        verify(session.localization).send(eq("ban-cancelled"), anyMap());
    }

    @Test
    @DisplayName("confirm dismiss -1 hides follow-up and clears UI state")
    void confirmDismiss_hidesFollowUpAndClearsState() {
        banMenu.open(session.player, target);
        menuService.onTextInput(session, "2d");
        menuService.onTextInput(session, "griefing");

        menuService.onMenuOption(session, -1);

        assertThat(session.activeScreen()).isNull();
        assertThat(session.activePrompt()).isNull();
        assertThat(session.textHandler).isNull();
        assertThat(session.actions).isEmpty();
        verify(gateway).hideFollowUpMenu(eq(session.player), eq(0));
        assertThat(session.hasDraft(draftClass())).isTrue();
    }

    private Session session(String uuid, int pid) {
        Player player = Player.create();
        player.con = mock(NetConnection.class);
        player.name = "Admin";

        PlayerData data = new PlayerData(uuid, true);
        data.uuid = uuid;
        data.pid = pid;
        data.discordId = "discord-admin";

        Session session = new Session(new GlobalConfig(), mock(Bundle.class), menuService, mock(PlayerDataRepository.class), player, data);
        Localization localization = mock(Localization.class);
        when(localization.t(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.t(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.format(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.format(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.getLocale()).thenReturn(Locale.US);
        session.localization = localization;
        return session;
    }

    private Player target(String uuid) {
        Player player = mock(Player.class);
        when(player.uuid()).thenReturn(uuid);
        when(player.coloredName()).thenReturn("[scarlet]Target");
        when(player.plainName()).thenReturn("Target");
        return player;
    }

    @SuppressWarnings("unchecked")
    private static Class<Object> draftClass() {
        try {
            return (Class<Object>) Class.forName("org.xcore.plugin.ui.menu.BanMenu$BanDraft");
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }
}
