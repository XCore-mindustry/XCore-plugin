package org.xcore.plugin.service.moderation;

import org.xcore.plugin.model.PlayerPids;

import java.util.Objects;

public record UnbanCommand(
        int targetId,
        String targetUuid,
        String targetIp,
        String targetName,
        ModerationActor actor
) {
    public static UnbanCommand byId(int id, ModerationActor actor) {
        return new UnbanCommand(id, null, null, null, Objects.requireNonNull(actor, "actor"));
    }

    public static UnbanCommand byTarget(String uuid, String ip, String name, ModerationActor actor) {
        return new UnbanCommand(PlayerPids.NONE, uuid, ip, name, Objects.requireNonNull(actor, "actor"));
    }
}
