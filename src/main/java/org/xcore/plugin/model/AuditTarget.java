package org.xcore.plugin.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonProperty;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditTarget {
    @Builder.Default
    public String uuid = "";

    public Integer pid;

    @BsonProperty("name_snapshot")
    @Builder.Default
    public String nameSnapshot = "Unknown";

    @BsonProperty("ip_snapshot")
    public String ipSnapshot;
}
