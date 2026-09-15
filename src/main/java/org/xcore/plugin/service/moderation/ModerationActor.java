package org.xcore.plugin.service.moderation;

import mindustry.gen.Player;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;

/**
 * Represents the administrator or system actor initiating a moderation action.
 */
public record ModerationActor(String name, String discordId) {

    public static final ModerationActor CONSOLE = new ModerationActor("console", null);

    public static ModerationActor of(String name, String discordId) {
        return new ModerationActor(
                name == null || name.isBlank() ? "console" : name,
                discordId == null || discordId.isBlank() ? null : discordId
        );
    }

    public static ModerationActor of(Session session) {
        if (session == null || session.player == null) {
            return CONSOLE;
        }
        String discordId = session.data != null ? session.data.discordId : null;
        return of(session.player.plainName(), discordId);
    }

    public static ModerationActor of(Player player, PlayerData data) {
        if (player == null) {
            return CONSOLE;
        }
        String discordId = data != null ? data.discordId : null;
        return of(player.plainName(), discordId);
    }
}
