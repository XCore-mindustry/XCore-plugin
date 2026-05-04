package org.xcore.plugin.service;

import arc.util.Strings;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Administration;
import org.xcore.plugin.localization.TranslatorLanguagesProvider;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.SessionService;

import static arc.util.Strings.parseInt;
import static mindustry.Vars.netServer;
import static org.xcore.plugin.common.NullSafe.orElse;
import static org.xcore.plugin.common.TextUtils.deepEquals;

@Singleton
public class FindService {

    private final SessionService sessionService;
    private final TranslatorLanguagesProvider translatorLanguages;

    @Inject
    public FindService(SessionService sessionService,
                       TranslatorLanguagesProvider translatorLanguages) {
        this.sessionService = sessionService;
        this.translatorLanguages = translatorLanguages;
    }

    public Player player(String nameOrId) {
        return orElse(playerById(nameOrId), playerByName(nameOrId));
    }

    public Player playerById(String id) {
        return id.startsWith("#") ? Groups.player.getByID(parseInt(id.substring(1))) : null;
    }

    public Player playerByName(String name) {
        return Groups.player.find(player -> deepEquals(player.name, name));
    }

    public Player playerByUuid(String uuid) {
        if (uuid == null) {
            return null;
        }
        return Groups.player.find(player -> uuid.equals(player.uuid()));
    }

    public PlayerData playerData(String uuidOrPid) {
        if (uuidOrPid == null || uuidOrPid.isBlank()) {
            return null;
        }
        return uuidOrPid.startsWith("#")
                ? sessionService.getOrLoadFromDb(Strings.parseInt(uuidOrPid.substring(1)))
                : sessionService.getOrLoadFromDb(uuidOrPid);
    }

    public Administration.PlayerInfo playerInfo(String name) {
        var player = player(name);
        if (player != null) return player.getInfo();
        return orElse(netServer.admins.getInfoOptional(name), netServer.admins.findByIP(name));
    }

    public String findTranslatorLanguage(String locale) {
        return translatorLanguages.getLanguages().orderedKeys().find(locale::startsWith);
    }
}