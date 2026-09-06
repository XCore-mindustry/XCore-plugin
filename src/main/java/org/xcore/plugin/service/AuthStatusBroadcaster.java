package org.xcore.plugin.service;

import com.google.gson.Gson;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import mindustry.gen.Call;
import mindustry.gen.Player;
import org.xcore.plugin.model.AuthStatusPacket;

import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class AuthStatusBroadcaster {

    private final AtomicLong revisionCounter = new AtomicLong(System.currentTimeMillis());
    private final Gson rawGson;

    @Inject
    public AuthStatusBroadcaster(@Named("raw") Gson rawGson) {
        this.rawGson = rawGson;
    }

    public long nextRevision() {
        return revisionCounter.incrementAndGet();
    }

    public void pushStatus(Player player, boolean isLinked, String discordUsername, boolean hasDiscordAdmin, boolean hasPassword, boolean isAdmin) {
        if (player == null || player.con == null) return;
        long rev = nextRevision();
        var status = new AuthStatusPacket(isLinked, discordUsername, hasDiscordAdmin, hasPassword, isAdmin, rev);
        Call.clientPacketReliable(player.con, "adm_auth_status", rawGson.toJson(status));
    }
}
