package org.xcore.plugin.cloud;

import jakarta.inject.Provider;
import lombok.Getter;
import mindustry.gen.Player;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.plugin.localization.BundleService;
import org.xcore.plugin.session.SessionService;

import java.util.Locale;
import java.util.Map;

import static com.ospx.flubundle.Bundle.args;

public class XCoreSender {

    @Getter
    private final MindustrySender handle;
    private final BundleService bundle;
    private final Provider<SessionService> sessionService;

    public XCoreSender(MindustrySender handle, BundleService bundle, Provider<SessionService> sessionService) {
        this.handle = handle;
        this.bundle = bundle;
        this.sessionService = sessionService;
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
            var session = sessionService.get().get(player());
            if (session == null) {
                handle.sendMessage(bundle.format(bundle.getDefaultLocale(), key, args));
                return;
            }
            session.locale().send(key, args);
        } else {
            handle.sendMessage(bundle.format(bundle.getDefaultLocale(), key, args));
        }
    }

    public void send(String key) {
        send(key, args());
    }

    public Locale locale() {
        if (isPlayer()) {
            var session = sessionService.get().get(player());
            if (session != null) {
                return session.locale().getLocale();
            }
        }
        return bundle.getDefaultLocale();
    }

    public String format(String key, Map<String, Object> args) {
        if (isPlayer()) {
            var session = sessionService.get().get(player());
            if (session != null) {
                return session.locale().format(key, args);
            }
        }
        return bundle.format(bundle.getDefaultLocale(), key, args);
    }

    public String format(String key) {
        return format(key, args());
    }
}
