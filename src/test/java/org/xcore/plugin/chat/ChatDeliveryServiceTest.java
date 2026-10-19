package org.xcore.plugin.chat;

import mindustry.game.Team;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.StorageExecutor;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.ChatFormatService;
import org.xcore.plugin.service.ClientCompatibilityService;
import org.xcore.plugin.service.TranslationMetricsService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.translation.ChatTranslation;
import org.xcore.plugin.translation.ChatTranslation.Reason;
import org.xcore.plugin.translation.ChatTranslationService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatDeliveryServiceTest {

    /** Runs the marshalled game-thread work inline. */
    private final Async async = new Async(new StorageExecutor(1), Runnable::run);
    private final TomlXcoreConfig config = new TomlXcoreConfig();
    private final SessionService sessionService = mock(SessionService.class);
    private final ChatFormatService chatFormatService = mock(ChatFormatService.class);
    private final ClientCompatibilityService compatibility = mock(ClientCompatibilityService.class);
    private final ChatTranslationService translations = mock(ChatTranslationService.class);
    private final List<Runnable> scheduled = new ArrayList<>();
    private final Map<String, CompletableFuture<ChatTranslation>> futures = new HashMap<>();
    private long now;
    private Player author;

    @BeforeEach
    void setUp() {
        author = connectedPlayer("author-uuid");
        when(author.plainName()).thenReturn("Author");
        when(chatFormatService.formatChat(any(), anyString())).thenAnswer(invocation -> "<" + invocation.getArgument(1) + ">");
        when(chatFormatService.formatTeamChat(any(), any(), anyString())).thenAnswer(invocation -> "{" + invocation.getArgument(2) + "}");
        when(translations.supports(anyString())).thenReturn(true);
        when(translations.translate(anyString(), anyString(), anyCollection())).thenAnswer(invocation -> {
            String text = invocation.getArgument(1);
            Collection<String> targets = invocation.getArgument(2);
            Map<String, CompletableFuture<ChatTranslation>> results = new LinkedHashMap<>();
            targets.forEach(target -> results.put(target,
                    futures.computeIfAbsent(text + "|" + target, ignored -> new CompletableFuture<>())));
            return results;
        });
    }

    private ChatDeliveryService service() {
        config.translation.normalize();
        return new ChatDeliveryService(config, sessionService, chatFormatService, compatibility, translations,
                mock(TranslationMetricsService.class), async, (delayMillis, task) -> scheduled.add(task), () -> now);
    }

    private void translate(String text, String target, String translation) {
        futures.computeIfAbsent(text + "|" + target, ignored -> new CompletableFuture<>())
                .complete(new ChatTranslation.Translated(translation));
    }

    private void fail(String text, String target) {
        futures.computeIfAbsent(text + "|" + target, ignored -> new CompletableFuture<>())
                .complete(new ChatTranslation.Untranslated(Reason.FAILED));
    }

    /** Moves the clock past the deadline and fires every scheduled deadline check. */
    private void passDeadline() {
        now += TimeUnit.MILLISECONDS.toNanos(config.translation.delivery.lateTranslationMillis / 2);
        List<Runnable> due = new ArrayList<>(scheduled);
        scheduled.clear();
        due.forEach(Runnable::run);
    }

    private static Player connectedPlayer(String uuid) {
        Player player = mock(Player.class);
        when(player.isAdded()).thenReturn(true);
        when(player.uuid()).thenReturn(uuid);
        NetConnection connection = mock(NetConnection.class);
        when(connection.isConnected()).thenReturn(true);
        player.con = connection;
        return player;
    }

    private Player recipient(String translatorLanguage) {
        Player player = connectedPlayer(translatorLanguage + "-" + System.nanoTime());
        Session session = mock(Session.class);
        PlayerData data = new PlayerData(player.uuid(), true);
        data.translatorLanguage = translatorLanguage;
        session.data = data;
        session.player = player;
        when(session.locale()).thenReturn(mock(Localization.class));
        sessions.add(session);
        return player;
    }

    private final List<Session> sessions = new ArrayList<>();

    private void givenGlobalRecipients() {
        when(sessionService.getAllCachedSnapshot()).thenReturn(sessions);
    }

    @Test
    @DisplayName("translator-off players get the original at once; translated players get one combined line")
    void deliverGlobal_sendsOriginalAndTranslatedLines() {
        Player off = recipient("off");
        Player russian = recipient("ru");
        givenGlobalRecipients();
        translate("hello", "ru", "привет");

        service().deliverGlobal(author, "hello");

        verify(off).sendMessage("<hello>", author, "hello");
        verify(russian).sendMessage("<hello> [white]([lightgray]привет[])", author, "hello (привет)");
        verify(author, never()).sendMessage(anyString(), any(Player.class), anyString());
    }

    @Test
    @DisplayName("deadline mode: the original goes out at the deadline and the translation follows")
    void deliverGlobal_sendsOriginalAtDeadline_thenLateTranslation() {
        Player russian = recipient("ru");
        givenGlobalRecipients();
        ChatDeliveryService service = service();

        service.deliverGlobal(author, "hello");
        verify(russian, never()).sendMessage(anyString(), any(Player.class), anyString());

        passDeadline();
        verify(russian).sendMessage("<hello>", author, "hello");

        translate("hello", "ru", "при[вет");
        verify(russian).sendMessage("[lightgray]» Author: при[[вет");
    }

    @Test
    @DisplayName("a translation later than late_translation_millis is dropped")
    void deliverGlobal_dropsVeryLateTranslation() {
        Player russian = recipient("ru");
        givenGlobalRecipients();
        ChatDeliveryService service = service();

        service.deliverGlobal(author, "hello");
        passDeadline();
        now += TimeUnit.MILLISECONDS.toNanos(config.translation.delivery.lateTranslationMillis);
        translate("hello", "ru", "привет");

        verify(russian).sendMessage("<hello>", author, "hello");
        verify(russian, never()).sendMessage(anyString());
    }

    @Test
    @DisplayName("lines leave a language lane in message order")
    void deliverGlobal_keepsMessageOrder() {
        Player russian = recipient("ru");
        givenGlobalRecipients();
        ChatDeliveryService service = service();
        translate("second", "ru", "второе");

        service.deliverGlobal(author, "first");
        service.deliverGlobal(author, "second");
        verify(russian, never()).sendMessage(anyString(), any(Player.class), anyString());

        translate("first", "ru", "первое");

        InOrder order = inOrder(russian);
        order.verify(russian).sendMessage("<first> [white]([lightgray]первое[])", author, "first (первое)");
        order.verify(russian).sendMessage("<second> [white]([lightgray]второе[])", author, "second (второе)");
        org.assertj.core.api.Assertions.assertThat(service.pendingCount()).isZero();
    }

    @Test
    @DisplayName("a failed translation delivers the original")
    void deliverGlobal_deliversOriginalOnFailure() {
        Player russian = recipient("ru");
        givenGlobalRecipients();
        ChatDeliveryService service = service();

        service.deliverGlobal(author, "hello");
        fail("hello", "ru");

        verify(russian).sendMessage("<hello>", author, "hello");
    }

    @Test
    @DisplayName("unsupported translator languages get the original without a translation request")
    void deliverGlobal_unsupportedLanguageGetsOriginal() {
        Player klingon = recipient("tlh");
        givenGlobalRecipients();
        when(translations.supports("tlh")).thenReturn(false);

        service().deliverGlobal(author, "hello");

        verify(klingon).sendMessage("<hello>", author, "hello");
        verify(translations, never()).translate(anyString(), anyString(), anyCollection());
    }

    @Test
    @DisplayName("original-first mode sends the original at once and the translation as a follow-up")
    void deliverGlobal_originalFirst() {
        config.translation.delivery.mode = "original-first";
        Player russian = recipient("ru");
        givenGlobalRecipients();

        service().deliverGlobal(author, "hello");
        verify(russian).sendMessage("<hello>", author, "hello");

        translate("hello", "ru", "привет");
        verify(russian).sendMessage("[lightgray]» Author: привет");
    }

    @Test
    @DisplayName("wait mode never sends a follow-up line")
    void deliverGlobal_waitModeHasNoFollowUp() {
        config.translation.delivery.mode = "wait";
        Player russian = recipient("ru");
        givenGlobalRecipients();
        ChatDeliveryService service = service();

        service.deliverGlobal(author, "hello");
        now += TimeUnit.MILLISECONDS.toNanos(config.translation.delivery.lateTranslationMillis);
        passDeadline();
        translate("hello", "ru", "привет");

        verify(russian).sendMessage("<hello>", author, "hello");
        verify(russian, never()).sendMessage(anyString());
    }

    @Test
    @DisplayName("recipients who left before the translation arrived are skipped")
    void deliverGlobal_skipsDisconnectedRecipients() {
        Player russian = recipient("ru");
        givenGlobalRecipients();
        ChatDeliveryService service = service();

        service.deliverGlobal(author, "hello");
        when(russian.con.isConnected()).thenReturn(false);
        translate("hello", "ru", "привет");

        verify(russian, never()).sendMessage(anyString(), any(Player.class), anyString());
    }

    @Test
    @DisplayName("team chat: author gets the original, Foo's clients get the raw text, vanilla clients do not")
    void deliverTeam_handlesAuthorAndClientCompatibility() {
        Team team = Team.sharded;
        when(author.team()).thenReturn(team);
        Session authorSession = mock(Session.class);
        PlayerData authorData = new PlayerData("author-uuid", true);
        authorData.translatorLanguage = "ru";
        authorSession.data = authorData;
        authorSession.player = author;
        Player foos = recipient("ru");
        Player vanilla = recipient("ru");
        List<Session> teamSessions = new ArrayList<>(sessions);
        teamSessions.add(authorSession);
        when(sessionService.findByTeam(team)).thenReturn(teamSessions);
        when(compatibility.isLikelyFoosClient(foos)).thenReturn(true);
        translate("hi team", "ru", "привет команда");

        service().deliverTeam(author, "hi team");

        verify(author).sendMessage("{hi team}", author);
        verify(foos).sendMessage("{hi team} [white]([lightgray]привет команда[])", author, "hi team (привет команда)");
        verify(vanilla).sendMessage("{hi team} [white]([lightgray]привет команда[])", author);
    }

    @Test
    @DisplayName("relayed chat is rendered per recipient and sent without an author")
    void deliverRemote_rendersPerRecipient() {
        Player off = recipient("off");
        Player russian = recipient("ru");
        givenGlobalRecipients();
        for (Session session : sessions) {
            when(session.locale().format(eq("global-chat-format"), any())).thenReturn("[mini] Bob: hi all");
        }
        translate("hi all", "ru", "всем привет");

        service().deliverRemote("remote:mini:Bob", "[red]Bob", "hi all", "global-chat-format", Map.of(), null);

        verify(off).sendMessage("[mini] Bob: hi all");
        verify(russian).sendMessage("[mini] Bob: hi all [white]([lightgray]всем привет[])");
        verify(translations).translate(eq("remote:mini:Bob"), eq("hi all"), anyCollection());
    }
}
