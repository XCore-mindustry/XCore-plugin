package org.xcore.plugin.modules.packets;

import arc.struct.Seq;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class PlayerInfoPacket {
    String lastName, lastIP, uuid;
    boolean admin;

    Seq<String> names, ips;
}
