package org.xcore.plugin;

import arc.files.Fi;
import arc.struct.OrderedMap;
import arc.util.serialization.JsonReader;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.xcore.plugin.modules.Config;
import org.xcore.plugin.modules.GlobalConfig;
import org.xcore.plugin.modules.votes.VoteKick;
import org.xcore.plugin.modules.votes.VoteSession;
import org.xcore.plugin.utils.database.Database;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static mindustry.Vars.dataDirectory;

public class PluginVars {
    public static final JsonReader reader = new JsonReader();
    public static final int votekickPlayTime = 60;
    public static final long kickDuration = 30 * 60 * 1000L;
    public static final float voteRatio = 0.55f;
    public static final float voteDuration = 60.0f;
    public static final int mapLoadDelay = 10;
    public static boolean gameoverRestart = false;
    public static final DateTimeFormatter longDateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneOffset.UTC);
    public static String discordUrl = "https://discord.gg/RUMCCa9QAC";
    public static Fi configFile = dataDirectory.child("xcconfig.json");
    public static Config config;
    public static GlobalConfig globalConfig;
    public static Database database;
    public static Gson gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();
    public static OrderedMap<String, String> translatorLanguages = new OrderedMap<>();
    public static VoteSession vote;
    public static VoteKick voteKick;
}
