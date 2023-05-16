package org.xcore.plugin.utils.models;

import arc.util.Time;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.Date;

@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BanData {
    @Builder.Default
    public String uuid = null;
    @Builder.Default
    public String ip = null;
    @Builder.Default
    public String name = "<unknown>";
    @Builder.Default
    public String adminName = "<unknown>";
    @Builder.Default
    public String reason = "Not Specified";

    public Date unbanDate;

    public boolean expired() {
        return unbanDate.getTime() < Time.millis();
    }
}
