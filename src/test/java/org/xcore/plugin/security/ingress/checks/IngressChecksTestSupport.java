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

import java.util.Locale;

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

    static Bundle testBundle() {
        return new Bundle(Locale.ENGLISH) {
            @Override
            public Locale resolveLocale(String code) {
                return Locale.ENGLISH;
            }

            @Override
            public Locale resolveLocale(Locale locale) {
                return locale == null ? Locale.ENGLISH : locale;
            }

            @Override
            public String format(Locale locale, String id, java.util.Map<String, Object> args) {
                return id;
            }
        };
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
