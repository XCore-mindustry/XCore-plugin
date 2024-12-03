package org.xcore.plugin.utils.models;

import arc.util.Time;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Date;

@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class Punishment {
    public String uuid;
    @Builder.Default
    public String name = "<unknown>";
    @Builder.Default
    public String adminName = "<unknown>";
    @Builder.Default
    public String reason = "Not Specified";

    public Date expireDate;

    public boolean expired() {
        return expireDate.getTime() < Time.millis();
    }

    public void save() {
    }

}
