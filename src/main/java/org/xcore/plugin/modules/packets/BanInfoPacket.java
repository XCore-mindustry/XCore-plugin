package org.xcore.plugin.modules.packets;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class BanInfoPacket {
    String name, uuid, ip, adminName, reason;
    public long unbanDate;
}