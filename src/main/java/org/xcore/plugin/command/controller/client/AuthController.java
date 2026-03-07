package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Argument;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.database.repository.AdminDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.PlayerDisplayService;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.netServer;

@Singleton
public class AuthController implements CloudClientController {

    private final AdminDataRepository adminDataRepository;
    private final SessionService sessionService;
    private final NetworkService network;
    private final Config config;
    private final PlayerDisplayService playerDisplayService;

    @Inject
    public AuthController(AdminDataRepository adminDataRepository,
                          SessionService sessionService,
                          NetworkService network,
                          Config config,
                          PlayerDisplayService playerDisplayService) {
        this.adminDataRepository = adminDataRepository;
        this.sessionService = sessionService;
        this.network = network;
        this.config = config;
        this.playerDisplayService = playerDisplayService;
    }

    @Command("login <password>")
    public void login(XCoreSender sender, @Argument("password") String password) {
        Session session = sessionService.get(sender.player().uuid());
        if (session == null || session.data == null) return;
        PlayerData data = session.data;
        Localization local = session.locale();


        if (password.length() < 4) {
            local.send("error-admin-password-too-short", args());
            return;
        }

        if (data.password.isEmpty()) {
            data.hashPassword(password);
            data.admin = true;
            adminDataRepository.save(data);
            local.send("commands-login-admin-password-created", args());
        }

        if (data.verifyPassword(password)) {
            if (data.adminConfirmed) {
                sender.player().admin(true);
                netServer.admins.adminPlayer(sender.player().uuid(), sender.player().getInfo().adminUsid);
                playerDisplayService.refresh(session);
                local.send("commands-login-success", args());
            } else {
                local.send("commands-login-request-approval-discord", args());
                network.post(new SocketEvents.AdminRequestEvent(data.pid, config.server));
            }
        } else {
            local.send("error-wrong-admin-password", args());
        }
    }

    @Command("logout")
    public void logout(XCoreSender sender) {
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
