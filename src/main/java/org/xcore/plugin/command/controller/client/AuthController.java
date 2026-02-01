package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.command.core.annotation.Command;
import org.xcore.plugin.command.core.context.ClientContext;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.database.repository.AdminDataRepository;
import org.xcore.plugin.service.PlayerSessionService;
import org.xcore.plugin.service.NetworkService;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.netServer;

@Singleton
public class AuthController {

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

    @Command(name = "login", params = "<password>")
    public void login(ClientContext ctx) {
        String password = ctx.args()[0];

        if (password.length() < 4) {
            ctx.send("error-admin-password-too-short", args());
            return;
        }

        var data = playerSessionService.get(ctx.player().uuid());
        var adminData = adminDataRepository.findByUuid(data.uuid);

        if (adminData.password.isEmpty()) {
            adminData.hashPassword(password);
            adminDataRepository.save(adminData);
            ctx.send("commands-login-admin-password-created", args());
        }

        if (adminData.verifyPassword(password)) {
            if (adminData.adminConfirmed) {
                ctx.player().admin(true);
                netServer.admins.adminPlayer(ctx.player().uuid(), ctx.player().getInfo().adminUsid);
                ctx.send("commands-login-success", args());
            } else {
                ctx.send("commands-login-request-approval-discord", args());
                network.post(new SocketEvents.AdminRequestEvent(data.pid, config.server));
            }
        } else {
            ctx.send("error-wrong-admin-password", args());
        }
    }

    @Command(name = "logout")
    public void logout(ClientContext ctx) {
        if (ctx.player().admin) {
            ctx.player().admin(false);
            netServer.admins.unAdminPlayer(ctx.player().uuid());
            ctx.send("commands-logout-successful", args());
        }
    }
}
