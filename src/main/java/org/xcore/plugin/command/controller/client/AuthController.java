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
import org.xcore.plugin.service.PlayerSessionService;
import org.xcore.plugin.service.NetworkService;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.netServer;

@Singleton
public class AuthController implements CloudClientController {

    private final AdminDataRepository adminDataRepository;
    private final PlayerSessionService playerSessionService;
    private final NetworkService network;
    private final Config config;

    @Inject
    public AuthController(AdminDataRepository adminDataRepository,
                          PlayerSessionService playerSessionService,
                          NetworkService network,
                          Config config) {
        this.adminDataRepository = adminDataRepository;
        this.playerSessionService = playerSessionService;
        this.network = network;
        this.config = config;
    }

    @Command("login <password>")
    public void login(XCoreSender sender,
                      @Argument("password") String password) {

        if (password.length() < 4) {
            sender.send("error-admin-password-too-short", args());
            return;
        }

        var data = playerSessionService.get(sender.player().uuid());
        var adminData = adminDataRepository.findByUuid(data.uuid);

        if (adminData.password.isEmpty()) {
            adminData.hashPassword(password);
            adminDataRepository.save(adminData);
            sender.send("commands-login-admin-password-created", args());
        }

        if (adminData.verifyPassword(password)) {
            if (adminData.adminConfirmed) {
                sender.player().admin(true);
                netServer.admins.adminPlayer(sender.player().uuid(), sender.player().getInfo().adminUsid);
                sender.send("commands-login-success", args());
            } else {
                sender.send("commands-login-request-approval-discord", args());
                network.post(new SocketEvents.AdminRequestEvent(data.pid, config.server));
            }
        } else {
            sender.send("error-wrong-admin-password", args());
        }
    }

    @Command("logout")
    public void logout(XCoreSender sender) {
        if (sender.player().admin) {
            sender.player().admin(false);
            netServer.admins.unAdminPlayer(sender.player().uuid());
            sender.send("commands-logout-successful", args());
        }
    }
}
