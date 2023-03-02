package org.xcore.plugin.utils;

import org.xcore.plugin.XcorePlugin;
import org.xcore.plugin.modules.Console;

import static org.xcore.plugin.PluginVars.*;

public class Config {
    //Строчка названия сервера
    public String server = "server";

    /**
     * Отвечает за использование продвинутой версии консоли {@link Console#init()}
     */
    public boolean consoleEnabled = true;
    //Путь до директории содержащей глобальные настройки
    public String globalConfigDirectory = null;

    /**
     * Загрузка и применение конфигурации. Если фаил не существует то он будет сгенерирован автоматически
     */
    public static void init() {
        if (configFile.exists()) {
            config = gson.fromJson(configFile.reader(), Config.class);
            XcorePlugin.info("Config loaded.");
        } else {
            configFile.writeString(gson.toJson(config = new Config()));
            XcorePlugin.info("Config generated.");
        }
    }

    /**
     * Метод проверки активности режима Mini PvP
     * @return true - Если режим включен
     */
    public boolean isMiniPvP() {
        return server.equals("mini-pvp");
    }

    /**
     * Метод проверки активности режима Mini Hexed
     * @return true - Если режим включен
     */
    public boolean isMiniHexed() {
        return server.equals("mini-hexed");
    }
}
