package org.xcore.plugin.modules.packets;

import arc.struct.Seq;
import arc.util.Log;
import com.google.gson.JsonObject;
import mindustry.io.JsonIO;
import org.xcore.plugin.PluginVars;

import java.util.Arrays;

public class PlayerInfoPacket {
    String lastName, lastIP, uuid;
    boolean admin;

    Seq<String> names, ips;

    public PlayerInfoPacket(String lastName, String lastIP, String uuid, boolean admin, Seq<String> names, Seq<String> ips) {
        this.lastName = lastName;
        this.lastIP = lastIP;
        this.uuid = uuid;
        this.admin = admin;
        this.names = names;
        this.ips = ips;
    }

    public JsonObject generate(){
        JsonObject json = new JsonObject();
        json.addProperty("lastName",lastName);
        json.addProperty("lastIp",lastIP);
        json.addProperty("uuid",uuid);
        json.addProperty("admin", admin);
        StringBuilder b = new StringBuilder("~[");
        for (String n : names){
            b.append('\t').append(n).append('\t');
            if(names.indexOf(n)!=names.size-1){
                b.append(',');
            }
        }
        b.append("]~");
        json.addProperty("names",b.toString());
        b = new StringBuilder("~[");
        for (String n : ips){
            b.append('\t').append(n).append('\t');
            if(ips.indexOf(n)!=ips.size-1){
                b.append(',');
            }
        }
        b.append("]~");
        json.addProperty("ips", b.toString());
        Log.debug(json.get("names"));
        Log.debug(json.get("ips"));
        return json;
    }
}
