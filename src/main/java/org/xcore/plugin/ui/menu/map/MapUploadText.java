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
        return format(locale, upload, false);
    }

    public static String format(Localization locale, MapDiscordUpload upload, boolean showIds) {
        if (upload == null) return "";
        var lines = new ArrayList<String>();
        if (upload.creatorDiscordId != null) {
            lines.add(locale.t("map-upload-creator", args("person", person(upload.creatorName, upload.creatorDiscordId, upload.creatorHandle, showIds))));
        }
        lines.add(locale.t("map-upload-submitter", args("person", person(upload.uploadedByName, upload.uploadedByDiscordId, upload.uploadedByHandle, showIds))));
        String date = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(upload.uploadedAt));
        lines.add(locale.t("map-upload-date", args("date", date)));
        return String.join("\n", lines);
    }

    public static String creator(Localization locale, MapDiscordUpload upload) {
        return upload == null || upload.creatorDiscordId == null ? "" : locale.t("map-upload-creator",
                args("person", person(upload.creatorName, upload.creatorDiscordId, upload.creatorHandle, false)));
    }

    private static String person(String name, String id, String handle, boolean showIds) {
        String clean = Strings.stripColors(name == null ? "" : name)
                .replaceAll("[\\p{Cntrl}\\[\\]]", " ").trim();
        if (clean.length() > 80) clean = clean.substring(0, 80);
        String safeId = id != null && id.matches("[0-9]{1,20}") ? id : "?";
        String safeHandle = handle == null ? "" : handle.replaceAll("[^a-zA-Z0-9_.]", "");
        if (safeHandle.length() > 32) safeHandle = safeHandle.substring(0, 32);
        String label = clean.isBlank() ? (safeHandle.isBlank() ? safeId : "@" + safeHandle) : clean;
        return "[lightgray]" + label + "[]"
                + (safeHandle.isBlank() || label.equals("@" + safeHandle) ? "" : " [gray](@" + safeHandle + ")[]")
                + (showIds ? " [gray](" + safeId + ")[]" : "");
    }
}
