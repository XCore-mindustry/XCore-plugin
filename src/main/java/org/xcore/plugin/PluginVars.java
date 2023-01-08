package org.xcore.plugin;

import arc.files.Fi;
import arc.util.CommandHandler;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.xcore.plugin.comp.Config;

import java.util.HashSet;

import static mindustry.Vars.dataDirectory;

public class PluginVars {

    public static String discordURL = "https://discord.gg/RUMCCa9QAC";
    public static Fi configFile = dataDirectory.child("xcconfig.json");
    public static Config config;
    public static Gson gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    public static CommandHandler serverCommands;

    public static double rtvRatio = 0.6;
    public static HashSet<String> rtvVotes = new HashSet<>();
    public static boolean rtvEnabled = true;
}
