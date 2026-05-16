package org.xcore.plugin.common;

import arc.util.Log;
import arc.util.Log.LogLevel;
import arc.util.Strings;

import java.io.PrintWriter;
import java.io.StringWriter;

public final class PLog {
    private static final String TAG = "XCore";

    private static final String TAG_INFO  = "&lc[" + TAG + "]&fr";
    private static final String TAG_WARN  = "&ly[" + TAG + "]&fr";
    private static final String TAG_ERR   = "&lr[" + TAG + "]&fr";
    private static final String TAG_DEBUG = "&lk[" + TAG + "]&fr";

    private PLog() {}

    private static String tag(String levelColor, String customTag) {
        return levelColor + "[" + customTag + "]&fr";
    }

    public static void info(String text, Object... values) {
        Log.info(TAG_INFO + " " + Strings.format(text, values));
    }

    public static void infoTag(String customTag, String text, Object... values) {
        Log.info(TAG_INFO + " " + tag("&lc", customTag) + " " + Strings.format(text, values));
    }

    public static void warn(String text, Object... values) {
        Log.warn(TAG_WARN + " " + Strings.format(text, values));
    }

    public static void warnTag(String customTag, String text, Object... values) {
        Log.warn(TAG_WARN + " " + tag("&ly", customTag) + " " + Strings.format(text, values));
    }

    public static void err(String text, Object... values) {
        Log.err(TAG_ERR + " " + Strings.format(text, values));
    }

    public static void err(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        Log.err(TAG_ERR + " " + sw);
    }

    public static void err(String text, Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        Log.err(TAG_ERR + " " + text + ": " + sw);
    }

    public static void errTag(String customTag, String text, Object... values) {
        Log.err(TAG_ERR + " " + tag("&lr", customTag) + " " + Strings.format(text, values));
    }

    public static void errTag(String customTag, Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        Log.err(TAG_ERR + " " + tag("&lr", customTag) + " " + sw);
    }

    public static void errTag(String customTag, String text, Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        Log.err(TAG_ERR + " " + tag("&lr", customTag) + " " + text + ": " + sw);
    }

    public static void debug(String text, Object... values) {
        Log.debug(TAG_DEBUG + " " + Strings.format(text, values));
    }

    public static void debugTag(String customTag, String text, Object... values) {
        Log.debug(TAG_DEBUG + " " + tag("&lk", customTag) + " " + Strings.format(text, values));
    }
}
