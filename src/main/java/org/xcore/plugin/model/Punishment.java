package org.xcore.plugin.model;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.bson.codecs.pojo.annotations.BsonProperty;

import java.time.Instant;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public abstract class Punishment extends ModelData {
    @Builder.Default public String uuid = "";
    @Builder.Default public String name = "Unknown";
    @BsonProperty("admin_name")
    @Builder.Default public String adminName = "Unknown";
    @Builder.Default public String reason = "Not Specified";

    @BsonProperty("expire_date")
    public Instant expireDate;

    public boolean expired() {
        return expireDate != null && expireDate.isBefore(Instant.now());
    }
}