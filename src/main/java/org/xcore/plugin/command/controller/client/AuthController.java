package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.service.AdminAuthService;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.netServer;

@Singleton
public class AuthController implements CloudClientController {

    private final AdminAuthService adminAuthService;
    private final SessionService sessionService;
    private final PlayerDisplayService playerDisplayService;

    @Inject
    public AuthController(AdminAuthService adminAuthService,
                          SessionService sessionService,
                          PlayerDisplayService playerDisplayService) {
        this.adminAuthService = adminAuthService;
        this.sessionService = sessionService;
        this.playerDisplayService = playerDisplayService;
    }

    @Command("login <password>")
    public void login(XCoreSender sender, @Argument("password") String password) {
        if (!sender.isPlayer() || sender.player() == null) return;
        Session session = sessionService.get(sender.player().uuid());
        if (session == null) return;
        Localization local = session.locale();

        AdminAuthService.AuthResult result = adminAuthService.authenticate(sender.player(), password);
        local.send(result.messageKey(), args());
    }

    @Command("logout")
    public void logout(XCoreSender sender) {
        if (!sender.isPlayer() || sender.player() == null) return;
        Session session = sessionService.get(sender.player().uuid());
        if (session == null || session.data == null) return;
        Localization local = session.locale();

        if (sender.player().admin) {
            sender.player().admin(false);
            netServer.admins.unAdminPlayer(sender.player().uuid());
            playerDisplayService.refresh(session);
            local.send("commands-logout-successful", args());
        }
    }
}
