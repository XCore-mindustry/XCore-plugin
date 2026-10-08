package tilelogger.command;

import com.ospx.flubundle.Args;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.world.Block;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import org.xcore.plugin.cloud.XCoreSender;
import tilelogger.PlayerConfig;
import tilelogger.TileLogger;
import tilelogger.service.TileLoggerService;


@Singleton
public class TileLoggerController {

    private final TileLoggerService service;

    @Inject
    public TileLoggerController(TileLoggerService service) {
        this.service = service;
    }

    @Command("tl")
    public void info(XCoreSender sender) {
        sender.send("tilelogger-info", Args.of("build", TileLogger.getBuildString()));
    }

    @Command("tl memory|m")
    public void memory(XCoreSender sender) {
        sender.sendMessage(service.getMemoryUsage(sender.isPlayer() ? sender.player() : null));
    }

    @Command("tl select|s")
    public void select(XCoreSender sender) {
        if (!sender.isPlayer()) {
            sender.send("error-not-allowed-from-console");
            return;
        }
        service.getPlayerConfig(sender.player()).selectState = 1;
        sender.send("tilelogger-select-start");
    }

    @Command("tl fill|f <block>")
    @Permission("admin")
    public void fill(XCoreSender sender, @Argument("block") String blockName) {
        if (!sender.isPlayer()) {
            sender.send("error-not-allowed-from-console");
            return;
        }

        Block block = Vars.content.block(blockName);
        if (block == null) {
            sender.send("error-block-not-found");
            return;
        }

        PlayerConfig config = service.getPlayerConfig(sender.player());
        service.fill(sender.player(), null, block, config.rect);
    }
}
