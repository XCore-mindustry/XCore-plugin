package org.xcore.plugin.modules.packets;

import arc.util.serialization.Json;
import arc.util.serialization.JsonValue;
import com.google.gson.JsonObject;

public class BanInfoPacket {
    String name, uuid, adminName, reason;
    public long unbanDate;

    public BanInfoPacket(String name, String uuid, String adminName, String reason, long unbanDate) {
        this.name = name;
        this.uuid = uuid;
        this.adminName = adminName;
        this.reason = reason;
        this.unbanDate = unbanDate;
    }

    public JsonObject generate(){
        JsonObject json = new JsonObject();
        json.addProperty("name",name);
        json.addProperty("uuid",uuid);
        json.addProperty("adminName",adminName);
        json.addProperty("reason",reason);
        json.addProperty("unbanDate",unbanDate);
        return json;
    }
}