package org.xcore.plugin.modules.packets;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class PlayerInfoPacket {
    String lastName, lastIP, uuid;
    boolean admin;

    String[] names, ips;
}
