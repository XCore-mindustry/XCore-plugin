package org.xcore.plugin.localization;

import com.ospx.flubundle.Bundle;
import com.ospx.flubundle.LocaleCodes;
import com.ospx.flubundle.LocaleResolver;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import mindustry.net.Packets;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.concurrent.GameThread;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Makes the shared {@link Bundle} honour the language a player selected in settings, so every
 * {@code bundle.locale(player)} / {@code messenger.to(player).send(...)} call, including the ones made by
 * other plugins, uses it instead of the client locale.
 *
 * <p>Connection checks run before a session exists. For them the language is looked up by the
 * packet's uuid: a live session first, then the language last seen for that uuid, then the
 * database. The database is never queried on the game thread, where the fast ingress checks run;
 * those use what is known in memory and otherwise the client locale.
 */
@Singleton
public class SessionLocaleResolver implements LocaleResolver {

    /** A remembered language older than this is re-read from the database when that is allowed. */
    static final long FRESH_MILLIS = 10 * 60 * 1000L;
    static final int MAX_REMEMBERED = 20_000;

    private record Known(String language, long at) {
    }

    private final Bundle bundle;
    private final SessionService sessionService;
    private final PlayerDataRepository playerDataRepository;
    private final LongSupplier clock;
    private final Map<String, Known> known = new ConcurrentHashMap<>();

    @Inject
    public SessionLocaleResolver(Bundle bundle, SessionService sessionService, PlayerDataRepository playerDataRepository) {
        this(bundle, sessionService, playerDataRepository, System::currentTimeMillis);
    }

    SessionLocaleResolver(Bundle bundle, SessionService sessionService, PlayerDataRepository playerDataRepository,
                          LongSupplier clock) {
        this.bundle = bundle;
        this.sessionService = sessionService;
        this.playerDataRepository = playerDataRepository;
        this.clock = clock;
    }

    @PostConstruct
    public void install() {
        bundle.setLocaleResolver(this);
    }

    @Override
    public Locale resolve(Player player) {
        Session session = sessionService.get(player);
        remember(session);
        return selectedLocale(session);
    }

    @Override
    public Locale resolve(Packets.ConnectPacket packet) {
        if (packet == null || packet.uuid == null || packet.uuid.isBlank()) {
            return null;
        }

        Session session = sessionService.get(packet.uuid);
        if (session != null && session.data != null) {
            remember(session);
            return selectedLocale(session);
        }

        Known entry = known.get(packet.uuid);
        boolean fresh = entry != null && clock.getAsLong() - entry.at() < FRESH_MILLIS;
        if (!fresh && playerDataRepository != null && !GameThread.isGameThread()) {
            try {
                String stored = playerDataRepository.findLanguage(packet.uuid);
                entry = store(packet.uuid, stored == null ? "auto" : stored);
            } catch (RuntimeException e) {
                PLog.warnTag("Localization", "Could not load the language of @: @", packet.uuid, e.getMessage());
            }
        }
        return entry == null ? null : toLocale(entry.language());
    }

    private void remember(Session session) {
        if (session == null || session.data == null || session.data.uuid == null) {
            return;
        }
        String language = normalize(session.data.language);
        Known entry = known.get(session.data.uuid);
        if (entry == null || !entry.language().equals(language) || clock.getAsLong() - entry.at() > FRESH_MILLIS / 2) {
            store(session.data.uuid, language);
        }
    }

    private Known store(String uuid, String language) {
        if (known.size() >= MAX_REMEMBERED) {
            known.clear();
        }
        Known entry = new Known(normalize(language), clock.getAsLong());
        known.put(uuid, entry);
        return entry;
    }

    private static String normalize(String language) {
        return language == null || language.isBlank() ? "auto" : language;
    }

    /**
     * The language the player selected in settings, or {@code null} when there is no session or the
     * selection is {@code auto} (use the client locale).
     */
    public static Locale selectedLocale(Session session) {
        if (session == null || session.data == null) {
            return null;
        }
        return toLocale(session.data.language);
    }

    private static Locale toLocale(String language) {
        if (language == null || "auto".equals(language)) {
            return null;
        }
        return LocaleCodes.parse(language);
    }
}
