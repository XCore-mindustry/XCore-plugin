package org.xcore.plugin.modules.votes;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import mindustry.maps.Map;
import org.xcore.plugin.modules.Config;
import org.xcore.plugin.modules.bundles.BundleService;
import org.xcore.plugin.modules.database.DatabaseService;
import org.xcore.plugin.modules.network.NetworkService;

@Singleton
public class VoteFactory {
    @Inject DatabaseService database;
    @Inject NetworkService network;
    @Inject BundleService bundle;
    @Inject VoteService voteService;
    @Inject Config config;

    public VoteKick createKick(Player starter, Player target, String reason) {
        return new VoteKick(starter, target, reason, database, network, bundle, voteService, config);
    }

    public VoteRtv createRtv(Map target, boolean isManual) {
        return new VoteRtv(target, isManual, database, bundle, voteService);
    }
}
