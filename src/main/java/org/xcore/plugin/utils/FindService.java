package org.xcore.plugin.utils;

import arc.util.Strings;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Administration;
import org.xcore.plugin.commands.controllers.client.TranslatorLanguagesProvider;
import org.xcore.plugin.modules.database.DatabaseService;
import org.xcore.plugin.utils.models.PlayerData;

import static arc.util.Strings.parseInt;
import static mindustry.Vars.netServer;
import static org.xcore.plugin.utils.Utils.deepEquals;
import static org.xcore.plugin.utils.Utils.notNullElse;

@Singleton
public class FindService {

    private final DatabaseService database;
    private final TranslatorLanguagesProvider translatorLanguages;

    @Inject
    public FindService(DatabaseService database, TranslatorLanguagesProvider translatorLanguages) {
        this.database = database;
        this.translatorLanguages = translatorLanguages;
    }

    public Player player(String nameOrId) {
        return notNullElse(playerById(nameOrId), playerByName(nameOrId));
    }

    public Player playerById(String id) {
        return id.startsWith("#") ? Groups.player.getByID(parseInt(id.substring(1))) : null;
    }

    public Player playerByName(String name) {
        return Groups.player.find(player -> deepEquals(player.name, name));
    }

    public Player playerByUuid(String uuid) {
        return Groups.player.find(player -> player.uuid().equals(uuid));
    }

    public PlayerData playerData(String uuidOrPid) {
        return uuidOrPid.startsWith("#")
                ? database.getCachedOrDb(Strings.parseInt(uuidOrPid.substring(1)))
                : database.getCachedOrDb(uuidOrPid);
    }

    public Administration.PlayerInfo playerInfo(String name) {
        var player = player(name);
        if (player != null) return player.getInfo();
        return notNullElse(netServer.admins.getInfoOptional(name), netServer.admins.findByIP(name));
    }

    public String findTranslatorLanguage(String locale) {
        return translatorLanguages.getLanguages().orderedKeys().find(locale::startsWith);
    }
}