package tilelogger;

import org.xcore.plugin.listeners.NetEvents;
import org.xcore.plugin.modules.votes.VoteKick;
import org.xcore.plugin.utils.Find;
import org.xcore.plugin.utils.models.PlayerData;

import arc.util.Nullable;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.gen.Player;
import mindustry.net.Administration.PlayerInfo;
import useful.Bundle;

import static org.xcore.plugin.PluginVars.database;
import static useful.Bundle.send;

import javax.swing.GroupLayout.Group;

public class XCoreIntegration {
    public static void init() {
        Bundle.load(TileLoggerPlugin.class);
        VoteKick.setOnKick(player -> TileLogger.rollback(null, findPlayerUuid(player.uuid()), -1, -180, new Rect((short) 0, (short) 0, (short) (Vars.world.width() - 1), (short) (Vars.world.height() - 1))));
        NetEvents.setIpAcceptor(TileLogger::checkSubnetAccepted);
    }

    public static void sendBundled(@Nullable Player player, String msg, Object... values) {
        send(player, msg, values);
    }

    public static boolean useAdminTools(Player player) {
        return player != null && database.getCachedOrDb(player.uuid()).adminModVersion != null && !player.con.mobile;
    }

    public static @Nullable PlayerDescriptor findPlayer(String str) {
        if (str.equals("all")) return new PlayerDescriptor("all", "", -1);
        PlayerData data = null;
        try {
            if (data == null) data = database.getCachedOrDb(Integer.parseInt(str)); // by pid
        } catch (NumberFormatException e) {}
        if (data == null) data = database.getCachedOrDb(str); // by uuid
        Player player = Find.playerByName(str);
        if (data == null && player != null) data = database.getCachedOrDb(player.uuid()); // by name
        if (data == null) return null;
        return new PlayerDescriptor(data.nickname, data.uuid, data.pid);
    }

    public static @Nullable PlayerDescriptor findPlayerUuid(String str) {
        PlayerData data = database.getCachedOrDb(str); // by uuid
        if (data != null) return new PlayerDescriptor(data.nickname, data.uuid, data.pid);
        PlayerInfo info = Vars.netServer.admins.getInfoOptional(str);
        if (info != null) return new PlayerDescriptor(info.lastName, info.id, -1);
        return null;
    }
}
