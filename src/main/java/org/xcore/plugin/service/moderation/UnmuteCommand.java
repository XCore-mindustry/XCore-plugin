package org.xcore.plugin.service.moderation;

import org.xcore.plugin.model.PlayerPids;

import java.util.Objects;

public record UnmuteCommand(
        int targetId,
        String targetUuid,
        String targetIp,
        String targetName,
        ModerationActor actor
) {
    public static UnmuteCommand byId(int id, ModerationActor actor) {
        return new UnmuteCommand(id, null, null, null, Objects.requireNonNull(actor, "actor"));
    }

    public static UnmuteCommand byTarget(String uuid, String ip, String name, ModerationActor actor) {
        return new UnmuteCommand(PlayerPids.NONE, uuid, ip, name, Objects.requireNonNull(actor, "actor"));
    }
}
