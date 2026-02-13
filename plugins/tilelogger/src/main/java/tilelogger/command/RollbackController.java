package tilelogger.command;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.Vars;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Default;
import org.incendo.cloud.annotations.Flag;
import org.incendo.cloud.annotations.Permission;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.cloud.annotation.AllowNegativeDuration;
import org.xcore.plugin.cloud.annotation.DefaultUnit;
import tilelogger.PlayerDescriptor;
import tilelogger.Rect;
import tilelogger.service.TileLoggerService;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class RollbackController {

    private final TileLoggerService service;

    @Inject
    public RollbackController(TileLoggerService service) {
        this.service = service;
    }

    @Command("rollback <target> [time]")
    @Permission("admin")
    public void rollback(XCoreSender sender,
                         @Argument("target") String targetStr,
                         @Argument("time") @AllowNegativeDuration @DefaultUnit(TimeUnit.MINUTES) @Default("0") Duration duration,
                         @Flag("selection") boolean useSelection) {

        PlayerDescriptor target;

        if (targetStr.equalsIgnoreCase("self") && sender.isPlayer()) {
            target = service.findPlayerUuid(sender.player().uuid());
        } else {
            target = service.findPlayer(targetStr);
        }

        if (target == null) {
            sender.send("error-player-not-found", args());
            return;
        }

        int timeSeconds = (int) duration.toSeconds();
        Rect rect;

        if (useSelection && sender.isPlayer()) {
            rect = service.getPlayerConfig(sender.player()).rect;
        } else {
            rect = new Rect((short)0, (short)0, (short)(Vars.world.width()-1), (short)(Vars.world.height()-1));
        }

        service.rollback(sender.isPlayer() ? sender.player() : null, target, -1, timeSeconds, rect);
    }
}
