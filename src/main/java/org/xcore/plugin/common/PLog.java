package org.xcore.plugin.common;

import arc.util.Log;
import arc.util.Strings;

public final class PLog {
    private static final String TAG = "XCore";

    private PLog() {}

    public static void info(String text, Object... values) {
        Log.infoTag(TAG, Strings.format(text, values));
    }

    public static void err(String text, Object... values) {
        Log.errTag(TAG, Strings.format(text, values));
    }
}
