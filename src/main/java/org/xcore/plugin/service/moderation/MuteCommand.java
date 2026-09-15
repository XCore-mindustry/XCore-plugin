package org.xcore.plugin.service.moderation;

import java.time.Duration;

public record MuteCommand(
        int targetId,
        String targetUuid,
        String targetIp,
        String targetName,
        ModerationActor actor,
        String reason,
        Duration duration
) {
    public static Builder byId(int id, ModerationActor actor, Duration duration) {
        return new Builder().targetId(id).actor(actor).duration(duration);
    }

    public static Builder byTarget(String uuid, String ip, String name, ModerationActor actor, Duration duration) {
        return new Builder().targetUuid(uuid).targetIp(ip).targetName(name).actor(actor).duration(duration);
    }

    public static class Builder {
        private int targetId = -1;
        private String targetUuid;
        private String targetIp;
        private String targetName;
        private ModerationActor actor = ModerationActor.CONSOLE;
        private String reason;
        private Duration duration;

        public Builder targetId(int targetId) { this.targetId = targetId; return this; }
        public Builder targetUuid(String targetUuid) { this.targetUuid = targetUuid; return this; }
        public Builder targetIp(String targetIp) { this.targetIp = targetIp; return this; }
        public Builder targetName(String targetName) { this.targetName = targetName; return this; }
        public Builder actor(ModerationActor actor) { this.actor = actor != null ? actor : ModerationActor.CONSOLE; return this; }
        public Builder reason(String reason) { this.reason = reason; return this; }
        public Builder duration(Duration duration) { this.duration = duration; return this; }

        public MuteCommand build() {
            return new MuteCommand(targetId, targetUuid, targetIp, targetName, actor, reason, duration);
        }
    }
}
