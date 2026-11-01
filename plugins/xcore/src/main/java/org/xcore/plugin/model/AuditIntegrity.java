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
public class AuditIntegrity {
    @BsonProperty("dedupe_key")
    public String dedupeKey;

    public String hash;
}
