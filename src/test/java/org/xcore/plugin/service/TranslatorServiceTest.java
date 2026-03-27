package org.xcore.plugin.service;

import arc.func.Boolf;
import mindustry.entities.EntityGroup;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.localization.TranslationFailure;
import org.xcore.plugin.localization.TranslationProvider;
import org.xcore.plugin.localization.TranslationResult;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
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
        SessionService sessionService = mock(SessionService.class);
        ChatFormatService chatFormatService = mock(ChatFormatService.class);
        TranslationFallbackService translationFallbackService = mock(TranslationFallbackService.class);
        TranslatorService service = new TranslatorService(sessionService, chatFormatService, translationFallbackService);

        Player author = mock(Player.class);
        Player recipient = mock(Player.class);
        when(recipient.uuid()).thenReturn("recipient-uuid");

        Session recipientSession = mock(Session.class);
        PlayerData recipientData = new PlayerData("recipient-uuid", true);
        recipientData.translatorLanguage = "ru";
        recipientSession.data = recipientData;

        when(sessionService.getAllCached()).thenReturn(List.of(recipientSession));
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
}
