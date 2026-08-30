package tilelogger.command;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Default;
import org.incendo.cloud.annotations.Permission;
import org.xcore.plugin.cloud.XCoreSender;
import tilelogger.service.TileLoggerService;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class TileLoggerServerController {

    private final TileLoggerService service;

    @Inject
    public TileLoggerServerController(TileLoggerService service) {
        this.service = service;
    }

    @Command("tl file <path> [mode]")
    @Permission("admin")
    public void file(XCoreSender sender,
                     @Argument("path") String path,
                     @Argument("mode") @Default("") String mode) {

        boolean write = mode.equalsIgnoreCase("w");
        service.resetHistory(path, write);
        sender.sendMessage("[accent]History reset: " + path + " (write=" + write + ")");
    }
}
