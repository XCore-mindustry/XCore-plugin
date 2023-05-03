package org.xcore.plugin.utils.models;

import fr.xpdustry.javelin.JavelinEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UUIDBanData implements JavelinEvent {
    @NonNull
    public String uuid;
    @Builder.Default
    public String name = "<unknown>";
    @Builder.Default
    public String adminName = "<unknown>";
    @Builder.Default
    public String reason = "Not Specified";

    @NonNull
    public String server;

    public long unbanDate;
}
