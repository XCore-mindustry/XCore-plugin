package org.xcore.plugin;

import arc.files.Fi;
import arc.struct.ObjectMap;
import arc.util.Timekeeper;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.xcore.plugin.modules.Config;

import java.util.HashSet;

import static mindustry.Vars.dataDirectory;

public class PluginVars {
    public static boolean isSocketServer;
    public static String discordURL = "https://discord.gg/RUMCCa9QAC";
    public static Fi configFile = dataDirectory.child("xcconfig.json");
    public static Config config;
    public static Gson gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    public static double rtvRatio = 0.6;
    public static HashSet<String> rtvVotes = new HashSet<>();
    public static boolean rtvEnabled = true;

    //duration of a kick in seconds
    public static int kickDuration = 60 * 60;
    //voting round duration in seconds
    public static float voteDuration = 0.5f * 60;
    //cooldown between votes in seconds
    public static int voteCooldown = 60 * 2;

    public static ObjectMap<String, Timekeeper> cooldowns = new ObjectMap<>();
    //current kick sessions
    public static Utils.VoteSession[] currentlyKicking = {null};
}
