package org.xcore.plugin.model;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.bson.codecs.pojo.annotations.BsonProperty;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BanData extends Punishment {
    @Builder.Default public String ip = null;
    @BsonProperty("ban_id")
    public Integer banId;
    public Integer pid;
    @BsonProperty("case_number")
    public Integer caseNumber;
}
