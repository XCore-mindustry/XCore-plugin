package org.xcore.plugin.service.moderation;

public record UnmuteCommand(
        int targetId,
        String targetUuid,
        String targetIp,
        String targetName,
        ModerationActor actor
) {
    public static UnmuteCommand byId(int id, ModerationActor actor) {
        return new UnmuteCommand(id, null, null, null, actor != null ? actor : ModerationActor.CONSOLE);
    }

    public static UnmuteCommand byTarget(String uuid, String ip, String name, ModerationActor actor) {
        return new UnmuteCommand(-1, uuid, ip, name, actor != null ? actor : ModerationActor.CONSOLE);
    }
}
