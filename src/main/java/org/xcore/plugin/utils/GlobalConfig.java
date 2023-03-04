package org.xcore.plugin.utils;

import arc.files.Fi;
import arc.struct.ObjectMap;
import arc.util.serialization.Jval;
import org.xcore.plugin.XcorePlugin;

import static org.xcore.plugin.PluginVars.*;

public class GlobalConfig {
    //Путь к глобальному фаилу конфигурации
    public static Fi globalConfigFile = Fi.get(config.globalConfigDirectory == null ? System.getProperty("user.home") : config.globalConfigDirectory).child("servers.json");
    //Перечень серверов и их Discord аналогов
    public ObjectMap<String, Long> servers = new ObjectMap<>();
    //Ссылка подключения к базе данных
    public String mongoConnectionString = "";
    //Токен авторизации Discord
    public String discordBotToken = "";
    //Идентификатор роли-администратора
    public long discordAdminRoleId = 0L;
    //Идентификатор канала для отправки банов
    public long discordBansChannelId = 0L;

    /**
     * Чтение глобальной конфигурации. Создаст новый фаил при отсутствии фаила по ссылке {@link GlobalConfig#globalConfigFile}
     */
    public static void init() {
        if (globalConfigFile.exists()) {
            globalConfig = gson.fromJson(globalConfigFile.reader(), GlobalConfig.class);
            XcorePlugin.info("Global Config loaded.");
        } else {
            globalConfigFile.writeString(gson.toJson(globalConfig = new GlobalConfig()));
            XcorePlugin.info("Global Config generated.");
        }
        globalConfig.postInit();
    }

    /**
     * Прогрузка {@link GlobalConfig#servers} из фаила конфигурации
     */
    public void postInit() {
        Jval.read(globalConfigFile.reader()).asObject().forEach(jval -> {
            if (jval.key.equals("servers")) {
                jval.value.asObject().forEach(j -> servers.put(j.key, j.value.asLong()));
            }
        });
    }
}
