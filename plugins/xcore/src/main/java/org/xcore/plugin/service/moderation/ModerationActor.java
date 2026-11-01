package org.xcore.plugin.service.moderation;

import org.xcore.plugin.permission.Actor;

import java.util.Objects;

/**
 * Represents the administrator or system actor initiating a moderation action.
 * <p>
 * There is no fallback: an action whose initiator is unknown is refused rather than recorded
 * as the console's.
 */
public record ModerationActor(String name, String discordId) {

    public static final ModerationActor CONSOLE = new ModerationActor(Actor.LocalConsole.AUDIT_NAME, null);

    public ModerationActor {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A moderation actor needs a name");
        }
        discordId = discordId == null || discordId.isBlank() ? null : discordId;
    }

    public static ModerationActor of(String name, String discordId) {
        return new ModerationActor(name, discordId);
    }

    public static ModerationActor of(Actor actor) {
        Objects.requireNonNull(actor, "actor");
        String discordId = actor instanceof Actor.PlayerActor(_, var session) && session != null && session.data != null
                ? session.data.discordId
                : null;
        return new ModerationActor(actor.auditName(), discordId);
    }
}
