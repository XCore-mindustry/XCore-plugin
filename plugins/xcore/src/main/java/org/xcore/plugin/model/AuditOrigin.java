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
public class AuditOrigin {
    @Builder.Default
    public AuditOriginChannel channel = AuditOriginChannel.SYSTEM;

    @Builder.Default
    public String source = "xcore-plugin";

    @BsonProperty("server_id")
    public String serverId;

    @BsonProperty("request_id")
    public String requestId;
}
