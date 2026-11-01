package org.xcore.plugin.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class ChatFormatService {

    private final SessionService sessionService;
    private final PlayerDisplayService playerDisplayService;

    @Inject
    public ChatFormatService(SessionService sessionService, PlayerDisplayService playerDisplayService) {
        this.sessionService = sessionService;
        this.playerDisplayService = playerDisplayService;
    }

    public String formatChat(Player author, String message) {
        return formatChat(author, null, message);
    }

    public String formatChat(Player author, Localization localization, String message) {
        var data = resolveData(author);
        var badgePrefix = playerDisplayService.buildChatBadgePrefix(data, author);
        var name = playerDisplayService.resolveChatBaseName(data, author);
        if (badgePrefix.isEmpty()) {
            return "[accent]" + name + "[lightgray]: [white]" + message;
        }

        return badgePrefix + " [accent]" + name + "[lightgray]: [white]" + message;
    }

    public String formatTeamChat(Player author, Localization localization, String message) {
        var data = resolveData(author);
        var badgePrefix = playerDisplayService.buildChatBadgePrefix(data, author);
        return localization.format("commands-t-chat", args(
                "color", author.team().color,
                "badge", badgePrefix.isEmpty() ? "" : badgePrefix + " ",
                "name", playerDisplayService.resolveChatBaseName(data, author),
                "message", message
        ));
    }

    private PlayerData resolveData(Player author) {
        Session session = sessionService.get(author);
        return session == null ? null : session.data;
    }
}
