package org.xcore.plugin.cloud;

import com.ospx.flubundle.Bundle;
import jakarta.inject.Provider;
import lombok.Getter;
import mindustry.gen.Player;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.permission.Actor;
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
    private final Actor console;

    public XCoreSender(MindustrySender handle, Bundle bundle, Provider<SessionService> sessionService) {
        this(handle, bundle, sessionService, Actor.LOCAL_CONSOLE);
    }

    /**
     * @param console which console this is when the sender is not a player; it is fixed here
     *                because where a command came from is only known while it is being issued
     */
    public XCoreSender(MindustrySender handle, Bundle bundle, Provider<SessionService> sessionService, Actor console) {
        this.handle = handle;
        this.bundle = bundle;
        this.sessionService = sessionService;
        this.console = console;
    }

    public Actor actor() {
        if (isPlayer()) {
            return new Actor.PlayerActor(player().uuid(), session());
        }
        return console;
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
        handle.sendMessage(format(key, args));
    }

    public void send(String key) {
        send(key, args());
    }

    /**
     * The sender's locale: the session's language for a connected player, otherwise the
     * bundle's choice for the player (or the default locale for the console).
     */
    public Locale locale() {
        Session s = session();
        if (s != null) {
            return s.locale().getLocale();
        }
        return isPlayer() ? bundle.locale(player()) : bundle.getDefaultLocale();
    }

    public String format(String key, Map<String, Object> args) {
        Session s = session();
        if (s != null) {
            return s.locale().format(key, args);
        }
        return bundle.format(locale(), key, args);
    }

    public String format(String key) {
        return format(key, args());
    }
}
