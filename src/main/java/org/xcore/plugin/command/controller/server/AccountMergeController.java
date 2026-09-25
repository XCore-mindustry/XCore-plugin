package org.xcore.plugin.command.controller.server;

import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.incendo.cloud.annotations.Default;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.xcore.cloud.mindustry.selector.annotation.DenySelectors;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudServerController;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.model.AuditActor;
import org.xcore.plugin.model.AuditActorType;
import org.xcore.plugin.service.AccountMergeService;

@Singleton
public class AccountMergeController implements CloudServerController {

    private final AccountMergeService accountMergeService;

    @Inject
    public AccountMergeController(AccountMergeService accountMergeService) {
        this.accountMergeService = accountMergeService;
    }

    @DenySelectors
    @Command("merge-player <source> <target> [reason]")
    @CommandDescription("Merges the source player account into the target player account (playtime, ratings, points, badges, matches).")
    public void mergePlayer(
            XCoreSender sender,
            @Argument(value = "source", description = "Source player (Name/#PID/UUID) to merge FROM") String source,
            @Argument(value = "target", description = "Target player (Name/#PID/UUID) to merge INTO") String target,
            @Argument(value = "reason", description = "Audit reason") @Default("Console manual merge") @Greedy String reason
    ) {
        executeMerge(source, target, reason);
    }

    @DenySelectors
    @Command("merge-account <source> <target> [reason]")
    @CommandDescription("Alias for merge-player. Merges the source player account into the target player account.")
    public void mergeAccount(
            XCoreSender sender,
            @Argument(value = "source", description = "Source player (Name/#PID/UUID) to merge FROM") String source,
            @Argument(value = "target", description = "Target player (Name/#PID/UUID) to merge INTO") String target,
            @Argument(value = "reason", description = "Audit reason") @Default("Console manual merge") @Greedy String reason
    ) {
        executeMerge(source, target, reason);
    }

    private void executeMerge(String source, String target, String reason) {
        PLog.info("&y[AccountMerge] Starting account merge: '@' -> '@'...", source, target);

        AuditActor actor = AuditActor.builder()
                .type(AuditActorType.SERVER_CONSOLE)
                .nameSnapshot("Console")
                .id("console")
                .build();

        var request = new AccountMergeService.MergeRequest(source, target, reason, actor);

        accountMergeService.mergeAsync(request).thenAccept(result -> {
            if (!result.success()) {
                PLog.err("&r[AccountMerge] Merge failed: @", result.message());
                return;
            }

            var s = result.sourceBefore();
            var tBefore = result.targetBefore();
            var tAfter = result.targetAfter();

            PLog.info("&g[AccountMerge] Successfully merged accounts!");
            PLog.info("  Source (Closed): #@ '@' (UUID: @)", s.pid, s.nickname, s.uuid);
            PLog.info("  Target (Active): #@ '@' (UUID: @)", tAfter.pid, tAfter.nickname, tAfter.uuid);
            PLog.info("  Playtime:        @m + @m -> @m", s.totalPlayTime, tBefore.totalPlayTime, tAfter.totalPlayTime);
            PLog.info("  PvP Rating:      @ vs @ -> @", s.pvpRating, tBefore.pvpRating, tAfter.pvpRating);
            PLog.info("  Hexed Points:    @ + @ -> @ (Rank: @)", s.hexedPoints, tBefore.hexedPoints, tAfter.hexedPoints, tAfter.hexedRank().name());
            PLog.info("  Badges:          total unlocked: @", tAfter.unlockedBadges != null ? tAfter.unlockedBadges.size() : 0);
            PLog.info("  Games Updated:   @ matches", result.gamesTransferred());
            if (result.banTransferred()) {
                PLog.warn("  Active Ban:      Transferred from source to target!");
            }
            if (result.muteTransferred()) {
                PLog.warn("  Active Mute:     Transferred from source to target!");
            }
        }).exceptionally(ex -> {
            PLog.err("&r[AccountMerge] Unexpected error during merge: @", ex.getMessage());
            Log.err(ex);
            return null;
        });
    }
}
