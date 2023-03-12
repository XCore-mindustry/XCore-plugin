package org.xcore.plugin;

import arc.files.Fi;
import arc.struct.ObjectMap;
import arc.struct.OrderedMap;
import arc.util.serialization.JsonReader;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.xcore.plugin.modules.votes.VoteKick;
import org.xcore.plugin.modules.votes.VoteSession;
import org.xcore.plugin.utils.Config;
import org.xcore.plugin.utils.GlobalConfig;
import org.xcore.plugin.utils.models.BanData;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static mindustry.Vars.dataDirectory;

public class PluginVars {
    public static final JsonReader reader = new JsonReader();
    public static final String banJson = "{\"name\": \"@\", \"uuid\": \"@\", \"ip\": \"@\", \"reason\": \"\", \"duration\": \"0\", \"skip_to_discord\": false, \"error\": \"\"}";
    public static final int maxHistoryCapacity = 6;
    public static final long kickDuration = 30 * 60 * 1000L;
    public static final float voteRatio = 0.55f;
    public static final float voteDuration = 60.0f;
    public static final int mapLoadDelay = 10;
    public static boolean isSocketServer;
    public static String discordURL = "https://discord.gg/RUMCCa9QAC";
    public static Fi configFile = dataDirectory.child("xcconfig.json");
    public static Config config;
    public static GlobalConfig globalConfig;
    public static Gson gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    public static final DateTimeFormatter shortDateFormat = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC);
    public static ObjectMap<Long, BanData> activeBanData = new ObjectMap<>();
    public static OrderedMap<String, String> translatorLanguages = new OrderedMap<>();
    public static VoteSession vote;
    public static VoteKick voteKick;
}
