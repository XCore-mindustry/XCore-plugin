package tilelogger.command;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Default;
import org.incendo.cloud.annotations.Permission;
import org.xcore.plugin.cloud.XCoreSender;
import tilelogger.service.TileLoggerService;

@Singleton
public class TileLoggerServerController {
    private final TileLoggerService service;

    @Inject
    public TileLoggerServerController(TileLoggerService service) {
        this.service = service;
    }

    @Command("tl file <path> [mode]")
    @Permission("admin")
    public void file(
            XCoreSender sender, @Argument("path") String path, @Argument("mode") @Default("") String mode) {
        if (mode.equalsIgnoreCase("w")) {
            service.resetHistory(path, true);
            sender.send("tilelogger-session-started");
        } else if (mode.isBlank() || mode.equalsIgnoreCase("r")) {
            service.openArchive(sender, path);
        } else
            throw new org.xcore.plugin.cloud.exception.XCoreCommandException("tilelogger-error-mode");
    }
}
