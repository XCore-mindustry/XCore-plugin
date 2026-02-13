package org.xcore.plugin.cloud;

import lombok.Getter;
import mindustry.gen.Player;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.plugin.localization.BundleService;

import java.util.Locale;
import java.util.Map;

import static com.ospx.flubundle.Bundle.args;

public class XCoreSender {

    @Getter
    private final MindustrySender handle;
    private final BundleService bundle;

    public XCoreSender(MindustrySender handle, BundleService bundle) {
        this.handle = handle;
        this.bundle = bundle;
    }

    public Player player() {
        return handle.player();
    }

    public boolean isPlayer() {
        return handle.isPlayer();
    }

    public void sendMessage(String message) {
        handle.sendMessage(message);
    }

    public void send(String key, Map<String, Object> args) {
        if (isPlayer()) {
            bundle.send(player(), key, args);
        } else {
            handle.sendMessage(bundle.format(bundle.getDefaultLocale(), key, args));
        }
    }

    public void send(String key) {
        send(key, args());
    }

    public Locale locale() {
        return isPlayer() ? bundle.locale(player()) : bundle.getDefaultLocale();
    }

    public String format(String key, Map<String, Object> args) {
        return bundle.format(locale(), key, args);
    }

    public String format(String key) {
        return format(key, args());
    }
}
