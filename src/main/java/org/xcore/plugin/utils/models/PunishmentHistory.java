package org.xcore.plugin.utils.models;

import arc.util.Time;
import lombok.NonNull;

import java.time.Instant;
import java.util.Date;

import static org.xcore.plugin.PluginVars.database;

public class PunishmentHistory {
    public int pid;
    public long duration;

    public String adminName;
    public String reason;

    public Date date;
    public PunishmentType type;

    public PunishmentHistory() {
    }

    public static PunishmentHistory of(@NonNull BanData data, int pid) {
        PunishmentHistory result = new PunishmentHistory();

        result.pid = pid;
        result.duration = data.unbanDate.getTime() - Time.millis();
        result.adminName = data.adminName;
        result.reason = data.reason;
        result.date = Date.from(Instant.now());
        result.type = PunishmentType.BAN;

        return result;
    }

    public void save() {
        database.punishmentHistoryExecutor.insertPunishment(this);
    }

    public enum PunishmentType {
        BAN, MUTE, VOTEKICK
    }
}
