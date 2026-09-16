package tilelogger.command;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Default;
import org.xcore.plugin.cloud.XCoreSender;
import tilelogger.PlayerConfig;
import tilelogger.PlayerDescriptor;
import tilelogger.service.TileLoggerService;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class HistoryController {

    private final TileLoggerService service;

    @Inject
    public HistoryController(TileLoggerService service) {
        this.service = service;
    }

    @Command("history [size]")
    public void historyToggle(XCoreSender sender,
                              @Argument("size") @Default("0") int size) {
        if (!sender.isPlayer()) return;

        PlayerConfig config = service.getPlayerConfig(sender.player());

        if (size == 0 && config.historySize == 0) {
            config.historySize = 6;
        } else {
            config.historySize = size;
        }

        sender.send("commands-history-success", args("size", config.historySize));
    }


    @Command("history p|player <target> [size]")
    public void historyTarget(XCoreSender sender,
                              @Argument("target") String targetStr,
                              @Argument("size") @Default("10") long size) {
        service.executeHistoryPlayer(sender, targetStr, size);
    }

    @Command("history t|tile <x> <y> [size]")
    public void historyTile(XCoreSender sender,
                            @Argument("x") short x,
                            @Argument("y") short y,
                            @Argument("size") @Default("10") long size) {
        service.showHistory(sender.player(), x, y, size);
    }
}
