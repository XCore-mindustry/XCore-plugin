package org.xcore.plugin.modules.packets;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class BanInfoPacket {
    public long unbanDate;
    String name, uuid, adminName, reason;
}