package org.xcore.plugin.model;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamGameData {
    public String Team;

    public long joinTime;
    public long leaveTime;
}
