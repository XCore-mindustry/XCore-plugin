package org.xcore.plugin.event.net.chat;

import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.protocol.generated.messages.chat.ChatMessages.ChatMessageV1;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.service.ChatFormatService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.SecurityService;
import org.xcore.plugin.service.TranslatorService;

@Singleton
public class ChatMessageHandler {

    private final Config config;
    private final TranslatorService translatorService;
    private final NetworkService network;
    private final SecurityService securityService;
    private final ChatFormatService chatFormatService;
    private final VoteChatInterceptor voteChatInterceptor;

    @Inject
    public ChatMessageHandler(Config config,
                              TranslatorService translatorService,
                              NetworkService network,
                              SecurityService securityService,
                              ChatFormatService chatFormatService,
                              VoteChatInterceptor voteChatInterceptor) {
        this.config = config;
        this.translatorService = translatorService;
        this.network = network;
        this.securityService = securityService;
        this.chatFormatService = chatFormatService;
        this.voteChatInterceptor = voteChatInterceptor;
    }

    public String handle(Player author, String text) {
        if (voteChatInterceptor.intercept(author, text)) {
            return null;
        }

        Log.info("&fi@: @", "&lc" + author.plainName(), "&lw" + text);

        if (securityService.isMuted(author)) {
            return null;
        }

        author.sendMessage(chatFormatService.formatChat(author, text), author, text);
        translatorService.translate(author, text);

        network.post(new ChatMessageV1(author.plainName(), text.replace("`", "*"), config.server));
        return null;
    }
}
