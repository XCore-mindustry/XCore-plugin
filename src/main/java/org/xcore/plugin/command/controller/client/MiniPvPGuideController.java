package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.incendo.cloud.annotations.Command;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.gamemode.pvp.guide.MiniPvPGuideService;

/** {@code /pvp info}: the MiniPvP guide. Registered on the MiniPvP server only. */
@Singleton
public class MiniPvPGuideController implements CloudClientController {

    private final MiniPvPGuideService guide;

    @Inject
    public MiniPvPGuideController(MiniPvPGuideService guide) {
        this.guide = guide;
    }

    @Command("pvp")
    @Command("pvp info")
    @Command("pvp guide")
    public void guide(XCoreSender sender) {
        sender.withSession(guide::open);
    }
}
