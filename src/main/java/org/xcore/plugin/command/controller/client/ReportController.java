package org.xcore.plugin.command.controller.client;

import jakarta.inject.Singleton;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.jspecify.annotations.Nullable;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.ui.menu.ReportMenu;

@Singleton
public class ReportController implements CloudClientController {
    private final ReportMenu menu;

    public ReportController(ReportMenu menu) {
        this.menu = menu;
    }

    @Command("report [target]")
    public void report(XCoreSender sender, @Argument("target") @Greedy @Nullable String target) {
        menu.open(resolveSession(sender), target);
    }
}
