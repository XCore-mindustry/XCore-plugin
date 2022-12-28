package org.xcore.plugin;

import arc.files.Fi;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.xcore.plugin.comp.Config;

import static mindustry.Vars.dataDirectory;

public class PluginVars {
    public static Fi configFile = dataDirectory.child("xcconfig.json");
    public static Config config;
    public static Gson gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();
}
