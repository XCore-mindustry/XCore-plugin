package org.xcore.plugin.cloud;

import com.ospx.flubundle.Bundle;
import jakarta.inject.Provider;
import lombok.Getter;
import mindustry.gen.Player;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static com.ospx.flubundle.Bundle.args;

public class XCoreSender {

    @Getter
    private final MindustrySender handle;
    private final Bundle bundle;
    private final Provider<SessionService> sessionService;

    public XCoreSender(MindustrySender handle, Bundle bundle, Provider<SessionService> sessionService) {
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

    /**
     * Resolves the active player session, or null if the sender is the server console
     * or the player is not currently connected.
     */
    public Session session() {
        if (sessionService == null || player() == null) {
            return null;
        }
        SessionService service = sessionService.get();
        return service != null ? service.get(player()) : null;
    }

    /**
     * Resolves the active player session wrapped in an Optional.
     */
    public Optional<Session> optionalSession() {
        return Optional.ofNullable(session());
    }

    /**
     * Resolves the player's persistent data if an active session exists.
     */
    public PlayerData playerData() {
        Session s = session();
        return s != null ? s.data : null;
    }

    /**
     * Executes the given action if an active player session with valid data exists.
     *
     * @return true if the action was executed, false otherwise
     */
    public boolean withSession(Consumer<Session> consumer) {
        Session s = session();
        if (s != null && s.data != null) {
            consumer.accept(s);
            return true;
        }
        return false;
    }

    /**
     * Returns the bound Localization instance for the sender's active session,
     * or a fallback localization if the sender is console or session is missing.
     */
    public Localization localization() {
        Session s = session();
        if (s != null) {
            return s.locale();
        }
        return new Localization(bundle, locale());
    }

    public void sendMessage(String message) {
        handle.sendMessage(message);
    }

    public void send(String key, Map<String, Object> args) {
        if (isPlayer()) {
            var session = sessionService.get().get(player());
            if (session == null) {
                handle.sendMessage(bundle.format(locale(), key, args));
                return;
            }
            session.locale().send(key, args);
        } else {
            handle.sendMessage(bundle.format(locale(), key, args));
        }
    }

    public void send(String key) {
        send(key, args());
    }

    public Locale locale() {
        if (isPlayer()) {
            var session = sessionService.get().get(player());
            if (session != null) {
                return session.locale().localizer().locale();
            }
            return bundle.locale(player());
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
        return bundle.format(locale(), key, args);
    }

    public String format(String key) {
        return format(key, args());
    }
}
