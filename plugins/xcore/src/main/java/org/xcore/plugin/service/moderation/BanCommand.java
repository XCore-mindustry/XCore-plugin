package org.xcore.plugin.service.moderation;

import org.xcore.plugin.model.PlayerPids;

import java.time.Duration;
import java.util.Objects;

public record BanCommand(
        int targetId,
        String targetUuid,
        String targetIp,
        String targetName,
        ModerationActor actor,
        String reason,
        Duration duration,
        boolean kickOnline
) {
    public static Builder byId(int id, ModerationActor actor, Duration duration) {
        return new Builder().targetId(id).actor(actor).duration(duration);
    }

    public static Builder byTarget(String uuid, String ip, String name, ModerationActor actor, Duration duration) {
        return new Builder().targetUuid(uuid).targetIp(ip).targetName(name).actor(actor).duration(duration);
    }

    public static class Builder {
        private int targetId = PlayerPids.NONE;
        private String targetUuid;
        private String targetIp;
        private String targetName;
        private ModerationActor actor;
        private String reason;
        private Duration duration;
        private boolean kickOnline = true;

        public Builder targetId(int targetId) { this.targetId = targetId; return this; }
        public Builder targetUuid(String targetUuid) { this.targetUuid = targetUuid; return this; }
        public Builder targetIp(String targetIp) { this.targetIp = targetIp; return this; }
        public Builder targetName(String targetName) { this.targetName = targetName; return this; }
        public Builder actor(ModerationActor actor) { this.actor = Objects.requireNonNull(actor, "actor"); return this; }
        public Builder reason(String reason) { this.reason = reason; return this; }
        public Builder duration(Duration duration) { this.duration = duration; return this; }
        public Builder kickOnline(boolean kickOnline) { this.kickOnline = kickOnline; return this; }

        public BanCommand build() {
            return new BanCommand(targetId, targetUuid, targetIp, targetName, actor, reason, duration, kickOnline);
        }
    }
}
