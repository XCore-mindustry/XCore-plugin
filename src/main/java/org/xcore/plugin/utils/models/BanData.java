package org.xcore.plugin.utils.models;

import fr.xpdustry.javelin.JavelinEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.xcore.plugin.utils.Database;

@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BanData implements JavelinEvent {
    public long bid;

    @NonNull
    public String uuid;
    @Builder.Default
    public String ip = "";
    @Builder.Default
    public String name = "<unknown>";
    @Builder.Default
    public String adminName = "<unknown>";
    @Builder.Default
    public String reason = "Not Specified";

    @NonNull
    public String server;

    public long unbanDate;

    @BsonIgnore
    @Builder.Default
    public boolean full = true;
    @BsonIgnore
    @Builder.Default
    public boolean unban = false;

    public void generateBid() {
        this.bid = Database.getNextSequence("banid");
    }
}
