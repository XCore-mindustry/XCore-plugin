package org.xcore.plugin.utils.models;

import fr.xpdustry.javelin.JavelinEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IPBanData implements JavelinEvent {
    public String ip;

    @Builder.Default
    public String name = "<unknown>";
    @Builder.Default
    public String adminName = "<unknown>";
    @Builder.Default
    public String reason = "Not Specified";

    public long unbanDate;
}
