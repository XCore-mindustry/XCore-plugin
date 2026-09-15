package org.xcore.plugin.service.moderation;

public record UnbanCommand(
        int targetId,
        String targetUuid,
        String targetIp,
        String targetName,
        ModerationActor actor
) {
    public static UnbanCommand byId(int id, ModerationActor actor) {
        return new UnbanCommand(id, null, null, null, actor != null ? actor : ModerationActor.CONSOLE);
    }

    public static UnbanCommand byTarget(String uuid, String ip, String name, ModerationActor actor) {
        return new UnbanCommand(-1, uuid, ip, name, actor != null ? actor : ModerationActor.CONSOLE);
    }
}
