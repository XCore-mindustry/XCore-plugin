package org.xcore.plugin.modules.packets;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class BanInfoPacket {
    String name, uuid, adminName, reason;
    public long unbanDate;
}