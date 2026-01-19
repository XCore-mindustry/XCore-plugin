package org.xcore.plugin.utils;

import arc.util.CommandHandler;
import mindustry.gen.Player;
import org.xcore.plugin.utils.models.MuteData;

import java.time.Duration;
import java.time.Instant;

import static com.ospx.flubundle.Bundle.args;
import static org.xcore.plugin.PluginVars.bundle;
import static org.xcore.plugin.PluginVars.database;

public class Security {
    public static boolean isMuted(Player player) {
        MuteData mute = database.getMuteDatas().get(player.uuid());

        if (mute == null) return false;

        if (!mute.expired()) {
            Duration remain = Duration.between(Instant.now(), mute.expireDate);

            bundle.send(player, "you-are-muted",
                    args("adminName", mute.adminName,
                            "reason", mute.reason,
                            "remainMinutes", remain.toMinutes(),
                            "remainSeconds", remain.toSecondsPart()
                    )
            );
            return true;
        }

        database.getMuteDatas().delete(player.uuid());
        return false;
    }

    public static CommandHandler.CommandRunner<Player> withMuteCheck(CommandHandler.CommandRunner<Player> command) {
        return (args, player) -> {
            if (isMuted(player)) return;
            command.accept(args, player);
        };
    }

    public static CommandHandler.CommandRunner<Player> withAdminCheck(CommandHandler.CommandRunner<Player> command) {
        return (args, player) -> {
            if (!player.admin) {
                bundle.send(player, "error-access-denied", args());
                return;
            }
            command.accept(args, player);
        };
    }

    public static CommandHandler.CommandRunner<Player> withPlayTimeCheck(int requiredMinutes, String errorBundleKey, CommandHandler.CommandRunner<Player> command) {
        return (args, player) -> {
            if (player.admin) {
                command.accept(args, player);
                return;
            }

            var data = database.getCached(player.uuid());
            if (data != null && data.totalPlayTime < requiredMinutes) {
                bundle.send(player, errorBundleKey, args("time", requiredMinutes));
                return;
            }

            command.accept(args, player);
        };
    }
}