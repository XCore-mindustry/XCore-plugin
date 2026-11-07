package org.xcore.plugin.command.controller.server;

import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudServerController;
import org.xcore.plugin.concurrent.StorageExecutor;
import org.xcore.plugin.service.stats.PlayerTotalsStore;

@Singleton
public class StatsRebuildController implements CloudServerController {
    private final PlayerTotalsStore totals;
    private final StorageExecutor storage;

    @Inject
    public StatsRebuildController(PlayerTotalsStore totals, StorageExecutor storage) {
        this.totals = totals;
        this.storage = storage;
    }

    @Command("stats-rebuild <uuid>")
    @CommandDescription("Queues a rebuild of one player's statistics, or of everyone's with 'all'.")
    public void rebuild(XCoreSender sender, @Argument("uuid") String uuid) {
        if (!totals.enabled()) {
            Log.err("[Stats] Set statistics.enabled = true (MongoDB replica set required) first.");
            return;
        }
        storage.supply(() -> {
            if ("all".equals(uuid)) totals.rebuildAll();
            else totals.invalidate(null, uuid);
            Log.info("[Stats] Rebuild queued: @", uuid);
            return null;
        }).exceptionally(error -> {
            Log.err("[Stats] Cannot queue rebuild", error);
            return null;
        });
    }

    @Command("stats-audit")
    @CommandDescription("Counts match outcomes under the current statistics rules. Read-only.")
    public void audit(XCoreSender sender) {
        storage.supply(() -> {
            Log.info("[Stats] Audit: @", totals.audit().toJson());
            return null;
        }).exceptionally(error -> {
            Log.err("[Stats] Audit failed", error);
            return null;
        });
    }
}
