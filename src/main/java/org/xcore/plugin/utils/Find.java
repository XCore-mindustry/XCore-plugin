package org.xcore.plugin.utils;

import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Administration;

import static arc.util.Strings.parseInt;
import static mindustry.Vars.netServer;
import static org.xcore.plugin.PluginVars.translatorLanguages;
import static org.xcore.plugin.utils.Utils.deepEquals;
import static org.xcore.plugin.utils.Utils.notNullElse;

public class Find {
    public static Player player(String nameOrId) {
        return notNullElse(playerById(nameOrId), playerByName(nameOrId));
    }

    public static Player playerById(String id) {
        return id.startsWith("#") ? Groups.player.getByID(parseInt(id.substring(1))) : null;
    }

    public static Player playerByName(String name) {
        return Groups.player.find(player -> deepEquals(player.name, name));
    }

    public static Player playerByUuid(String uuid) {
        return Groups.player.find(player -> player.uuid().equals(uuid));
    }

    public static Administration.PlayerInfo playerInfo(String name) {
        var player = player(name);
        if (player != null) return player.getInfo();

        return notNullElse(netServer.admins.getInfoOptional(name), netServer.admins.findByIP(name));
    }

    public static String findTranslatorLanguage(String locale) {
        return translatorLanguages.orderedKeys().find(locale::startsWith);
    }
}
