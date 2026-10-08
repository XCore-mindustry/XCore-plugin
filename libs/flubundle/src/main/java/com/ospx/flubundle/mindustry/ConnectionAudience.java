package com.ospx.flubundle.mindustry;

import com.ospx.flubundle.Bundle;
import com.ospx.flubundle.Text;
import mindustry.net.NetConnection;
import mindustry.net.Packets;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * A connection that has sent its {@link Packets.ConnectPacket} but has no {@code Player} yet.
 *
 * <p>Messages are rendered in {@link Bundle#locale(Packets.ConnectPacket)}: the language the
 * player selected earlier when the installed {@link com.ospx.flubundle.LocaleResolver} knows it,
 * otherwise the client locale. Created by {@link Messenger#connecting(NetConnection, Packets.ConnectPacket)}.
 *
 * <pre>{@code
 * messenger.connecting(con, packet).kick("ban-notice", Args.of("reason", Markup.escape(ban.reason)));
 * String reason = messenger.connecting(con, packet).format("whitelist-denied");
 * }</pre>
 */
public final class ConnectionAudience {

    private final Bundle bundle;
    private final NetConnection connection;
    private final Packets.ConnectPacket packet;

    ConnectionAudience(Bundle bundle, NetConnection connection, Packets.ConnectPacket packet) {
        this.bundle = bundle;
        this.connection = connection;
        this.packet = Objects.requireNonNull(packet, "packet");
    }

    /** The locale messages to this connection are rendered in. Resolved on each call. */
    public Locale locale() {
        return bundle.locale(packet);
    }

    public String format(String key) {
        return format(Text.of(key));
    }

    public String format(String key, Map<String, Object> args) {
        return format(Text.of(key, args));
    }

    public String format(Text text) {
        return bundle.format(locale(), text);
    }

    /**
     * Disconnects with a localized reason, like {@link NetConnection#kick(String)} (the address may
     * not reconnect for 30 seconds).
     */
    public void kick(String key) {
        kick(Text.of(key));
    }

    public void kick(String key, Map<String, Object> args) {
        kick(Text.of(key, args));
    }

    public void kick(Text text) {
        requireConnection().kick(format(text));
    }

    /**
     * Disconnects with a localized reason and keeps the address from reconnecting for
     * {@code durationMillis}.
     */
    public void kick(Text text, long durationMillis) {
        requireConnection().kick(format(text), durationMillis);
    }

    private NetConnection requireConnection() {
        return Objects.requireNonNull(connection, "this audience has no connection to kick");
    }
}
