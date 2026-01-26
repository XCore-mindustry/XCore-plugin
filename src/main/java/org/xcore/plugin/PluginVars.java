package org.xcore.plugin;

import arc.files.Fi;
import arc.util.serialization.JsonReader;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.reflect.Modifier;
import static mindustry.Vars.dataDirectory;

public class PluginVars {
    public static final JsonReader reader = new JsonReader();
    public static final int votekickPlayTime = 60;
    public static final int globalChatPlayTime = 4 * 60;

    public static final int kickDuration = 30 * 60 * 1000;
    public static final float voteDuration = 60.0f;
    public static final int mapLoadDelay = 10;

    public static long gameStarted;

    public static boolean gameoverRestart = false;

    public static final String discordUrl = "https://discord.gg/RUMCCa9QAC";
    public static final String githubUrl = "https://github.com/XCore-mindustry/";
    public static final String donatelloUrl = "https://donatello.to/xcore";
    public static final String discordRedVSBlueUrl = "https://discord.gg/UdnuFetcNt";

    public static final Fi configFile = dataDirectory.child("xcconfig.json");

    public static final Gson prettyGson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .excludeFieldsWithModifiers(Modifier.PRIVATE, Modifier.TRANSIENT, Modifier.STATIC)
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    public static final Gson rawGson = new GsonBuilder().serializeNulls().create();

    public static String xcoreVersion = "Unknown";
}