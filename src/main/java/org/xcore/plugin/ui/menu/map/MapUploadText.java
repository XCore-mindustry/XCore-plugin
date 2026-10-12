package org.xcore.plugin.ui.menu.map;

import arc.util.Strings;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.MapDiscordUpload;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import static com.ospx.flubundle.Bundle.args;

/** The same submission attribution in the reactive dialog and legacy /map menu. */
public final class MapUploadText {
    private MapUploadText() {}

    public static String format(Localization locale, MapDiscordUpload upload) {
        if (upload == null) return "";
        var lines = new ArrayList<String>();
        if (upload.creatorDiscordId != null) {
            lines.add(locale.t("map-upload-creator", args("person", person(upload.creatorName, upload.creatorDiscordId))));
        }
        lines.add(locale.t("map-upload-submitter", args("person", person(upload.uploadedByName, upload.uploadedByDiscordId))));
        String date = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(upload.uploadedAt));
        lines.add(locale.t("map-upload-date", args("date", date)));
        return String.join("\n", lines);
    }

    private static String person(String name, String id) {
        String clean = Strings.stripColors(name == null ? "" : name)
                .replaceAll("[\\p{Cntrl}\\[\\]]", " ").trim();
        if (clean.length() > 80) clean = clean.substring(0, 80);
        return "[lightgray]" + (clean.isBlank() ? id : clean) + "[] [gray](" + id + ")[]";
    }
}
