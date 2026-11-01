package org.xcore.cloud.mindustry.exception;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.caption.Caption;
import org.xcore.cloud.mindustry.parser.MindustryCaptionKeys;

/**
 * A player-only command was run by a sender that is not an in-game player.
 */
public class PlayerRequiredException extends RuntimeException {

    public PlayerRequiredException() {
        super("This command can only be used by an in-game player");
    }

    public @NonNull Caption caption() {
        return MindustryCaptionKeys.SENDER_PLAYER_REQUIRED;
    }
}
