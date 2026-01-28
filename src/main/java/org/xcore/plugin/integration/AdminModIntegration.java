package org.xcore.plugin.integration;

import arc.util.Log;
import arc.util.Strings;
import com.google.gson.Gson;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import mindustry.gen.Call;
import org.xcore.plugin.service.BundleService;
import org.xcore.plugin.service.TimeService;
import org.xcore.plugin.database.DatabaseService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.common.VersionComparator;
import org.xcore.plugin.model.BanData;
import org.xcore.plugin.model.BanRequestData;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.netServer;

@Singleton
public class AdminModIntegration {

    private final DatabaseService database;
    private final NetworkService network;
    private final Gson rawGson;
    private final BundleService bundle;
    private final TimeService time;

    @Inject
    public AdminModIntegration(DatabaseService database, NetworkService network, @Named("raw") Gson rawGson,
                               BundleService bundle, TimeService timeService) {
        this.database = database;
        this.network = network;
        this.rawGson = rawGson;
        this.bundle = bundle;
        this.time = timeService;
    }

    @PostConstruct
    public void init() {
        netServer.addPacketHandler("take_ban_data", (player, content) -> {
            if (!player.admin) return;

            BanRequestData req;
            try {
                req = rawGson.fromJson(content, BanRequestData.class);
            } catch (Exception e) {
                Log.err("Error processing ban request from @: @", player.name, e.getMessage());
                player.sendMessage("[scarlet]An error occurred while processing the request.");
                return;
            }

            var targetData = database.getPlayerDataRepository().findById(req.pid);

            if (targetData == null) {
                bundle.send(player, "error-player-not-found", args());
                Call.clientPacketReliable(player.con, "give_ban_data", content);
                return;
            }

            Instant date = time.parsePeriod(req.duration, TimeUnit.DAYS);

            if (date == null) {
                bundle.send(player, "error.wrong-period-format", args());
                Call.clientPacketReliable(player.con, "give_ban_data", content);
                return;
            }

            netServer.admins.unbanPlayerID(targetData.uuid);

            var ban = BanData.builder()
                    .name(req.name)
                    .uuid(targetData.uuid)
                    .ip(targetData.ip)
                    .adminName(player.name)
                    .reason(req.reason)
                    .expireDate(Instant.now().plusMillis(date.toEpochMilli()))
                    .build();
            network.post(ban);
            database.getBanDataRepository().save(ban);
        });

        netServer.addPacketHandler("cancel_ban_data", (player, content) -> {
            if (!player.admin) return;
            BanRequestData req = rawGson.fromJson(content, BanRequestData.class);

            var targetData = database.getPlayerDataRepository().findById(req.pid);
            netServer.admins.unbanPlayerID(targetData.uuid);

            bundle.send(player, "ban-cancelled", args("nickname", targetData.nickname));
        });

        netServer.addPacketHandler("adm_mod_end", (player, content) -> {
            var data = database.getCached(player.uuid());

            if (data == null || data.adminModVersion != null) return;
            Log.info("Player @ joined with the Admin mod version '@'", player.plainName(), content);

            if (VersionComparator.compareVersions(content, "1.3") < 0) {
                player.con.kick(Strings.format("""
                        [green]The required AdminTools version: [grey]1.3[]
                        [scarlet]Your AdminTools version: [grey]@[]

                        [cyan]Please update your AdminTools to join this server.
                       \s""", content), 0);
                return;
            }
            data.adminModVersion = content;
        });
    }
}