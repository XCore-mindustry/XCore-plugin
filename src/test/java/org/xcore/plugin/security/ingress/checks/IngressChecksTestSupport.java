package org.xcore.plugin.security.ingress.checks;

import arc.struct.Seq;
import com.ospx.flubundle.Bundle;
import mindustry.Vars;
import mindustry.core.NetServer;
import mindustry.entities.EntityGroup;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.mod.Mods;
import mindustry.net.Net;
import mindustry.net.NetConnection;
import mindustry.net.Packets;
import org.xcore.plugin.localization.BundleService;

import java.util.Locale;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

final class IngressChecksTestSupport {

    private IngressChecksTestSupport() {
    }

    static VarsState captureVarsState() {
        return new VarsState(Vars.netServer, Vars.net, Vars.mods, Groups.player);
    }

    static EntityGroup<Player> newPlayerGroup() {
        return new EntityGroup<>(Player.class, false, false);
    }

    static Player createPlayer(String name, String uuid, String usid, boolean admin) {
        var player = Player.create();
        var con = new DummyConnection("127.0.0.1");
        con.uuid = uuid;
        con.usid = usid;

        player.con = con;
        player.name = name;
        player.admin = admin;
        return player;
    }

    static Packets.ConnectPacket newPacket() {
        var packet = new Packets.ConnectPacket();
        packet.uuid = "uuid-1";
        packet.usid = "usid-1";
        packet.name = "Player";
        packet.locale = "en";
        packet.versionType = "release";
        packet.version = 100;
        packet.mods = Seq.with();
        return packet;
    }

    static BundleService mockBundleService() {
        var bundle = mock(BundleService.class);
        when(bundle.locale(any(String.class))).thenReturn(Locale.ENGLISH);
        when(bundle.locale(any(Locale.class))).thenAnswer(invocation -> invocation.getArgument(0, Locale.class));
        when(bundle.getDefaultLocale()).thenReturn(Locale.ENGLISH);
        when(bundle.format(any(Locale.class), any(String.class), anyMap()))
                .thenAnswer(invocation -> invocation.getArgument(1, String.class));
        lenient().when(bundle.localizer(any(Locale.class))).thenAnswer(invocation -> {
            Locale locale = invocation.getArgument(0, Locale.class);
            Bundle delegate = new Bundle(locale == null ? Locale.ENGLISH : locale);
            return delegate.localizer(locale == null ? Locale.ENGLISH : locale);
        });
        return bundle;
    }

    static class DummyConnection extends NetConnection {
        int closeCalls;
        int sendCalls;
        Object lastSent;
        boolean lastReliable;

        DummyConnection(String address) {
            super(address);
        }

        @Override
        public void send(Object object, boolean reliable) {
            sendCalls++;
            lastSent = object;
            lastReliable = reliable;
        }

        @Override
        public void close() {
            closeCalls++;
        }
    }

    static final class VarsState {
        private final NetServer netServer;
        private final Net net;
        private final Mods mods;
        private final EntityGroup<Player> playerGroup;

        private VarsState(NetServer netServer, Net net, Mods mods, EntityGroup<Player> playerGroup) {
            this.netServer = netServer;
            this.net = net;
            this.mods = mods;
            this.playerGroup = playerGroup;
        }

        void restore() {
            Vars.netServer = netServer;
            Vars.net = net;
            Vars.mods = mods;
            Groups.player = playerGroup;
        }
    }
}
