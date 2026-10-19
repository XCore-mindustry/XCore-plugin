package org.xcore.plugin.event.net.chat;

import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.cases.RecentChatBuffer;
import org.xcore.plugin.chat.ChatDeliveryService;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.service.ChatFormatService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.SecurityService;
import org.xcore.protocol.generated.messages.chat.ChatMessages.ChatMessageV1;

@Singleton
public class ChatMessageHandler {

    private final TomlXcoreConfig config;
    private final ChatDeliveryService chatDeliveryService;
    private final NetworkService network;
    private final SecurityService securityService;
    private final ChatFormatService chatFormatService;
    private final VoteChatInterceptor voteChatInterceptor;

    private final RecentChatBuffer recentChat;

    @Inject
    public ChatMessageHandler(TomlXcoreConfig config,
                              ChatDeliveryService chatDeliveryService,
                              NetworkService network,
                              SecurityService securityService,
                              ChatFormatService chatFormatService,
                              VoteChatInterceptor voteChatInterceptor,
                              RecentChatBuffer recentChat) {
        this.recentChat = recentChat;
        this.config = config;
        this.chatDeliveryService = chatDeliveryService;
        this.network = network;
        this.securityService = securityService;
        this.chatFormatService = chatFormatService;
        this.voteChatInterceptor = voteChatInterceptor;
    }

    public ChatMessageHandler(TomlXcoreConfig config, ChatDeliveryService chatDeliveryService, NetworkService network,
                              SecurityService securityService, ChatFormatService chatFormatService,
                              VoteChatInterceptor voteChatInterceptor) {
        this(config, chatDeliveryService, network, securityService, chatFormatService, voteChatInterceptor, null);
    }

    public String handle(Player author, String text) {
        if (voteChatInterceptor.intercept(author, text)) {
            return null;
        }

        // Anti-leak guard: Prevent accidental password leakage into public chat, logs, and Discord relay
        String trimmed = text.trim();
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("/t ") || lower.startsWith("/a ")) {
            lower = lower.substring(3).trim();
        }
        if (lower.startsWith("login ") || lower.startsWith("/login") || lower.startsWith("xl ") || lower.startsWith("/xl")) {
            author.sendMessage("[scarlet]⚠ Отправка пароля в общий чат заблокирована.");
            return null;
        }

        Log.info("&fi@: @", "&lc" + author.plainName(), "&lw" + text);

        if (securityService.checkAndNotifyMuted(author)) {
            return null;
        }

        if (recentChat != null) recentChat.record(author, text, false);

        author.sendMessage(chatFormatService.formatChat(author, text), author, text);
        chatDeliveryService.deliverGlobal(author, text);

        network.post(new ChatMessageV1(author.plainName(), text.replace("`", "*"), config.server.name));
        return null;
    }
}
