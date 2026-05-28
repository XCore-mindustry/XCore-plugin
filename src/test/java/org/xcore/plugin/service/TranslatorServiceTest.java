package org.xcore.plugin.service;

import arc.func.Boolf;
import mindustry.entities.EntityGroup;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.localization.TranslationFailure;
import org.xcore.plugin.localization.TranslationProvider;
import org.xcore.plugin.localization.TranslationResult;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TranslatorServiceTest {

    private EntityGroup<Player> previousPlayerGroup;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        previousPlayerGroup = Groups.player;
        EntityGroup<Player> playerGroup = mock(EntityGroup.class);
        Groups.player = playerGroup;
    }

    @AfterEach
    void tearDown() {
        Groups.player = previousPlayerGroup;
    }

    @Test
    @DisplayName("translate sends original message when all translation providers fail")
    void translate_sendsOriginalMessage_whenAllTranslationProvidersFail() {
        TomlXcoreConfig config = config();
        SessionService sessionService = mock(SessionService.class);
        ChatFormatService chatFormatService = mock(ChatFormatService.class);
        ClientCompatibilityService clientCompatibilityService = mock(ClientCompatibilityService.class);
        TranslationFallbackService translationFallbackService = mock(TranslationFallbackService.class);
        TranslationCacheService translationCacheService = mock(TranslationCacheService.class);
        TranslationMetricsService translationMetricsService = mock(TranslationMetricsService.class);
        TranslatorService service = new TranslatorService(
                config,
                sessionService,
                chatFormatService,
                clientCompatibilityService,
                translationFallbackService,
                translationCacheService,
                translationMetricsService
        );

        Player author = mock(Player.class);
        Player recipient = mock(Player.class);
        when(recipient.uuid()).thenReturn("recipient-uuid");

        Session recipientSession = mock(Session.class);
        PlayerData recipientData = new PlayerData("recipient-uuid", true);
        recipientData.translatorLanguage = "ru";
        recipientSession.data = recipientData;

        when(sessionService.getAllCachedSnapshot()).thenReturn(List.of(recipientSession));
        when(chatFormatService.formatChat(author, "hello")).thenReturn("formatted-message");
        doAnswer(invocation -> {
            Boolf<Player> predicate = invocation.getArgument(0);
            return predicate.get(recipient) ? recipient : null;
        }).when(Groups.player).find(any());
        doAnswer(invocation -> {
            arc.func.Cons<TranslationResult> callback = invocation.getArgument(1);
            callback.get(TranslationResult.failure(TranslationFailure.unavailable("fallback", "all translation providers failed")));
            return null;
        }).when(translationFallbackService).translate(any(TranslationProvider.Request.class), any());

        service.translate(author, "hello");

        verify(recipient).sendMessage("formatted-message", author, "hello");
    }

    @Test
    @DisplayName("translate sends original message when target language is unsupported")
    void translate_sendsOriginalMessage_whenTargetLanguageIsUnsupported() {
        TomlXcoreConfig config = config();
        SessionService sessionService = mock(SessionService.class);
        ChatFormatService chatFormatService = mock(ChatFormatService.class);
        ClientCompatibilityService clientCompatibilityService = mock(ClientCompatibilityService.class);
        TranslationFallbackService translationFallbackService = mock(TranslationFallbackService.class);
        TranslationCacheService translationCacheService = mock(TranslationCacheService.class);
        TranslationMetricsService translationMetricsService = mock(TranslationMetricsService.class);
        TranslatorService service = new TranslatorService(
                config,
                sessionService,
                chatFormatService,
                clientCompatibilityService,
                translationFallbackService,
                translationCacheService,
                translationMetricsService
        );

        Player author = mock(Player.class);
        Player recipient = mock(Player.class);
        when(recipient.uuid()).thenReturn("recipient-uuid");

        Session recipientSession = mock(Session.class);
        PlayerData recipientData = new PlayerData("recipient-uuid", true);
        recipientData.translatorLanguage = "ru";
        recipientSession.data = recipientData;

        when(sessionService.getAllCachedSnapshot()).thenReturn(List.of(recipientSession));
        when(chatFormatService.formatChat(author, "hello")).thenReturn("formatted-message");
        when(translationFallbackService.supports("ru")).thenReturn(false);
        doAnswer(invocation -> {
            Boolf<Player> predicate = invocation.getArgument(0);
            return predicate.get(recipient) ? recipient : null;
        }).when(Groups.player).find(any());

        service.translate(author, "hello");

        verify(recipient).sendMessage("formatted-message", author, "hello");
        verify(translationFallbackService, never()).translate(any(TranslationProvider.Request.class), any());
    }

    @Test
    @DisplayName("translate uses cached translation before provider pipeline")
    void translate_usesCachedTranslation_beforeProviderPipeline() {
        TomlXcoreConfig config = config();
        SessionService sessionService = mock(SessionService.class);
        ChatFormatService chatFormatService = mock(ChatFormatService.class);
        ClientCompatibilityService clientCompatibilityService = mock(ClientCompatibilityService.class);
        TranslationFallbackService translationFallbackService = mock(TranslationFallbackService.class);
        TranslationCacheService translationCacheService = mock(TranslationCacheService.class);
        TranslationMetricsService translationMetricsService = mock(TranslationMetricsService.class);
        TranslatorService service = new TranslatorService(
                config,
                sessionService,
                chatFormatService,
                clientCompatibilityService,
                translationFallbackService,
                translationCacheService,
                translationMetricsService
        );
        AtomicReference<String> translated = new AtomicReference<>();

        when(translationFallbackService.supports("ru")).thenReturn(true);
        when(translationFallbackService.pipelineSignature()).thenReturn("nvidia:openai,google:google");
        when(translationCacheService.get("auto", "ru", "hello", "nvidia:openai,google:google"))
                .thenReturn(new TranslationCacheService.CachedTranslation("привет", "pipeline", System.currentTimeMillis()));

        service.translate("hello", "auto", "ru", translated::set, () -> translated.set("error"));

        assertThat(translated.get()).isEqualTo("привет");
        verify(translationFallbackService, never()).translate(any(TranslationProvider.Request.class), any());
    }

    @Test
    @DisplayName("translate sends original message when translation matches original text")
    void translate_sendsOriginalMessage_whenTranslationMatchesOriginalText() {
        TomlXcoreConfig config = config();
        SessionService sessionService = mock(SessionService.class);
        ChatFormatService chatFormatService = mock(ChatFormatService.class);
        ClientCompatibilityService clientCompatibilityService = mock(ClientCompatibilityService.class);
        TranslationFallbackService translationFallbackService = mock(TranslationFallbackService.class);
        TranslationCacheService translationCacheService = mock(TranslationCacheService.class);
        TranslationMetricsService translationMetricsService = mock(TranslationMetricsService.class);
        TranslatorService service = new TranslatorService(
                config,
                sessionService,
                chatFormatService,
                clientCompatibilityService,
                translationFallbackService,
                translationCacheService,
                translationMetricsService
        );

        Player author = mock(Player.class);
        Player recipient = mock(Player.class);
        when(recipient.uuid()).thenReturn("recipient-uuid");

        Session recipientSession = mock(Session.class);
        PlayerData recipientData = new PlayerData("recipient-uuid", true);
        recipientData.translatorLanguage = "ru";
        recipientSession.data = recipientData;

        when(sessionService.getAllCachedSnapshot()).thenReturn(List.of(recipientSession));
        when(chatFormatService.formatChat(author, "привет")).thenReturn("formatted-message");
        when(translationFallbackService.supports("ru")).thenReturn(true);
        doAnswer(invocation -> {
            Boolf<Player> predicate = invocation.getArgument(0);
            return predicate.get(recipient) ? recipient : null;
        }).when(Groups.player).find(any());
        doAnswer(invocation -> {
            arc.func.Cons<TranslationResult> callback = invocation.getArgument(1);
            callback.get(TranslationResult.success("  Привет  "));
            return null;
        }).when(translationFallbackService).translate(any(TranslationProvider.Request.class), any());

        service.translate(author, "привет");

        verify(recipient).sendMessage("formatted-message", author, "привет");
    }

    @Test
    @DisplayName("translate sends original message when provider returns blank translation")
    void translate_sendsOriginalMessage_whenProviderReturnsBlankTranslation() {
        TomlXcoreConfig config = config();
        SessionService sessionService = mock(SessionService.class);
        ChatFormatService chatFormatService = mock(ChatFormatService.class);
        ClientCompatibilityService clientCompatibilityService = mock(ClientCompatibilityService.class);
        TranslationFallbackService translationFallbackService = mock(TranslationFallbackService.class);
        TranslationCacheService translationCacheService = mock(TranslationCacheService.class);
        TranslationMetricsService translationMetricsService = mock(TranslationMetricsService.class);
        TranslatorService service = new TranslatorService(
                config,
                sessionService,
                chatFormatService,
                clientCompatibilityService,
                translationFallbackService,
                translationCacheService,
                translationMetricsService
        );

        Player author = mock(Player.class);
        Player recipient = mock(Player.class);
        when(recipient.uuid()).thenReturn("recipient-uuid");

        Session recipientSession = mock(Session.class);
        PlayerData recipientData = new PlayerData("recipient-uuid", true);
        recipientData.translatorLanguage = "ru";
        recipientSession.data = recipientData;

        when(sessionService.getAllCachedSnapshot()).thenReturn(List.of(recipientSession));
        when(chatFormatService.formatChat(author, "hello")).thenReturn("formatted-message");
        when(translationFallbackService.supports("ru")).thenReturn(true);
        doAnswer(invocation -> {
            Boolf<Player> predicate = invocation.getArgument(0);
            return predicate.get(recipient) ? recipient : null;
        }).when(Groups.player).find(any());
        doAnswer(invocation -> {
            arc.func.Cons<TranslationResult> callback = invocation.getArgument(1);
            callback.get(TranslationResult.success("   "));
            return null;
        }).when(translationFallbackService).translate(any(TranslationProvider.Request.class), any());

        service.translate(author, "hello");

        verify(recipient).sendMessage("formatted-message", author, "hello");
    }

    @Test
    @DisplayName("translate does not send original message when preserveOriginalMessageOnFailure is disabled")
    void translate_doesNotSendOriginalMessage_whenPreserveOriginalMessageOnFailureDisabled() {
        TomlXcoreConfig config = config();
        config.translation.preserveOriginalMessageOnFailure = false;
        SessionService sessionService = mock(SessionService.class);
        ChatFormatService chatFormatService = mock(ChatFormatService.class);
        ClientCompatibilityService clientCompatibilityService = mock(ClientCompatibilityService.class);
        TranslationFallbackService translationFallbackService = mock(TranslationFallbackService.class);
        TranslationCacheService translationCacheService = mock(TranslationCacheService.class);
        TranslationMetricsService translationMetricsService = mock(TranslationMetricsService.class);
        TranslatorService service = new TranslatorService(
                config,
                sessionService,
                chatFormatService,
                clientCompatibilityService,
                translationFallbackService,
                translationCacheService,
                translationMetricsService
        );

        Player author = mock(Player.class);
        Player recipient = mock(Player.class);
        when(recipient.uuid()).thenReturn("recipient-uuid");

        Session recipientSession = mock(Session.class);
        PlayerData recipientData = new PlayerData("recipient-uuid", true);
        recipientData.translatorLanguage = "ru";
        recipientSession.data = recipientData;

        when(sessionService.getAllCachedSnapshot()).thenReturn(List.of(recipientSession));
        when(chatFormatService.formatChat(author, "hello")).thenReturn("formatted-message");
        when(translationFallbackService.supports("ru")).thenReturn(true);
        doAnswer(invocation -> {
            Boolf<Player> predicate = invocation.getArgument(0);
            return predicate.get(recipient) ? recipient : null;
        }).when(Groups.player).find(any());
        doAnswer(invocation -> {
            arc.func.Cons<TranslationResult> callback = invocation.getArgument(1);
            callback.get(TranslationResult.failure(TranslationFailure.unavailable("fallback", "all translation providers failed")));
            return null;
        }).when(translationFallbackService).translate(any(TranslationProvider.Request.class), any());

        service.translate(author, "hello");

        verify(recipient, never()).sendMessage("formatted-message", author, "hello");
    }

    @Test
    @DisplayName("translateTeamChat sends translated team message when translator is enabled")
    void translateTeamChat_sendsTranslatedTeamMessage_whenTranslatorIsEnabled() {
        TomlXcoreConfig config = config();
        SessionService sessionService = mock(SessionService.class);
        ChatFormatService chatFormatService = mock(ChatFormatService.class);
        ClientCompatibilityService clientCompatibilityService = mock(ClientCompatibilityService.class);
        TranslationFallbackService translationFallbackService = mock(TranslationFallbackService.class);
        TranslationCacheService translationCacheService = mock(TranslationCacheService.class);
        TranslationMetricsService translationMetricsService = mock(TranslationMetricsService.class);
        TranslatorService service = new TranslatorService(
                config,
                sessionService,
                chatFormatService,
                clientCompatibilityService,
                translationFallbackService,
                translationCacheService,
                translationMetricsService
        );

        Team team = mock(Team.class);
        Player author = mock(Player.class);
        Player recipient = mock(Player.class);
        when(author.team()).thenReturn(team);

        Localization authorLocalization = mock(Localization.class);
        Localization recipientLocalization = mock(Localization.class);

        Session authorSession = mock(Session.class);
        PlayerData authorData = new PlayerData("author-uuid", true);
        authorData.translatorLanguage = "off";
        authorSession.player = author;
        authorSession.data = authorData;
        when(authorSession.locale()).thenReturn(authorLocalization);

        Session recipientSession = mock(Session.class);
        PlayerData recipientData = new PlayerData("recipient-uuid", true);
        recipientData.translatorLanguage = "ru";
        recipientSession.player = recipient;
        recipientSession.data = recipientData;
        when(recipientSession.locale()).thenReturn(recipientLocalization);

        when(sessionService.findByTeam(team)).thenReturn(List.of(authorSession, recipientSession));
        when(chatFormatService.formatTeamChat(author, authorLocalization, "hello")).thenReturn("team-author");
        when(chatFormatService.formatTeamChat(author, recipientLocalization, "hello")).thenReturn("team-recipient");
        when(clientCompatibilityService.isLikelyFoosClient(author)).thenReturn(false);
        when(clientCompatibilityService.isLikelyFoosClient(recipient)).thenReturn(false);
        when(translationFallbackService.supports("ru")).thenReturn(true);
        doAnswer(invocation -> {
            arc.func.Cons<TranslationResult> callback = invocation.getArgument(1);
            callback.get(TranslationResult.success("привет"));
            return null;
        }).when(translationFallbackService).translate(any(TranslationProvider.Request.class), any());

        service.translateTeamChat(author, "hello");

        verify(author).sendMessage("team-author", author);
        verify(recipient).sendMessage("team-recipient [white]([lightgray]привет[])", author);
    }

    @Test
    @DisplayName("translateTeamChat reuses translation for same target language")
    void translateTeamChat_reusesTranslation_forSameTargetLanguage() {
        TomlXcoreConfig config = config();
        SessionService sessionService = mock(SessionService.class);
        ChatFormatService chatFormatService = mock(ChatFormatService.class);
        ClientCompatibilityService clientCompatibilityService = mock(ClientCompatibilityService.class);
        TranslationFallbackService translationFallbackService = mock(TranslationFallbackService.class);
        TranslationCacheService translationCacheService = mock(TranslationCacheService.class);
        TranslationMetricsService translationMetricsService = mock(TranslationMetricsService.class);
        TranslatorService service = new TranslatorService(
                config,
                sessionService,
                chatFormatService,
                clientCompatibilityService,
                translationFallbackService,
                translationCacheService,
                translationMetricsService
        );

        Team team = mock(Team.class);
        Player author = mock(Player.class);
        Player firstRecipient = mock(Player.class);
        Player secondRecipient = mock(Player.class);
        when(author.team()).thenReturn(team);

        Localization authorLocalization = mock(Localization.class);
        Localization firstLocalization = mock(Localization.class);
        Localization secondLocalization = mock(Localization.class);

        Session authorSession = mock(Session.class);
        PlayerData authorData = new PlayerData("author-uuid", true);
        authorData.translatorLanguage = "off";
        authorSession.player = author;
        authorSession.data = authorData;
        when(authorSession.locale()).thenReturn(authorLocalization);

        Session firstSession = mock(Session.class);
        PlayerData firstData = new PlayerData("recipient-1", true);
        firstData.translatorLanguage = "ru";
        firstSession.player = firstRecipient;
        firstSession.data = firstData;
        when(firstSession.locale()).thenReturn(firstLocalization);

        Session secondSession = mock(Session.class);
        PlayerData secondData = new PlayerData("recipient-2", true);
        secondData.translatorLanguage = "ru";
        secondSession.player = secondRecipient;
        secondSession.data = secondData;
        when(secondSession.locale()).thenReturn(secondLocalization);

        when(sessionService.findByTeam(team)).thenReturn(List.of(authorSession, firstSession, secondSession));
        when(chatFormatService.formatTeamChat(author, authorLocalization, "hello")).thenReturn("team-author");
        when(chatFormatService.formatTeamChat(author, firstLocalization, "hello")).thenReturn("team-recipient-1");
        when(chatFormatService.formatTeamChat(author, secondLocalization, "hello")).thenReturn("team-recipient-2");
        when(clientCompatibilityService.isLikelyFoosClient(author)).thenReturn(false);
        when(clientCompatibilityService.isLikelyFoosClient(firstRecipient)).thenReturn(false);
        when(clientCompatibilityService.isLikelyFoosClient(secondRecipient)).thenReturn(false);
        when(translationFallbackService.supports("ru")).thenReturn(true);
        doAnswer(invocation -> {
            arc.func.Cons<TranslationResult> callback = invocation.getArgument(1);
            callback.get(TranslationResult.success("привет"));
            return null;
        }).when(translationFallbackService).translate(any(TranslationProvider.Request.class), any());

        service.translateTeamChat(author, "hello");

        verify(translationFallbackService).translate(any(TranslationProvider.Request.class), any());
        verify(firstRecipient).sendMessage("team-recipient-1 [white]([lightgray]привет[])", author);
        verify(secondRecipient).sendMessage("team-recipient-2 [white]([lightgray]привет[])", author);
    }

    @Test
    @DisplayName("translateTeamChat sends original team message when translation matches original text")
    void translateTeamChat_sendsOriginalTeamMessage_whenTranslationMatchesOriginalText() {
        TomlXcoreConfig config = config();
        SessionService sessionService = mock(SessionService.class);
        ChatFormatService chatFormatService = mock(ChatFormatService.class);
        ClientCompatibilityService clientCompatibilityService = mock(ClientCompatibilityService.class);
        TranslationFallbackService translationFallbackService = mock(TranslationFallbackService.class);
        TranslationCacheService translationCacheService = mock(TranslationCacheService.class);
        TranslationMetricsService translationMetricsService = mock(TranslationMetricsService.class);
        TranslatorService service = new TranslatorService(
                config,
                sessionService,
                chatFormatService,
                clientCompatibilityService,
                translationFallbackService,
                translationCacheService,
                translationMetricsService
        );

        Team team = mock(Team.class);
        Player author = mock(Player.class);
        Player recipient = mock(Player.class);
        when(author.team()).thenReturn(team);

        Localization authorLocalization = mock(Localization.class);
        Localization recipientLocalization = mock(Localization.class);

        Session authorSession = mock(Session.class);
        PlayerData authorData = new PlayerData("author-uuid", true);
        authorData.translatorLanguage = "off";
        authorSession.player = author;
        authorSession.data = authorData;
        when(authorSession.locale()).thenReturn(authorLocalization);

        Session recipientSession = mock(Session.class);
        PlayerData recipientData = new PlayerData("recipient-uuid", true);
        recipientData.translatorLanguage = "ru";
        recipientSession.player = recipient;
        recipientSession.data = recipientData;
        when(recipientSession.locale()).thenReturn(recipientLocalization);

        when(sessionService.findByTeam(team)).thenReturn(List.of(authorSession, recipientSession));
        when(chatFormatService.formatTeamChat(author, authorLocalization, "привет")).thenReturn("team-author");
        when(chatFormatService.formatTeamChat(author, recipientLocalization, "привет")).thenReturn("team-recipient");
        when(clientCompatibilityService.isLikelyFoosClient(author)).thenReturn(false);
        when(clientCompatibilityService.isLikelyFoosClient(recipient)).thenReturn(false);
        when(translationFallbackService.supports("ru")).thenReturn(true);
        doAnswer(invocation -> {
            arc.func.Cons<TranslationResult> callback = invocation.getArgument(1);
            callback.get(TranslationResult.success(" привет "));
            return null;
        }).when(translationFallbackService).translate(any(TranslationProvider.Request.class), any());

        service.translateTeamChat(author, "привет");

        verify(author).sendMessage("team-author", author);
        verify(recipient).sendMessage("team-recipient", author);
    }

    @Test
    @DisplayName("translateTeamChat sends original raw message for foos client when translation matches original text")
    void translateTeamChat_sendsOriginalRawMessage_forFoosClientWhenTranslationMatchesOriginalText() {
        TomlXcoreConfig config = config();
        SessionService sessionService = mock(SessionService.class);
        ChatFormatService chatFormatService = mock(ChatFormatService.class);
        ClientCompatibilityService clientCompatibilityService = mock(ClientCompatibilityService.class);
        TranslationFallbackService translationFallbackService = mock(TranslationFallbackService.class);
        TranslationCacheService translationCacheService = mock(TranslationCacheService.class);
        TranslationMetricsService translationMetricsService = mock(TranslationMetricsService.class);
        TranslatorService service = new TranslatorService(
                config,
                sessionService,
                chatFormatService,
                clientCompatibilityService,
                translationFallbackService,
                translationCacheService,
                translationMetricsService
        );

        Team team = mock(Team.class);
        Player author = mock(Player.class);
        Player recipient = mock(Player.class);
        when(author.team()).thenReturn(team);

        Localization authorLocalization = mock(Localization.class);
        Localization recipientLocalization = mock(Localization.class);

        Session authorSession = mock(Session.class);
        PlayerData authorData = new PlayerData("author-uuid", true);
        authorData.translatorLanguage = "off";
        authorSession.player = author;
        authorSession.data = authorData;
        when(authorSession.locale()).thenReturn(authorLocalization);

        Session recipientSession = mock(Session.class);
        PlayerData recipientData = new PlayerData("recipient-uuid", true);
        recipientData.translatorLanguage = "ru";
        recipientSession.player = recipient;
        recipientSession.data = recipientData;
        when(recipientSession.locale()).thenReturn(recipientLocalization);

        when(sessionService.findByTeam(team)).thenReturn(List.of(authorSession, recipientSession));
        when(chatFormatService.formatTeamChat(author, authorLocalization, "привет")).thenReturn("team-author");
        when(chatFormatService.formatTeamChat(author, recipientLocalization, "привет")).thenReturn("team-recipient");
        when(clientCompatibilityService.isLikelyFoosClient(author)).thenReturn(false);
        when(clientCompatibilityService.isLikelyFoosClient(recipient)).thenReturn(true);
        when(translationFallbackService.supports("ru")).thenReturn(true);
        doAnswer(invocation -> {
            arc.func.Cons<TranslationResult> callback = invocation.getArgument(1);
            callback.get(TranslationResult.success("ПРИВЕТ"));
            return null;
        }).when(translationFallbackService).translate(any(TranslationProvider.Request.class), any());

        service.translateTeamChat(author, "привет");

        verify(author).sendMessage("team-author", author);
        verify(recipient).sendMessage("team-recipient", author, "привет");
    }

    @Test
    @DisplayName("translateTeamChat does not send original message when preserveOriginalMessageOnFailure is disabled")
    void translateTeamChat_doesNotSendOriginalMessage_whenPreserveOriginalMessageOnFailureDisabled() {
        TomlXcoreConfig config = config();
        config.translation.preserveOriginalMessageOnFailure = false;
        SessionService sessionService = mock(SessionService.class);
        ChatFormatService chatFormatService = mock(ChatFormatService.class);
        ClientCompatibilityService clientCompatibilityService = mock(ClientCompatibilityService.class);
        TranslationFallbackService translationFallbackService = mock(TranslationFallbackService.class);
        TranslationCacheService translationCacheService = mock(TranslationCacheService.class);
        TranslationMetricsService translationMetricsService = mock(TranslationMetricsService.class);
        TranslatorService service = new TranslatorService(
                config,
                sessionService,
                chatFormatService,
                clientCompatibilityService,
                translationFallbackService,
                translationCacheService,
                translationMetricsService
        );

        Team team = mock(Team.class);
        Player author = mock(Player.class);
        Player recipient = mock(Player.class);
        when(author.team()).thenReturn(team);

        Localization authorLocalization = mock(Localization.class);
        Localization recipientLocalization = mock(Localization.class);

        Session authorSession = mock(Session.class);
        PlayerData authorData = new PlayerData("author-uuid", true);
        authorData.translatorLanguage = "off";
        authorSession.player = author;
        authorSession.data = authorData;
        when(authorSession.locale()).thenReturn(authorLocalization);

        Session recipientSession = mock(Session.class);
        PlayerData recipientData = new PlayerData("recipient-uuid", true);
        recipientData.translatorLanguage = "ru";
        recipientSession.player = recipient;
        recipientSession.data = recipientData;
        when(recipientSession.locale()).thenReturn(recipientLocalization);

        when(sessionService.findByTeam(team)).thenReturn(List.of(authorSession, recipientSession));
        when(chatFormatService.formatTeamChat(author, authorLocalization, "hello")).thenReturn("team-author");
        when(chatFormatService.formatTeamChat(author, recipientLocalization, "hello")).thenReturn("team-recipient");
        when(clientCompatibilityService.isLikelyFoosClient(author)).thenReturn(false);
        when(clientCompatibilityService.isLikelyFoosClient(recipient)).thenReturn(false);
        when(translationFallbackService.supports("ru")).thenReturn(true);
        doAnswer(invocation -> {
            arc.func.Cons<TranslationResult> callback = invocation.getArgument(1);
            callback.get(TranslationResult.failure(TranslationFailure.unavailable("fallback", "all translation providers failed")));
            return null;
        }).when(translationFallbackService).translate(any(TranslationProvider.Request.class), any());

        service.translateTeamChat(author, "hello");

        verify(author).sendMessage("team-author", author);
        verify(recipient, never()).sendMessage("team-recipient", author);
    }

    @Test
    @DisplayName("translateTeamChat sends foos-compatible raw message for likely foos client")
    void translateTeamChat_sendsFoosCompatibleRawMessage_forLikelyFoosClient() {
        TomlXcoreConfig config = config();
        SessionService sessionService = mock(SessionService.class);
        ChatFormatService chatFormatService = mock(ChatFormatService.class);
        ClientCompatibilityService clientCompatibilityService = mock(ClientCompatibilityService.class);
        TranslationFallbackService translationFallbackService = mock(TranslationFallbackService.class);
        TranslationCacheService translationCacheService = mock(TranslationCacheService.class);
        TranslationMetricsService translationMetricsService = mock(TranslationMetricsService.class);
        TranslatorService service = new TranslatorService(
                config,
                sessionService,
                chatFormatService,
                clientCompatibilityService,
                translationFallbackService,
                translationCacheService,
                translationMetricsService
        );

        Team team = mock(Team.class);
        Player author = mock(Player.class);
        Player recipient = mock(Player.class);
        when(author.team()).thenReturn(team);

        Localization authorLocalization = mock(Localization.class);
        Localization recipientLocalization = mock(Localization.class);

        Session authorSession = mock(Session.class);
        PlayerData authorData = new PlayerData("author-uuid", true);
        authorData.translatorLanguage = "off";
        authorSession.player = author;
        authorSession.data = authorData;
        when(authorSession.locale()).thenReturn(authorLocalization);

        Session recipientSession = mock(Session.class);
        PlayerData recipientData = new PlayerData("recipient-uuid", true);
        recipientData.translatorLanguage = "ru";
        recipientSession.player = recipient;
        recipientSession.data = recipientData;
        when(recipientSession.locale()).thenReturn(recipientLocalization);

        when(sessionService.findByTeam(team)).thenReturn(List.of(authorSession, recipientSession));
        when(chatFormatService.formatTeamChat(author, authorLocalization, "hello")).thenReturn("team-author");
        when(chatFormatService.formatTeamChat(author, recipientLocalization, "hello")).thenReturn("team-recipient");
        when(clientCompatibilityService.isLikelyFoosClient(author)).thenReturn(false);
        when(clientCompatibilityService.isLikelyFoosClient(recipient)).thenReturn(true);
        when(translationFallbackService.supports("ru")).thenReturn(true);
        doAnswer(invocation -> {
            arc.func.Cons<TranslationResult> callback = invocation.getArgument(1);
            callback.get(TranslationResult.success("привет"));
            return null;
        }).when(translationFallbackService).translate(any(TranslationProvider.Request.class), any());

        service.translateTeamChat(author, "hello");

        verify(author).sendMessage("team-author", author);
        verify(recipient).sendMessage("team-recipient [white]([lightgray]привет[])", author, "hello (привет)");
    }
    private static TomlXcoreConfig config() {
        return new TomlXcoreConfig();
    }
}
