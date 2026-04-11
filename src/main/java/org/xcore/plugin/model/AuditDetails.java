package org.xcore.plugin.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditDetails {
    @BsonProperty("duration_ms")
    public Long durationMs;

    @BsonProperty("expires_at")
    public Instant expiresAt;

    public String visibility;

    public Set<String> tags;

    @Builder.Default
    public Map<String, String> extra = Map.of();
}
