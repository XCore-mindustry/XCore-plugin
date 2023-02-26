package org.xcore.plugin.modules.packets;

import arc.struct.Seq;
import org.xcore.plugin.modules.models.BannedData;

public class BannedPlayersPacket {
    public Seq<BannedData> bans;
}
